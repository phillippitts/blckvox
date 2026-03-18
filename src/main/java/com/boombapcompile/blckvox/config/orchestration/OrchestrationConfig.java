package com.boombapcompile.blckvox.config.orchestration;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.orchestration.ApplicationStateTracker;
import com.boombapcompile.blckvox.service.orchestration.CaptureOrchestrator;
import com.boombapcompile.blckvox.service.orchestration.CaptureStateMachine;
import com.boombapcompile.blckvox.service.orchestration.DefaultCaptureOrchestrator;
import com.boombapcompile.blckvox.service.orchestration.DefaultRecordingService;
import com.boombapcompile.blckvox.service.orchestration.DefaultReconciliationService;
import com.boombapcompile.blckvox.service.orchestration.DefaultTranscriptionOrchestrator;
import com.boombapcompile.blckvox.service.orchestration.HotkeyRecordingAdapter;
import com.boombapcompile.blckvox.service.orchestration.EngineSelectionStrategy;
import com.boombapcompile.blckvox.service.orchestration.ReconciliationService;
import com.boombapcompile.blckvox.service.orchestration.RecordingService;
import com.boombapcompile.blckvox.service.orchestration.TranscriptionMetricsPublisher;
import com.boombapcompile.blckvox.service.orchestration.TranscriptionOrchestrator;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the orchestration beans: CaptureOrchestrator, TranscriptionOrchestrator,
 * RecordingService, and HotkeyRecordingAdapter.
 */
@Configuration
public class OrchestrationConfig {

    private final AudioCaptureService captureService;
    private final List<SttEngine> sttEngines;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties orchestrationProperties;
    private final HotkeyProperties hotkeyProperties;
    private final ApplicationEventPublisher publisher;
    private final TranscriptionMetricsPublisher metricsPublisher;
    private final ReconciliationDependencies reconciliationDeps;

    public OrchestrationConfig(AudioCaptureService captureService,
                               List<SttEngine> sttEngines,
                               SttEngineWatchdog watchdog,
                               OrchestrationProperties orchestrationProperties,
                               HotkeyProperties hotkeyProperties,
                               ApplicationEventPublisher publisher,
                               TranscriptionMetricsPublisher metricsPublisher,
                               @Autowired(required = false)
                               ReconciliationDependencies reconciliationDeps) {
        this.captureService = captureService;
        this.sttEngines = List.copyOf(sttEngines);
        this.watchdog = watchdog;
        this.orchestrationProperties = orchestrationProperties;
        this.hotkeyProperties = hotkeyProperties;
        this.publisher = publisher;
        this.metricsPublisher = metricsPublisher;
        this.reconciliationDeps = reconciliationDeps;
    }

    @Bean
    public CaptureStateMachine captureStateMachine() {
        return new CaptureStateMachine();
    }

    @Bean
    public EngineSelectionStrategy engineSelectionStrategy() {
        return new EngineSelectionStrategy(sttEngines,
                watchdog, orchestrationProperties);
    }

    @Bean
    public CaptureOrchestrator captureOrchestrator(CaptureStateMachine captureStateMachine) {
        return new DefaultCaptureOrchestrator(captureService, captureStateMachine);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public TranscriptionOrchestrator transcriptionOrchestrator(EngineSelectionStrategy engineSelector) {
        return new DefaultTranscriptionOrchestrator(
                orchestrationProperties, publisher,
                DefaultReconciliationService.disabled(),
                engineSelector, metricsPublisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
    public TranscriptionOrchestrator reconciledTranscriptionOrchestrator(EngineSelectionStrategy engineSelector) {
        ReconciliationService reconciliation = new DefaultReconciliationService(
                reconciliationDeps.getParallelSttService(),
                reconciliationDeps.getTranscriptReconciler(),
                reconciliationDeps.getReconciliationProperties());
        return new DefaultTranscriptionOrchestrator(
                orchestrationProperties, publisher,
                reconciliation, engineSelector, metricsPublisher);
    }

    @Bean
    public RecordingService recordingService(CaptureOrchestrator captureOrchestrator,
                                             TranscriptionOrchestrator transcriptionOrchestrator,
                                             ApplicationStateTracker stateTracker) {
        return new DefaultRecordingService(captureOrchestrator, transcriptionOrchestrator, stateTracker,
                orchestrationProperties.getMaxRecordingDurationSeconds());
    }

    @Bean
    public HotkeyRecordingAdapter hotkeyRecordingAdapter(RecordingService recordingService) {
        return new HotkeyRecordingAdapter(recordingService, hotkeyProperties);
    }
}
