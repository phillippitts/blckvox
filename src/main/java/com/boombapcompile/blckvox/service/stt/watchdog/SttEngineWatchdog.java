package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven watchdog that observes engine failures and performs bounded auto-restarts with cooldown.
 *
 * <p>Delegates restart budget tracking to {@link RestartBudgetTracker} and confidence
 * monitoring to {@link ConfidenceMonitor}.
 */
@Component
@ConditionalOnProperty(prefix = "stt.watchdog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SttEngineWatchdog {

    private static final Logger LOG = LogManager.getLogger(SttEngineWatchdog.class);

    public enum EngineState { HEALTHY, DEGRADED, DISABLED }

    private final ApplicationEventPublisher publisher;
    private final RestartBudgetTracker budgetTracker;
    private final ConfidenceMonitor confidenceMonitor;

    private final Map<String, SttEngine> enginesByName = new ConcurrentHashMap<>();
    private final Map<String, EngineState> state = new ConcurrentHashMap<>();
    private final String primaryEngineName;

    /**
     * Convenience constructor without OrchestrationProperties (eager init for all engines).
     * Used by tests and when orchestration properties are not available.
     */
    public SttEngineWatchdog(List<SttEngine> engines,
                             SttWatchdogProperties props,
                             ApplicationEventPublisher publisher) {
        this(engines, props, publisher, null);
    }

    @Autowired
    public SttEngineWatchdog(List<SttEngine> engines,
                             SttWatchdogProperties props,
                             ApplicationEventPublisher publisher,
                             @Autowired(required = false)
                             OrchestrationProperties orchestrationProperties) {
        Objects.requireNonNull(props, "props");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.budgetTracker = new RestartBudgetTracker(props);
        this.confidenceMonitor = new ConfidenceMonitor(props);
        this.primaryEngineName = orchestrationProperties != null
                ? orchestrationProperties.getPrimaryEngine().name().toLowerCase()
                : null;

        for (SttEngine e : engines) {
            String name = e.getEngineName();
            enginesByName.put(name, e);
            state.put(name, EngineState.HEALTHY);
            budgetTracker.register(name);
            confidenceMonitor.register(name);
        }
        LOG.info("Watchdog initialized for engines={}", enginesByName.keySet());
    }

    // Package-private for tests
    SttEngineWatchdog(List<SttEngine> engines,
                      ApplicationEventPublisher publisher,
                      RestartBudgetTracker budgetTracker,
                      ConfidenceMonitor confidenceMonitor) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.budgetTracker = Objects.requireNonNull(budgetTracker);
        this.confidenceMonitor = Objects.requireNonNull(confidenceMonitor);
        this.primaryEngineName = null; // null = eager init for all (test compat)

        for (SttEngine e : engines) {
            String name = e.getEngineName();
            enginesByName.put(name, e);
            state.put(name, EngineState.HEALTHY);
        }
    }

    @PostConstruct
    public void initializeEngines() {
        LOG.info("Initializing STT engines at startup...");
        for (Map.Entry<String, SttEngine> entry : enginesByName.entrySet()) {
            String name = entry.getKey();
            SttEngine engine = entry.getValue();

            // Lazy init: skip non-primary engines at startup (initialized on first use)
            if (primaryEngineName != null && !name.equals(primaryEngineName)) {
                LOG.info("Engine {} deferred for lazy initialization (primary={})", name, primaryEngineName);
                continue;
            }

            try {
                engine.initialize();
                LOG.info("Engine {} initialized successfully", name);
            } catch (Exception ex) {
                LOG.error("Failed to initialize engine {} at startup: {}", name, ex.toString());
                updateState(name, EngineState.DISABLED);
            }
        }
    }

    /**
     * Initializes an engine on-demand (lazy initialization for secondary engines).
     * Thread-safe: delegates to {@link SttEngine#initialize()} which is idempotent.
     *
     * @param engineName engine to initialize
     * @return true if engine is healthy after initialization attempt
     */
    public boolean initializeOnDemand(String engineName) {
        SttEngine engine = enginesByName.get(engineName);
        if (engine == null) {
            return false;
        }
        if (engine.isHealthy()) {
            return true; // Already initialized
        }
        try {
            LOG.info("Lazy-initializing engine {} on first use", engineName);
            engine.initialize();
            LOG.info("Engine {} lazy-initialized successfully", engineName);
            return engine.isHealthy();
        } catch (Exception ex) {
            LOG.error("Failed to lazy-initialize engine {}: {}", engineName, ex.toString());
            updateState(engineName, EngineState.DISABLED);
            return false;
        }
    }

    /**
     * Returns a snapshot of all engine states (thread-safe copy).
     */
    public Map<String, EngineState> getEngineStates() {
        return Map.copyOf(state);
    }

    /** Visible for tests. */
    EngineState getState(String engine) {
        return state.get(engine);
    }

    /**
     * Updates engine state and publishes an {@link EngineHealthChangedEvent}
     * if the state actually changed.
     */
    private void updateState(String engine, EngineState newState) {
        EngineState previous = state.put(engine, newState);
        if (previous != null && previous != newState) {
            publisher.publishEvent(new EngineHealthChangedEvent(
                    engine, previous, newState, Instant.now()));
        }
    }

    /** Visible for tests. */
    ConfidenceMonitor getConfidenceMonitor() {
        return confidenceMonitor;
    }

    /**
     * Checks if an engine is currently enabled (not disabled or in cooldown).
     */
    public boolean isEngineEnabled(String engine) {
        EngineState s = state.get(engine);
        if (s == EngineState.DISABLED) {
            return false;
        }
        return !budgetTracker.isInCooldown(engine);
    }

    @Async("sttExecutor")
    @EventListener
    public void onFailure(EngineFailureEvent event) {
        String engine = event.engine();
        if (!enginesByName.containsKey(engine)) {
            LOG.warn("EngineFailureEvent for unknown engine: {}", engine);
            return;
        }

        LOG.warn("Engine failure: engine={}, msg={} ", engine, event.message());
        if (!isEngineEnabled(engine)) {
            LOG.warn("Engine {} currently disabled until {}", engine, budgetTracker.getCooldownUntil(engine));
            return;
        }

        updateState(engine, EngineState.DEGRADED);
        attemptRestart(engine);
    }

    @EventListener
    public void onRecovered(EngineRecoveredEvent event) {
        String engine = event.engine();
        if (!enginesByName.containsKey(engine)) {
            return;
        }
        updateState(engine, EngineState.HEALTHY);
        budgetTracker.clearOnRecovery(engine);
        confidenceMonitor.clearOnRecovery(engine);
        LOG.info("Engine recovered: {}", engine);
    }

    @EventListener
    public void onTranscriptionCompleted(TranscriptionCompletedEvent event) {
        String engine = event.engineUsed();
        if (!confidenceMonitor.isTracked(engine)) {
            return;
        }

        // Skip failed and silent results — failures poison the average with 0.0,
        // and silent results inflate it with 1.0, both masking real engine health.
        if (event.result().isFailure()) {
            LOG.debug("Skipping failed result for confidence tracking: engine={}", engine);
            return;
        }
        if (event.result().text().isEmpty() && event.result().confidence() >= 1.0) {
            LOG.debug("Skipping silent result for confidence tracking: engine={}", engine);
            return;
        }

        double confidence = event.result().confidence();
        ConfidenceMonitor.Evaluation eval = confidenceMonitor.record(engine, confidence);
        if (eval != null && eval.degraded()) {
            LOG.warn("Engine {} confidence degraded: avg={} (window tracked by monitor)",
                    engine, String.format("%.3f", eval.average()));
            updateState(engine, EngineState.DEGRADED);
            publisher.publishEvent(new EngineFailureEvent(
                    engine, Instant.now(),
                    "low-confidence: avg=" + String.format("%.3f", eval.average()),
                    null, Map.of("reason", "low-confidence",
                                 "avgConfidence", String.format("%.3f", eval.average()))
            ));
        }
    }

    @Scheduled(fixedRateString = "#{${stt.watchdog.health-summary-interval-millis:60000}}")
    void logHealthSummary() {
        StringBuilder sb = new StringBuilder("Watchdog states: ");
        state.forEach((name, st) -> {
            sb.append(name).append('=').append(st);
            sb.append(confidenceMonitor.formattedSummary(name));
            sb.append(' ');
        });
        LOG.info(sb.toString().trim());
    }

    private void attemptRestart(String engine) {
        if (!budgetTracker.tryLockRestart(engine)) {
            LOG.debug("Restart already in progress for {}", engine);
            return;
        }
        try {
            if (budgetTracker.isInCooldown(engine)) {
                LOG.warn("Engine {} is in cooldown until {}", engine, budgetTracker.getCooldownUntil(engine));
                return;
            }
            if (budgetTracker.isBackoffActive(engine)) {
                LOG.debug("Engine {} in backoff until {}", engine, budgetTracker.getBackoffUntil(engine));
                return;
            }
            if (!budgetTracker.allowsRestart(engine)) {
                disableEngine(engine);
                return;
            }
            budgetTracker.recordRestart(engine);
            if (tryRestart(engine)) {
                publisher.publishEvent(new EngineRecoveredEvent(engine, Instant.now()));
                LOG.info("Engine {} restarted successfully", engine);
            } else {
                updateState(engine, EngineState.DEGRADED);
                LOG.warn("Engine {} restart failed; remaining in DEGRADED state", engine);
            }
        } finally {
            budgetTracker.unlockRestart(engine);
        }
    }

    private void disableEngine(String engine) {
        updateState(engine, EngineState.DISABLED);
        Instant until = budgetTracker.disable(engine);
        LOG.error("Engine {} disabled after {} restarts within budget; cooldown until {}",
                engine, budgetTracker.getRestartCount(engine), until);
        checkAllEnginesDisabled();
    }

    /**
     * Safety mode: if all engines are disabled, force-enable the engine with the highest
     * recent average confidence to prevent the system from becoming completely unusable.
     */
    private void checkAllEnginesDisabled() {
        if (enginesByName.size() < 2) {
            return;
        }
        boolean allDisabled = enginesByName.keySet().stream()
                .noneMatch(this::isEngineEnabled);
        if (!allDisabled) {
            return;
        }

        LOG.error("SAFETY MODE: All STT engines disabled — force-enabling best available engine");

        // Stream is guaranteed non-empty: guarded by size >= 2 check above
        String bestEngine = enginesByName.keySet().stream()
                .max(Comparator.comparingDouble(confidenceMonitor::averageConfidence))
                .orElseThrow();

        double avgConf = confidenceMonitor.averageConfidence(bestEngine);
        updateState(bestEngine, EngineState.DEGRADED);
        budgetTracker.clearOnRecovery(bestEngine);
        LOG.error("SAFETY MODE: Force-enabled engine {} (avg confidence: {})",
                bestEngine, String.format("%.3f", avgConf));
        if (tryRestart(bestEngine)) {
            publisher.publishEvent(new EngineRecoveredEvent(bestEngine, Instant.now()));
        }
    }

    private boolean tryRestart(String engine) {
        SttEngine e = enginesByName.get(engine);
        try {
            LOG.warn("Restarting engine {}", engine);
            e.close();
        } catch (Exception ex) {
            LOG.debug("Error during engine.close(): {}", ex.toString());
        }
        try {
            e.initialize();
            return true;
        } catch (Exception ex) {
            LOG.error("Engine {} failed to initialize after restart: {}", engine, ex.toString());
            return false;
        }
    }
}
