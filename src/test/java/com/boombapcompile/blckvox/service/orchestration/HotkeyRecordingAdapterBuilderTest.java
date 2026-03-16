package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.hotkey.TriggerType;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotkeyRecordingAdapterBuilderTest {

    @Test
    void buildWithAllRequiredDependenciesSucceeds() {
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder().build();
        assertThat(adapter).isNotNull();
    }

    @Test
    void buildWithMissingCaptureServiceThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().captureService(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("captureService");
    }

    @Test
    void buildWithMissingVoskEngineThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().voskEngine(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("voskEngine");
    }

    @Test
    void buildWithMissingWhisperEngineThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().whisperEngine(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("whisperEngine");
    }

    @Test
    void buildWithMissingPublisherThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().publisher(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("publisher");
    }

    @Test
    void buildWithoutMetricsPublisherUsesNoop() {
        // No metricsPublisher set — should still build successfully using NOOP
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder().build();
        assertThat(adapter).isNotNull();
    }

    @Test
    void buildWithReconciliationDependencies() {
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder()
                .parallelSttService((pcm, timeout) ->
                        new ParallelSttService.EnginePair(
                                new EngineResult("a", 0.9, List.of("a"), 100L, "vosk", null),
                                new EngineResult("b", 0.8, List.of("b"), 100L, "whisper", null)))
                .transcriptReconciler((v, w) -> TranscriptionResult.of("reconciled", 0.95, "reconciled"))
                .reconciliationProperties(
                        new ReconciliationProperties(true,
                                ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7))
                .build();
        assertThat(adapter).isNotNull();
    }

    @Test
    void buildWithMissingWatchdogThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().watchdog(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("watchdog");
    }

    @Test
    void buildWithMissingOrchestrationPropertiesThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().orchestrationProperties(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("orchestrationProperties");
    }

    @Test
    void buildWithMissingHotkeyPropertiesThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().hotkeyProperties(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hotkeyProperties");
    }

    @Test
    void buildWithMissingCaptureStateMachineThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().captureStateMachine(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("captureStateMachine");
    }

    @Test
    void buildWithMissingEngineSelectorThrows() {
        assertThatThrownBy(() -> fullyPopulatedBuilder().engineSelector(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engineSelector");
    }

    @Test
    void buildWithExplicitMetricsPublisher() {
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder()
                .metricsPublisher(TranscriptionMetricsPublisher.NOOP)
                .build();
        assertThat(adapter).isNotNull();
    }

    @Test
    void buildWithPartialReconciliationUsesDisabled() {
        // Only set parallelSttService but not reconciler/properties → disabled mode
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder()
                .parallelSttService((pcm, timeout) ->
                        new ParallelSttService.EnginePair(null, null))
                .build();
        assertThat(adapter).isNotNull();
    }

    @Test
    void buildWithParallelAndReconcilerButNoPropertiesUsesDisabled() {
        // parallelSttService and transcriptReconciler set, but reconciliationProperties is null
        // Covers the third operand false branch in the && chain at line 155
        HotkeyRecordingAdapter adapter = fullyPopulatedBuilder()
                .parallelSttService((pcm, timeout) ->
                        new ParallelSttService.EnginePair(null, null))
                .transcriptReconciler((v, w) -> TranscriptionResult.of("r", 0.9, "reconciled"))
                .build();
        assertThat(adapter).isNotNull();
    }

    // ---- helpers ----

    private static HotkeyRecordingAdapterBuilder fullyPopulatedBuilder() {
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog();
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);

        return HotkeyRecordingAdapterBuilder.builder()
                .captureService(new FakeCapture())
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(orchProps)
                .hotkeyProperties(new HotkeyProperties(
                        TriggerType.SINGLE_KEY, "D", 300, List.of(), List.of(), false))
                .publisher(e -> { })
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, orchProps));
    }

    static class FakeCapture implements AudioCaptureService {
        @Override public UUID startSession() { return UUID.randomUUID(); }
        @Override public void stopSession(UUID id) { }
        @Override public void cancelSession(UUID id) { }
        @Override public byte[] readAll(UUID id) { return new byte[0]; }
    }

    static class StubEngine implements SttEngine {
        private final String name;
        StubEngine(String name) { this.name = name; }
        @Override public void initialize() { }
        @Override public TranscriptionResult transcribe(byte[] data) {
            return TranscriptionResult.of("text", 1.0, name);
        }
        @Override public String getEngineName() { return name; }
        @Override public boolean isHealthy() { return true; }
        @Override public void close() { }
    }

    static class FakeWatchdog extends SttEngineWatchdog {
        FakeWatchdog() {
            super(List.of(),
                    new SttWatchdogProperties(true, 60, 3, 10, false, 60_000L, 0.3, 10, 5),
                    e -> { });
        }
        @Override
        public boolean isEngineEnabled(String engine) { return true; }
    }
}
