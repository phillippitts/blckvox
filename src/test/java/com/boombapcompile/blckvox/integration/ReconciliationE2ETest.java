package com.boombapcompile.blckvox.integration;

import com.boombapcompile.blckvox.service.orchestration.TranscriptionMetricsPublisher;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPressedEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyReleasedEvent;
import com.boombapcompile.blckvox.service.orchestration.CaptureStateMachine;
import com.boombapcompile.blckvox.service.orchestration.HotkeyRecordingAdapter;
import com.boombapcompile.blckvox.service.orchestration.HotkeyRecordingAdapterBuilder;
import com.boombapcompile.blckvox.service.orchestration.EngineSelectionStrategy;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.ConfidenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.SimplePreferenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.WordOverlapReconciler;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.parallel.DefaultParallelSttService;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import com.boombapcompile.blckvox.testutil.EventCapturingPublisher;
import com.boombapcompile.blckvox.testutil.FakeAudioCaptureService;
import com.boombapcompile.blckvox.testutil.FakeSttEngine;
import com.boombapcompile.blckvox.testutil.SyncExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for Phase 4 reconciliation.
 *
 * <p>Tests the full pipeline: HotkeyPressed → AudioCapture → HotkeyReleased
 * → ParallelSTT → Reconciliation → TranscriptionCompletedEvent
 *
 * <p>Validates each reconciliation strategy and memory leak prevention.
 *
 * <p>Uses test doubles from {@link com.boombapcompile.blckvox.testutil} package.
 */
class ReconciliationE2ETest {

    /** Helper to create a real watchdog for testing */
    static SttEngineWatchdog createWatchdog(SttEngine vosk, SttEngine whisper,
                                             ApplicationEventPublisher publisher) {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        return new SttEngineWatchdog(List.of(vosk, whisper), props, publisher);
    }

    @Test
    void wordOverlapStrategySelectsHigherOverlap() {
        // Setup: Vosk returns "hello", Whisper returns "hello world"
        // Expected: Whisper has higher Jaccard similarity (2/2 vs 1/2)
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "hello", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "hello world", 0.9);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.OVERLAP, 0.5, 1.0);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new WordOverlapReconciler(0.5);

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();

        // Act: Full hotkey flow
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Reconciliation picked Whisper (higher overlap)
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("hello world");
        assertThat(event.result().confidence()).isEqualTo(0.9);
    }

    @Test
    void confidenceStrategySelectsHigherConfidence() {
        // Setup: Vosk 0.85 confidence, Whisper 0.95 confidence
        // Expected: Whisper selected due to higher confidence
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.85);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6, 1.0);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
        assertThat(event.result().confidence()).isEqualTo(0.95);
    }

    @Test
    void preferenceStrategyRespectsVoskPrimary() {
        // Setup: Primary=VOSK, both engines return non-empty text
        // Expected: Vosk selected per preference strategy
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(
                OrchestrationProperties.PrimaryEngine.VOSK
        );

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("vosk text");
        assertThat(event.result().confidence()).isEqualTo(0.9);
    }

    @Test
    void preferenceStrategyRespectsWhisperPrimary() {
        // Setup: Primary=WHISPER, both engines return non-empty text
        // Expected: Whisper selected per preference strategy
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, 1.0);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(
                OrchestrationProperties.PrimaryEngine.WHISPER
        );

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.WHISPER, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.WHISPER, 1000, 200)))
                .build();

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
        assertThat(event.result().confidence()).isEqualTo(0.95);
    }

    @Test
    void reconciliationHandlesOneEngineFailing() {
        // Setup: Vosk fails, Whisper succeeds
        // Expected: Whisper result selected
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "unused", 0.9);
        vosk.healthy = false; // Make Vosk fail
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6, 0.7);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should still get Whisper result
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
    }

    @Test
    void reconciliationMemoryLeakTest() {
        // Setup: Run 100 iterations to verify no memory leaks
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "test text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "test text", 0.95);

        HotkeyRecordingAdapter orchestrator = buildConfidenceOrchestrator(
                capture, vosk, whisper, publisher);

        // Measure memory before
        long memoryBefore = measureMemoryUsage();

        // Act: Run 100 iterations
        for (int i = 0; i < 100; i++) {
            orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
            orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
            publisher.clear(); // Clear events to prevent publisher from hoarding memory
        }

        // Measure memory after
        long memoryAfter = measureMemoryUsage();
        long growthMB = (memoryAfter - memoryBefore) / 1024 / 1024;

        // Assert: Memory growth should be less than 50MB
        assertThat(growthMB).isLessThan(50);
    }

    @Test
    void reconciliationDisabledFallsBackToSingleEngine() {
        // Setup: Reconciliation disabled
        // Expected: Single-engine mode (no "reconciled" engine name)
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                false, ReconciliationProperties.Strategy.CONFIDENCE, 0.6, 0.7); // Disabled

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        HotkeyRecordingAdapter orchestrator = HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should use single engine (vosk, since it's primary)
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("vosk"); // NOT "reconciled"
        assertThat(event.result().text()).isEqualTo("vosk text");
    }
    private static HotkeyProperties fakeHotkeyProps() {
        return new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                java.util.List.of("META"), java.util.List.of(), false);
    }

    private static HotkeyRecordingAdapter buildConfidenceOrchestrator(FakeAudioCaptureService capture,
                                                                      FakeSttEngine vosk,
                                                                      FakeSttEngine whisper,
                                                                      EventCapturingPublisher publisher) {
        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6, 0.7);
        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        return HotkeyRecordingAdapterBuilder.builder()
                .captureService(capture)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(createWatchdog(vosk, whisper, publisher))
                .orchestrationProperties(new OrchestrationProperties(
                        OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200))
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(publisher)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(recProps)
                .metricsPublisher(
                        TranscriptionMetricsPublisher.NOOP)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper,
                        createWatchdog(vosk, whisper, publisher),
                        new OrchestrationProperties(
                                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200)))
                .build();
    }

    private static long measureMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
