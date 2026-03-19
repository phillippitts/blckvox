package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Strategy for selecting healthy STT engines based on configuration and watchdog health.
 *
 * <p>This class encapsulates the engine selection logic, separating health checking
 * and preference-based selection from the orchestration workflow.
 *
 * <p><b>Selection Algorithm:</b>
 * <ol>
 *   <li>Iterate engines in priority order (configured primary first)</li>
 *   <li>Return the first engine that is enabled and healthy</li>
 *   <li>For non-primary engines, attempt lazy initialization if enabled but not yet healthy</li>
 *   <li>If no engine is available, throw {@link TranscriptionException}</li>
 * </ol>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The watchdog health checks
 * are atomic, and engine references are immutable.
 *
 * @since 1.0
 */
public final class EngineSelectionStrategy {

    private static final Logger LOG = LogManager.getLogger(EngineSelectionStrategy.class);

    private final List<SttEngine> engines;
    private final SttEngineWatchdog watchdog;

    /**
     * Constructs an engine selection strategy.
     *
     * @param engines list of STT engines (must not be empty)
     * @param watchdog engine health monitor (may be null if watchdog is disabled)
     * @param props orchestration configuration (primary engine preference)
     * @throws NullPointerException if engines or props is null
     * @throws IllegalArgumentException if engines list is empty
     */
    public EngineSelectionStrategy(List<SttEngine> engines,
                                    SttEngineWatchdog watchdog,
                                    OrchestrationProperties props) {
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(props, "props");
        if (engines.isEmpty()) {
            throw new IllegalArgumentException("engines list must not be empty");
        }

        String primaryName = props.getPrimaryEngine().name().toLowerCase();
        // Sort so configured primary engine is first
        List<SttEngine> sorted = new ArrayList<>(engines);
        sorted.sort((a, b) -> {
            boolean aIsPrimary = a.getEngineName().equals(primaryName);
            boolean bIsPrimary = b.getEngineName().equals(primaryName);
            return Boolean.compare(bIsPrimary, aIsPrimary);
        });
        this.engines = List.copyOf(sorted);
        this.watchdog = watchdog;
    }

    /**
     * Selects a healthy engine based on priority order and watchdog health.
     *
     * @return selected STT engine (never null)
     * @throws TranscriptionException if all engines are unavailable
     */
    public SttEngine selectEngine() {
        SttEngine primary = engines.getFirst();
        String primaryName = primary.getEngineName();

        if (isEnabled(primaryName) && primary.isHealthy()) {
            LOG.debug("Selected primary engine: {}", primaryName);
            return primary;
        }

        // Primary unhealthy — try fallback engines
        for (int i = 1; i < engines.size(); i++) {
            SttEngine fallback = engines.get(i);
            String name = fallback.getEngineName();

            boolean healthy = isEnabled(name) && fallback.isHealthy();
            if (!healthy && isEnabled(name) && watchdog != null) {
                healthy = watchdog.initializeOnDemand(name);
            }

            if (healthy) {
                LOG.warn("Primary engine {} unhealthy, falling back to {}", primaryName, name);
                return fallback;
            }
        }

        StringJoiner sj = new StringJoiner(", ");
        for (SttEngine e : engines) {
            String name = e.getEngineName();
            sj.add(String.format("%s.enabled=%s, %s.healthy=%s",
                    name, isEnabled(name), name, e.isHealthy()));
        }
        throw new TranscriptionException("All engines unavailable (" + sj + ")");
    }

    private boolean isEnabled(String engineName) {
        return watchdog == null || watchdog.isEngineEnabled(engineName);
    }
}
