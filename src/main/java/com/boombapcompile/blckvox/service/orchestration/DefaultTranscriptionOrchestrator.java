package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.audio.AudioDurationCalculator;
import com.boombapcompile.blckvox.service.audio.AudioSilenceDetector;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Objects;

/**
 * Default implementation of {@link TranscriptionOrchestrator} supporting single-engine
 * and dual-engine reconciliation modes.
 *
 * <p>This implementation coordinates transcription execution with engine selection, confidence
 * evaluation, result publishing, and metrics recording. All operations are thread-safe.
 *
 * <p><b>Single-Engine Mode:</b> Selects one engine based on primary preference and health status.
 * Automatically upgrades to reconciliation if Vosk confidence is below threshold.
 *
 * <p><b>Reconciliation Mode:</b> Runs both engines in parallel and reconciles results using
 * configurable strategy (SIMPLE, CONFIDENCE, or OVERLAP).
 *
 * <p><b>Error Handling:</b> Transcription failures result in empty text being published to
 * notify downstream consumers while preventing incorrect text emission.
 *
 * @since 1.1
 */
public class DefaultTranscriptionOrchestrator implements TranscriptionOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DefaultTranscriptionOrchestrator.class);

    // Engine name constants
    private static final String ENGINE_RECONCILED = "reconciled";

    private final ApplicationEventPublisher publisher;
    private final int silenceThreshold;

    // Reconciliation service (encapsulates parallel, reconciler, recProps)
    private final ReconciliationService reconciliation;

    // State machines and services
    private final EngineSelectionStrategy engineSelector;
    private final TranscriptionMetricsPublisher metricsPublisher;

    /**
     * Constructs a DefaultTranscriptionOrchestrator.
     *
     * @param props orchestration configuration (silence gap threshold)
     * @param publisher Spring event publisher for transcription results
     * @param reconciliation service for dual-engine reconciliation (encapsulates parallel, reconciler, recProps)
     * @param engineSelector strategy for selecting STT engine (also manages vosk, whisper, watchdog)
     * @param metricsPublisher metrics publishing service
     * @throws NullPointerException if any required parameter is null
     */
    public DefaultTranscriptionOrchestrator(OrchestrationProperties props,
                                            ApplicationEventPublisher publisher,
                                            ReconciliationService reconciliation,
                                            EngineSelectionStrategy engineSelector,
                                            TranscriptionMetricsPublisher metricsPublisher) {
        Objects.requireNonNull(props, "props must not be null");
        this.silenceThreshold = props.getSilenceThreshold();
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation must not be null");
        this.engineSelector = Objects.requireNonNull(engineSelector, "engineSelector must not be null");
        this.metricsPublisher = Objects.requireNonNull(metricsPublisher, "metricsPublisher must not be null");
    }

    @Override
    public void transcribe(byte[] pcm) {
        Objects.requireNonNull(pcm, "pcm must not be null");

        // Skip transcription if audio is effectively silent to avoid STT hallucinations
        double maxWindowRms = AudioSilenceDetector.calculateMaxWindowRMS(pcm);
        if (AudioSilenceDetector.isSilentMaxWindow(pcm, silenceThreshold)) {
            double overallRms = AudioSilenceDetector.calculateOverallRMS(pcm);
            LOG.info("Audio is silent (maxWindowRms={}, overallRms={}, threshold={}); skipping transcription",
                    String.format("%.1f", maxWindowRms), String.format("%.1f", overallRms), silenceThreshold);
            publishResult(TranscriptionResult.of("", 1.0, "silent"), "silent");
            return;
        }

        // If reconciliation is enabled and services are present, run both engines and reconcile
        if (isReconciliationEnabled()) {
            transcribeWithReconciliation(pcm);
        } else {
            transcribeWithSingleEngine(pcm);
        }
    }

    /**
     * Checks if reconciliation mode is enabled with all required dependencies.
     *
     * @return true if reconciliation is enabled and all services are available
     */
    private boolean isReconciliationEnabled() {
        return reconciliation.isEnabled();
    }

    /**
     * Transcribes audio using both engines in parallel and reconciles the results.
     *
     * <p><b>Failure semantics:</b> If reconciliation fails (either with a
     * {@link TranscriptionException} from an engine or any other runtime exception), this method
     * will record a failure metric for the "reconciled" engine and publish a
     * {@link TranscriptionCompletedEvent} with an empty text result. It will not fall back to
     * any previously computed single-engine result. This conservative behavior avoids emitting
     * potentially inaccurate text when both engines disagree or fail.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithReconciliation(byte[] pcm) {
        double audioDurationMs = AudioDurationCalculator.durationMs(pcm.length);
        long startTime = System.nanoTime();
        try {
            TranscriptionResult result = reconciliation.reconcile(pcm);
            String strategy = reconciliation.getStrategy();

            // Record metrics
            long duration = System.nanoTime() - startTime;
            metricsPublisher.recordSuccess(ENGINE_RECONCILED, duration, strategy);

            long processingMs = TimeUtils.nanosToMillis(duration);
            double ratio = processingMs / audioDurationMs;
            metricsPublisher.recordProcessingRatio(ENGINE_RECONCILED, ratio);

            logTranscriptionWithAudio(ENGINE_RECONCILED, processingMs, audioDurationMs, ratio,
                    result.text().length(), strategy);
            publishResult(result, ENGINE_RECONCILED);
        } catch (TranscriptionException te) {
            metricsPublisher.recordFailure(ENGINE_RECONCILED, "transcription_error");
            LOG.warn("Reconciled transcription failed: {}", te.getMessage());
            TranscriptionResult failedResult = TranscriptionResult.failure(ENGINE_RECONCILED, te.getMessage());
            publishResult(failedResult, ENGINE_RECONCILED);
        } catch (RuntimeException re) {
            metricsPublisher.recordFailure(ENGINE_RECONCILED, "unexpected_error");
            LOG.error("Unexpected error during reconciled transcription", re);
            TranscriptionResult failedResult = TranscriptionResult.failure(ENGINE_RECONCILED, re.getMessage());
            publishResult(failedResult, ENGINE_RECONCILED);
        }
    }

    /**
     * Transcribes audio using a single engine selected based on watchdog state.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithSingleEngine(byte[] pcm) {
        SttEngine engine = null;
        double audioDurationMs = AudioDurationCalculator.durationMs(pcm.length);
        long startTime = System.nanoTime();

        try {
            engine = selectSingleEngine();
            TranscriptionResult result = engine.transcribe(pcm);
            String engineName = engine.getEngineName();

            // Record metrics for successful single-engine transcription
            long duration = System.nanoTime() - startTime;
            metricsPublisher.recordSuccess(engineName, duration, null);

            long processingMs = TimeUtils.nanosToMillis(duration);
            double ratio = processingMs / audioDurationMs;
            metricsPublisher.recordProcessingRatio(engineName, ratio);

            logTranscriptionWithAudio(engineName, processingMs, audioDurationMs, ratio,
                    result.text().length(), null);
            publishResult(result, engineName);
        } catch (TranscriptionException te) {
            String engineName = engine != null ? engine.getEngineName() : "unknown";
            metricsPublisher.recordFailure(engineName, "transcription_error");
            LOG.warn("Transcription failed: {}", te.getMessage());
            TranscriptionResult failedResult = TranscriptionResult.failure(engineName, te.getMessage());
            publishResult(failedResult, engineName);
        } catch (RuntimeException re) {
            String engineName = engine != null ? engine.getEngineName() : "unknown";
            metricsPublisher.recordFailure(engineName, "unexpected_error");
            LOG.error("Unexpected error during transcription", re);
            TranscriptionResult failedResult = TranscriptionResult.failure(engineName, re.getMessage());
            publishResult(failedResult, engineName);
        }
    }

    /**
     * Selects the best available engine based on primary preference and health status.
     *
     * @return selected STT engine
     * @throws TranscriptionException if both engines are unavailable
     */
    private SttEngine selectSingleEngine() {
        return engineSelector.selectEngine();
    }

    /**
     * Logs transcription result with audio duration and processing ratio for profiling.
     */
    private void logTranscriptionWithAudio(String engineName, long processingMs, double audioDurationMs,
                                            double ratio, int textLength, String strategy) {
        String ratioStr = String.format("%.2f", ratio);
        String audioStr = String.format("%.0f", audioDurationMs);
        try {
            ThreadContext.put("audioDurationMs", String.format("%.1f", audioDurationMs));
            ThreadContext.put("processingRatio", ratioStr);

            if (strategy != null) {
                LOG.info("Transcription completed by {} in {} ms (audio={}ms, ratio={}x, strategy={}, chars={})",
                        engineName, processingMs, audioStr, ratioStr, strategy, textLength);
            } else {
                LOG.info("Transcription completed by {} in {} ms (audio={}ms, ratio={}x, chars={})",
                        engineName, processingMs, audioStr, ratioStr, textLength);
            }
        } finally {
            ThreadContext.remove("audioDurationMs");
            ThreadContext.remove("processingRatio");
        }
    }

    /**
     * Publishes transcription result as an event for downstream processing.
     *
     * <p>Pause-based newlines are now inserted directly by the STT engines
     * (Vosk via audio silence detection, Whisper via segment timestamps).
     *
     * @param result the transcription result
     * @param engineName name of the engine that produced the result
     */
    private void publishResult(TranscriptionResult result, String engineName) {
        LOG.debug("[transcript] engine={}, confidence={}, text='{}'",
                engineName, String.format("%.2f", result.confidence()), result.text());
        publisher.publishEvent(new TranscriptionCompletedEvent(result, Instant.now(), engineName));
    }
}
