package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks restart attempts per engine in a sliding time window and enforces a budget.
 *
 * <p>Thread-safe via per-engine {@link ReentrantLock}s.
 */
public class RestartBudgetTracker {

    private static final Logger LOG = LogManager.getLogger(RestartBudgetTracker.class);

    private final int windowMinutes;
    private final int maxRestartsPerWindow;
    private final int cooldownMinutes;
    private final long backoffBaseDelayMs;
    private final double backoffMultiplier;
    private final long backoffMaxDelayMs;

    private final ConcurrentMap<String, Deque<Instant>> restartWindow = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> disabledUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> nextAllowedRestart = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RestartBudgetTracker(SttWatchdogProperties props) {
        Objects.requireNonNull(props, "props");
        this.windowMinutes = props.windowMinutes();
        this.maxRestartsPerWindow = props.maxRestartsPerWindow();
        this.cooldownMinutes = props.cooldownMinutes();
        this.backoffBaseDelayMs = props.backoffBaseDelayMs();
        this.backoffMultiplier = props.backoffMultiplier();
        this.backoffMaxDelayMs = props.backoffMaxDelayMs();
    }

    /** Registers an engine for tracking. Must be called before any other method for this engine. */
    public void register(String engine) {
        restartWindow.put(engine, new ArrayDeque<>());
        locks.put(engine, new ReentrantLock());
        nextAllowedRestart.put(engine, Instant.EPOCH);
    }

    /**
     * Returns true if the budget allows another restart attempt for this engine.
     * Prunes expired entries from the sliding window.
     */
    public boolean allowsRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            pruneOld(window);
            return window.size() < maxRestartsPerWindow;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically checks the restart budget and records a restart if allowed.
     *
     * <p>This method combines {@link #allowsRestart(String)} and {@link #recordRestart(String)}
     * into a single atomic operation, eliminating the TOCTOU race that would exist if callers
     * checked the budget and recorded separately without holding the per-engine lock.
     *
     * @param engine the engine name
     * @return true if the restart was allowed and recorded; false if the budget is exhausted
     */
    public boolean tryRecordRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            pruneOld(window);
            if (window.size() >= maxRestartsPerWindow) {
                return false;
            }
            window.addLast(Instant.now());
            int attempts = window.size();
            long delayMs = Math.min(
                    (long) (backoffBaseDelayMs * Math.pow(backoffMultiplier, attempts - 1)),
                    backoffMaxDelayMs);
            nextAllowedRestart.put(engine, Instant.now().plus(Duration.ofMillis(delayMs)));
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Records a restart attempt for the given engine and sets the backoff expiry. */
    public void recordRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            window.addLast(Instant.now());
            int attempts = window.size();
            long delayMs = Math.min(
                    (long) (backoffBaseDelayMs * Math.pow(backoffMultiplier, attempts - 1)),
                    backoffMaxDelayMs);
            nextAllowedRestart.put(engine, Instant.now().plus(Duration.ofMillis(delayMs)));
        } finally {
            lock.unlock();
        }
    }

    /** Marks the engine as disabled with a cooldown period. Returns the cooldown expiry. */
    public Instant disable(String engine) {
        Instant until = Instant.now().plus(Duration.ofMinutes(cooldownMinutes));
        disabledUntil.put(engine, until);
        return until;
    }

    /** Clears cooldown, backoff, and restart window for the engine. */
    public void clearOnRecovery(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            restartWindow.get(engine).clear();
        } finally {
            lock.unlock();
        }
        disabledUntil.remove(engine);
        nextAllowedRestart.put(engine, Instant.EPOCH);
    }

    /** Returns true if the engine is currently in its cooldown period. */
    public boolean isInCooldown(String engine) {
        Instant until = disabledUntil.get(engine);
        return until != null && Instant.now().isBefore(until);
    }

    /** Returns the cooldown expiry timestamp, or null if not in cooldown. */
    public Instant getCooldownUntil(String engine) {
        return disabledUntil.get(engine);
    }

    /** Returns true if the engine is currently in its exponential backoff period. */
    public boolean isBackoffActive(String engine) {
        Instant until = nextAllowedRestart.get(engine);
        return until != null && Instant.now().isBefore(until);
    }

    /** Returns the backoff expiry timestamp for the engine (for logging). */
    public Instant getBackoffUntil(String engine) {
        return nextAllowedRestart.get(engine);
    }

    /**
     * Attempts to acquire the per-engine restart lock (non-blocking).
     * Returns true if the lock was acquired. Caller MUST call {@link #unlockRestart} when done.
     */
    public boolean tryLockRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        if (lock == null) {
            LOG.warn("No restart lock for: {}", engine);
            return false;
        }
        return lock.tryLock();
    }

    /** Releases the per-engine restart lock. */
    public void unlockRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        if (lock == null) {
            LOG.warn("No restart lock for: {}", engine);
            return;
        }
        lock.unlock();
    }

    /** Returns the current restart count within the window (for logging). */
    public int getRestartCount(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            pruneOld(window);
            return window.size();
        } finally {
            lock.unlock();
        }
    }

    private void pruneOld(Deque<Instant> window) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
