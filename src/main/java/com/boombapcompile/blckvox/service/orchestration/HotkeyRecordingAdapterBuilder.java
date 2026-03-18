package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/**
 * Builder for {@link HotkeyRecordingAdapter} to simplify construction with many dependencies.
 *
 * <p>This builder provides a fluent API for constructing adapters in both single-engine
 * and reconciliation modes. It enforces that all required dependencies are provided before
 * allowing construction.
 *
 * @since 1.0
 */
public final class HotkeyRecordingAdapterBuilder {

    // Required dependencies
    private AudioCaptureService captureService;
    private SttEngine voskEngine;
    private SttEngine whisperEngine;
    private SttEngineWatchdog watchdog;
    private OrchestrationProperties orchestrationProperties;
    private HotkeyProperties hotkeyProperties;
    private ApplicationEventPublisher publisher;
    private CaptureStateMachine captureStateMachine;
    private EngineSelectionStrategy engineSelector;

    // Optional dependencies (for reconciliation mode)
    private ParallelSttService parallelSttService;
    private TranscriptReconciler transcriptReconciler;
    private ReconciliationProperties reconciliationProperties;
    private TranscriptionMetricsPublisher metricsPublisher;

    private HotkeyRecordingAdapterBuilder() {
        // Private constructor - use builder() factory method
    }

    /**
     * Creates a new builder instance.
     *
     * @return new builder for HotkeyRecordingAdapter
     */
    public static HotkeyRecordingAdapterBuilder builder() {
        return new HotkeyRecordingAdapterBuilder();
    }

    public HotkeyRecordingAdapterBuilder captureService(AudioCaptureService captureService) {
        this.captureService = captureService;
        return this;
    }

    public HotkeyRecordingAdapterBuilder voskEngine(SttEngine voskEngine) {
        this.voskEngine = voskEngine;
        return this;
    }

    public HotkeyRecordingAdapterBuilder whisperEngine(SttEngine whisperEngine) {
        this.whisperEngine = whisperEngine;
        return this;
    }

    public HotkeyRecordingAdapterBuilder watchdog(SttEngineWatchdog watchdog) {
        this.watchdog = watchdog;
        return this;
    }

    public HotkeyRecordingAdapterBuilder orchestrationProperties(
            OrchestrationProperties orchestrationProperties) {
        this.orchestrationProperties = orchestrationProperties;
        return this;
    }

    public HotkeyRecordingAdapterBuilder hotkeyProperties(HotkeyProperties hotkeyProperties) {
        this.hotkeyProperties = hotkeyProperties;
        return this;
    }

    public HotkeyRecordingAdapterBuilder publisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        return this;
    }

    public HotkeyRecordingAdapterBuilder captureStateMachine(CaptureStateMachine captureStateMachine) {
        this.captureStateMachine = captureStateMachine;
        return this;
    }

    public HotkeyRecordingAdapterBuilder engineSelector(EngineSelectionStrategy engineSelector) {
        this.engineSelector = engineSelector;
        return this;
    }

    public HotkeyRecordingAdapterBuilder parallelSttService(ParallelSttService parallelSttService) {
        this.parallelSttService = parallelSttService;
        return this;
    }

    public HotkeyRecordingAdapterBuilder transcriptReconciler(TranscriptReconciler transcriptReconciler) {
        this.transcriptReconciler = transcriptReconciler;
        return this;
    }

    public HotkeyRecordingAdapterBuilder reconciliationProperties(
            ReconciliationProperties reconciliationProperties) {
        this.reconciliationProperties = reconciliationProperties;
        return this;
    }

    public HotkeyRecordingAdapterBuilder metricsPublisher(TranscriptionMetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
        return this;
    }

    /**
     * Builds the HotkeyRecordingAdapter instance.
     *
     * <p>If all reconciliation dependencies are provided, creates an adapter in
     * reconciliation mode. Otherwise, creates a single-engine adapter.
     *
     * @return configured HotkeyRecordingAdapter
     * @throws NullPointerException if any required dependency is null
     */
    public HotkeyRecordingAdapter build() {
        // Validate required dependencies
        Objects.requireNonNull(captureService, "captureService is required");
        Objects.requireNonNull(voskEngine, "voskEngine is required");
        Objects.requireNonNull(whisperEngine, "whisperEngine is required");
        Objects.requireNonNull(watchdog, "watchdog is required");
        Objects.requireNonNull(orchestrationProperties, "orchestrationProperties is required");
        Objects.requireNonNull(hotkeyProperties, "hotkeyProperties is required");
        Objects.requireNonNull(publisher, "publisher is required");
        Objects.requireNonNull(captureStateMachine, "captureStateMachine is required");
        Objects.requireNonNull(engineSelector, "engineSelector is required");

        // Provide default no-op metricsPublisher if not set (for tests)
        TranscriptionMetricsPublisher effectiveMetricsPublisher = metricsPublisher != null
                ? metricsPublisher
                : TranscriptionMetricsPublisher.NOOP;

        // Create CaptureOrchestrator from capture service and state machine
        CaptureOrchestrator captureOrchestrator = new DefaultCaptureOrchestrator(
                captureService, captureStateMachine);

        // Create ReconciliationService if all reconciliation components are present
        ReconciliationService reconciliation;
        if (parallelSttService != null && transcriptReconciler != null && reconciliationProperties != null) {
            reconciliation = new DefaultReconciliationService(
                    parallelSttService,
                    transcriptReconciler,
                    reconciliationProperties
            );
        } else {
            reconciliation = DefaultReconciliationService.disabled();
        }

        // Create TranscriptionOrchestrator from all transcription-related dependencies
        TranscriptionOrchestrator transcriptionOrchestrator = new DefaultTranscriptionOrchestrator(
                orchestrationProperties,
                publisher,
                reconciliation,
                engineSelector,
                effectiveMetricsPublisher
        );

        ApplicationStateTracker effectiveStateTracker = new ApplicationStateTracker(publisher);
        RecordingService recordingService = new DefaultRecordingService(
                captureOrchestrator,
                transcriptionOrchestrator,
                effectiveStateTracker,
                orchestrationProperties.getMaxRecordingDurationSeconds()
        );

        return new HotkeyRecordingAdapter(recordingService, hotkeyProperties);
    }
}
