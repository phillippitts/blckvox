package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties.PrimaryEngine;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Strategy for selecting healthy STT engines based on configuration and watchdog health.
 *
 * <p>This class encapsulates the engine selection logic, separating health checking
 * and preference-based selection from the orchestration workflow.
 *
 * <p><b>Selection Algorithm:</b>
 * <ol>
 *   <li>Check if primary engine (configured preference) is healthy</li>
 *   <li>If primary is healthy, return it</li>
 *   <li>If primary is unhealthy, return secondary (fallback) engine</li>
 *   <li>If both are unhealthy, return primary anyway (fail gracefully)</li>
 * </ol>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The watchdog health checks
 * are atomic, and engine references are immutable.
 *
 * @since 1.0
 */
public final class EngineSelectionStrategy {

    private static final Logger LOG = LogManager.getLogger(EngineSelectionStrategy.class);


    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties props;

    /**
     * Constructs an engine selection strategy.
     *
     * @param vosk Vosk STT engine
     * @param whisper Whisper STT engine
     * @param watchdog engine health monitor
     * @param props orchestration configuration (primary engine preference)
     * @throws NullPointerException if any parameter is null
     */
    public EngineSelectionStrategy(SttEngine vosk,
                                    SttEngine whisper,
                                    SttEngineWatchdog watchdog,
                                    OrchestrationProperties props) {
        this.vosk = Objects.requireNonNull(vosk, SttEngineNames.VOSK);
        this.whisper = Objects.requireNonNull(whisper, SttEngineNames.WHISPER);
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog");
        this.props = Objects.requireNonNull(props, "props");
    }

    /**
     * Selects a healthy engine based on primary preference and watchdog health.
     *
     * <p>This method prefers the configured primary engine but falls back to
     * the secondary if the primary is unhealthy. If both engines are unavailable,
     * throws {@link TranscriptionException}.
     *
     * @return selected STT engine (never null)
     * @throws TranscriptionException if both engines are unavailable
     */
    public SttEngine selectEngine() {
        SttEngine primary = getPrimaryEngine();
        SttEngine secondary = getSecondaryEngine();
        String primaryName = getPrimaryEngineName();
        String secondaryName = getSecondaryEngineName();

        boolean primaryHealthy = watchdog.isEngineEnabled(primaryName) && primary.isHealthy();

        if (primaryHealthy) {
            LOG.debug("Selected primary engine: {}", primaryName);
            return primary;
        }

        // Primary unhealthy — attempt lazy init of secondary if needed
        boolean secondaryHealthy = watchdog.isEngineEnabled(secondaryName) && secondary.isHealthy();
        if (!secondaryHealthy && watchdog.isEngineEnabled(secondaryName)) {
            secondaryHealthy = watchdog.initializeOnDemand(secondaryName);
        }

        if (secondaryHealthy) {
            LOG.warn("Primary engine {} unhealthy, falling back to {}", primaryName, secondaryName);
            return secondary;
        }

        throw new TranscriptionException(String.format(
                "Both engines unavailable (vosk.enabled=%s, vosk.healthy=%s, "
                        + "whisper.enabled=%s, whisper.healthy=%s)",
                watchdog.isEngineEnabled(SttEngineNames.VOSK), vosk.isHealthy(),
                watchdog.isEngineEnabled(SttEngineNames.WHISPER), whisper.isHealthy()));
    }

    /**
     * Returns the primary engine based on configuration.
     *
     * @return primary STT engine
     */
    private SttEngine getPrimaryEngine() {
        return props.getPrimaryEngine() == PrimaryEngine.VOSK ? vosk : whisper;
    }

    /**
     * Returns the secondary (fallback) engine.
     *
     * @return secondary STT engine
     */
    private SttEngine getSecondaryEngine() {
        return props.getPrimaryEngine() == PrimaryEngine.VOSK ? whisper : vosk;
    }

    /**
     * Returns the name of the primary engine.
     *
     * @return SttEngineNames.VOSK or SttEngineNames.WHISPER
     */
    private String getPrimaryEngineName() {
        return props.getPrimaryEngine() == PrimaryEngine.VOSK ? SttEngineNames.VOSK : SttEngineNames.WHISPER;
    }

    /**
     * Returns the name of the secondary engine.
     *
     * @return SttEngineNames.VOSK or SttEngineNames.WHISPER
     */
    private String getSecondaryEngineName() {
        return props.getPrimaryEngine() == PrimaryEngine.VOSK ? SttEngineNames.WHISPER : SttEngineNames.VOSK;
    }

}
