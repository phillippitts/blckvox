package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A concurrency guard with dynamically adjustable permits.
 *
 * <p>Extends the semantics of {@link ConcurrencyGuard} but allows the number of
 * available permits to be adjusted at runtime via {@link #adjustPermits(int)}.
 *
 * <p>To increase permits: releases additional permits into the semaphore.
 * To decrease permits: best-effort non-blocking drain of excess permits.
 *
 * <p>The current permit count is always clamped to {@code [1, configuredMax]}.
 */
public final class DynamicConcurrencyGuard implements TranscriptionGuard {

    private static final Logger LOG = LogManager.getLogger(DynamicConcurrencyGuard.class);

    private final Semaphore semaphore;
    private final long timeoutMs;
    private final String engineName;
    private final ApplicationEventPublisher publisher;
    private final int configuredMax;
    private final AtomicInteger currentPermits;
    private final ReentrantLock adjustLock = new ReentrantLock();

    public DynamicConcurrencyGuard(int initialPermits,
                                    long timeoutMs,
                                    String engineName,
                                    ApplicationEventPublisher publisher) {
        this.configuredMax = Math.max(1, initialPermits);
        this.semaphore = new Semaphore(this.configuredMax);
        this.timeoutMs = timeoutMs;
        this.engineName = engineName;
        this.publisher = publisher;
        this.currentPermits = new AtomicInteger(this.configuredMax);
    }

    public void acquire() {
        try {
            boolean acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                Map<String, String> context = Map.of(
                        "reason", "concurrency-limit",
                        "timeoutMs", String.valueOf(timeoutMs));
                EngineEventPublisher.publishFailure(
                        publisher, engineName,
                        "concurrency limit reached after " + timeoutMs + "ms wait",
                        null, context);
                throw new TranscriptionException(
                        engineName + " concurrency limit reached after " + timeoutMs + "ms wait",
                        engineName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException(
                    engineName + " transcription interrupted while waiting for semaphore",
                    engineName, e);
        }
    }

    public void release() {
        semaphore.release();
    }

    /**
     * Adjusts the effective permit count toward {@code newTarget}.
     *
     * <p>Clamped to {@code [1, configuredMax]}. Increase releases extra permits;
     * decrease drains permits best-effort (non-blocking).
     *
     * @param newTarget desired number of permits
     */
    public void adjustPermits(int newTarget) {
        adjustLock.lock();
        try {
            int target = Math.max(1, Math.min(newTarget, configuredMax));
            int current = currentPermits.get();
            if (target == current) {
                return;
            }

            if (target > current) {
                int delta = target - current;
                semaphore.release(delta);
                currentPermits.set(target);
                LOG.debug("{} permits increased: {} -> {}", engineName, current, target);
            } else {
                int delta = current - target;
                int drained = 0;
                for (int i = 0; i < delta; i++) {
                    if (semaphore.tryAcquire()) {
                        drained++;
                    }
                }
                currentPermits.addAndGet(-drained);
                if (drained > 0) {
                    LOG.debug("{} permits decreased: {} -> {} (drained {})",
                            engineName, current, currentPermits.get(), drained);
                }
            }
        } finally {
            adjustLock.unlock();
        }
    }

    public int getCurrentPermits() {
        return currentPermits.get();
    }

    public int getConfiguredMax() {
        return configuredMax;
    }
}
