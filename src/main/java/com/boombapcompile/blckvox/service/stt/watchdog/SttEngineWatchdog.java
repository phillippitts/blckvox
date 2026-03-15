package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
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

    @Autowired
    public SttEngineWatchdog(List<SttEngine> engines,
                             SttWatchdogProperties props,
                             ApplicationEventPublisher publisher) {
        Objects.requireNonNull(props, "props");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.budgetTracker = new RestartBudgetTracker(props);
        this.confidenceMonitor = new ConfidenceMonitor(props);

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
            try {
                engine.initialize();
                LOG.info("Engine {} initialized successfully", name);
            } catch (Exception ex) {
                LOG.error("Failed to initialize engine {} at startup: {}", name, ex.toString());
                state.put(name, EngineState.DISABLED);
            }
        }
    }

    /** Visible for tests. */
    EngineState getState(String engine) {
        return state.get(engine);
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

        state.put(engine, EngineState.DEGRADED);
        attemptRestart(engine);
    }

    @EventListener
    public void onRecovered(EngineRecoveredEvent event) {
        String engine = event.engine();
        if (!enginesByName.containsKey(engine)) {
            return;
        }
        state.put(engine, EngineState.HEALTHY);
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

        double confidence = event.result().confidence();
        ConfidenceMonitor.Evaluation eval = confidenceMonitor.record(engine, confidence);
        if (eval != null && eval.degraded()) {
            LOG.warn("Engine {} confidence degraded: avg={} (window tracked by monitor)",
                    engine, String.format("%.3f", eval.average()));
            state.put(engine, EngineState.DEGRADED);
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
            if (!budgetTracker.allowsRestart(engine)) {
                disableEngine(engine);
                return;
            }
            budgetTracker.recordRestart(engine);
            if (tryRestart(engine)) {
                publisher.publishEvent(new EngineRecoveredEvent(engine, Instant.now()));
                LOG.info("Engine {} restarted successfully", engine);
            } else {
                state.put(engine, EngineState.DEGRADED);
                LOG.warn("Engine {} restart failed; remaining in DEGRADED state", engine);
            }
        } finally {
            budgetTracker.unlockRestart(engine);
        }
    }

    private void disableEngine(String engine) {
        state.put(engine, EngineState.DISABLED);
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
        state.put(bestEngine, EngineState.DEGRADED);
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
