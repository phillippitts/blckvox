package com.boombapcompile.blckvox.config.orchestration;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.orchestration.ApplicationStateTracker;
import com.boombapcompile.blckvox.service.orchestration.CaptureOrchestrator;
import com.boombapcompile.blckvox.service.orchestration.CaptureStateMachine;
import com.boombapcompile.blckvox.service.orchestration.EngineSelectionStrategy;
import com.boombapcompile.blckvox.service.orchestration.HotkeyRecordingAdapter;
import com.boombapcompile.blckvox.service.orchestration.RecordingService;
import com.boombapcompile.blckvox.service.orchestration.TranscriptionMetricsPublisher;
import com.boombapcompile.blckvox.service.orchestration.TranscriptionOrchestrator;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrchestrationConfigTest {

    private final AudioCaptureService captureService = mock(AudioCaptureService.class);
    private final SttEngine voskEngine = mock(SttEngine.class);
    private final SttEngine whisperEngine = mock(SttEngine.class);
    private final SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
    private final OrchestrationProperties orchProps = new OrchestrationProperties(
            OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
    private final HotkeyProperties hotkeyProps = new HotkeyProperties(
            TriggerType.MODIFIER_COMBO, "J", 300, List.of("META"), List.of(), false);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TranscriptionMetricsPublisher metricsPublisher = TranscriptionMetricsPublisher.NOOP;

    @Test
    void captureStateMachineCreatesNewInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        CaptureStateMachine csm = config.captureStateMachine();
        assertThat(csm).isNotNull();
    }

    @Test
    void engineSelectionStrategyCreatesNewInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        EngineSelectionStrategy strategy = config.engineSelectionStrategy();
        assertThat(strategy).isNotNull();
    }

    @Test
    void captureOrchestratorCreatesDefaultInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        CaptureOrchestrator orchestrator = config.captureOrchestrator(new CaptureStateMachine());
        assertThat(orchestrator).isNotNull();
    }

    @Test
    void transcriptionOrchestratorCreatesNonReconciledInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        EngineSelectionStrategy strategy = config.engineSelectionStrategy();
        TranscriptionOrchestrator orchestrator = config.transcriptionOrchestrator(strategy);
        assertThat(orchestrator).isNotNull();
    }

    @Test
    void reconciledTranscriptionOrchestratorCreatesReconciledInstance() {
        ParallelSttService parallelSttService = mock(ParallelSttService.class);
        TranscriptReconciler reconciler = mock(TranscriptReconciler.class);
        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);
        ReconciliationDependencies deps = new ReconciliationDependencies(
                parallelSttService, reconciler, recProps);

        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, deps);

        EngineSelectionStrategy strategy = config.engineSelectionStrategy();
        TranscriptionOrchestrator orchestrator = config.reconciledTranscriptionOrchestrator(strategy);
        assertThat(orchestrator).isNotNull();
    }

    @Test
    void recordingServiceCreatesDefaultInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        CaptureOrchestrator captureOrch = config.captureOrchestrator(new CaptureStateMachine());
        EngineSelectionStrategy strategy = config.engineSelectionStrategy();
        TranscriptionOrchestrator transcriptionOrch = config.transcriptionOrchestrator(strategy);
        ApplicationStateTracker stateTracker = new ApplicationStateTracker(publisher);

        RecordingService recordingService = config.recordingService(
                captureOrch, transcriptionOrch, stateTracker);
        assertThat(recordingService).isNotNull();
    }

    @Test
    void hotkeyRecordingAdapterCreatesInstance() {
        OrchestrationConfig config = new OrchestrationConfig(
                captureService, voskEngine, whisperEngine, watchdog,
                orchProps, hotkeyProps, publisher, metricsPublisher, null);

        RecordingService recordingService = mock(RecordingService.class);
        HotkeyRecordingAdapter adapter = config.hotkeyRecordingAdapter(recordingService);
        assertThat(adapter).isNotNull();
    }

    @Test
    void reconciliationDependenciesGettersReturnInjectedValues() {
        ParallelSttService parallelSttService = mock(ParallelSttService.class);
        TranscriptReconciler reconciler = mock(TranscriptReconciler.class);
        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);

        ReconciliationDependencies deps = new ReconciliationDependencies(
                parallelSttService, reconciler, recProps);

        assertThat(deps.getParallelSttService()).isSameAs(parallelSttService);
        assertThat(deps.getTranscriptReconciler()).isSameAs(reconciler);
        assertThat(deps.getReconciliationProperties()).isSameAs(recProps);
    }
}
