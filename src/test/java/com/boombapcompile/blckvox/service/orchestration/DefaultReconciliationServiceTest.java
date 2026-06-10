package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties.Strategy;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService.EnginePair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultReconciliationService}.
 *
 * <p>Uses stub implementations for all collaborators (no Spring context required).
 */
class DefaultReconciliationServiceTest {

    // --- Constructor null-rejection tests ---

    @Test
    void shouldRejectNullParallelService() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                null, stubReconciler(), enabledProps()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parallel");
    }

    @Test
    void shouldRejectNullReconciler() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                stubParallel(), null, enabledProps()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reconciler");
    }

    @Test
    void shouldRejectNullProps() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                stubParallel(), stubReconciler(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("props");
    }

    // --- Disabled service ---

    @Test
    void disabledServiceShouldReportNotEnabled() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void disabledServiceShouldThrowOnReconcile() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThatThrownBy(() -> disabled.reconcile(new byte[100]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void disabledServiceShouldThrowOnGetStrategy() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThatThrownBy(disabled::getStrategy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    // --- Enabled service: isEnabled ---

    @Test
    void enabledServiceShouldReportEnabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void disabledPropsShouldReportNotEnabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThat(service.isEnabled()).isFalse();
    }

    // --- reconcile() ---

    @Test
    void shouldRejectNullPcm() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThatThrownBy(() -> service.reconcile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pcm");
    }

    @Test
    void shouldThrowWhenReconciliationDisabledButReconcileCalled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThatThrownBy(() -> service.reconcile(new byte[100]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void shouldReconcileSuccessfully() {
        // Vosk confidence 0.5 is below threshold 0.7, so Whisper will be invoked
        EngineResult voskResult = new EngineResult("hello", 0.5, List.of("hello"), 100, "vosk", "{}");
        EngineResult whisperResult = new EngineResult(
                "hello world", 0.95, List.of("hello", "world"), 200, "whisper", "{}");

        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                return new EnginePair(voskResult, whisperResult);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return voskResult;
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                return new EnginePair(vosk, whisperResult);
            }
        };

        TranscriptionResult expectedResult = TranscriptionResult.of("hello world", 0.95, "reconciled");
        TranscriptReconciler reconciler = (vosk, whisper) -> expectedResult;

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, reconciler, enabledProps());

        TranscriptionResult result = service.reconcile(new byte[100]);

        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    // --- getStrategy() ---

    @Test
    void shouldReturnStrategyName() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThat(service.getStrategy()).isEqualTo("SIMPLE");
    }

    @Test
    void shouldReturnConfidenceStrategy() {
        ReconciliationProperties props = new ReconciliationProperties(true, Strategy.CONFIDENCE, 0.6, 0.7);
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), props);
        assertThat(service.getStrategy()).isEqualTo("CONFIDENCE");
    }

    @Test
    void shouldThrowOnGetStrategyWhenDisabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThatThrownBy(service::getStrategy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    // --- Smart upgrade tests ---

    @Test
    void shouldReturnVoskDirectlyWhenConfidenceAboveThreshold() {
        EngineResult highConfidenceVosk = new EngineResult("high confidence", 0.9,
                List.of("high", "confidence"), 50, "vosk", "{}");
        // Whisper should never be called
        boolean[] whisperCalled = {false};
        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                whisperCalled[0] = true;
                return new EnginePair(highConfidenceVosk, null);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return highConfidenceVosk;
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                whisperCalled[0] = true;
                return new EnginePair(vosk, null);
            }
        };

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, stubReconciler(), enabledProps());
        TranscriptionResult result = service.reconcile(new byte[100]);

        assertThat(result.text()).isEqualTo("high confidence");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(whisperCalled[0]).isFalse();
    }

    @Test
    void shouldReconcileWhenVoskConfidenceBelowThreshold() {
        EngineResult lowConfidenceVosk = new EngineResult("uncertain", 0.5,
                List.of("uncertain"), 50, "vosk", "{}");
        EngineResult whisperResult = new EngineResult("clear result", 0.95,
                List.of("clear", "result"), 200, "whisper", "{}");

        boolean[] whisperCalled = {false};
        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                return new EnginePair(lowConfidenceVosk, whisperResult);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return lowConfidenceVosk;
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                whisperCalled[0] = true;
                return new EnginePair(vosk, whisperResult);
            }
        };

        TranscriptReconciler reconciler = (vosk, whisper) ->
                TranscriptionResult.of("reconciled", 0.95, "reconciled");

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, reconciler, enabledProps());
        TranscriptionResult result = service.reconcile(new byte[100]);

        assertThat(whisperCalled[0]).isTrue();
        assertThat(result.text()).isEqualTo("reconciled");
    }

    @Test
    void shouldFallBackToFullReconciliationWhenVoskPreCheckFails() {
        EngineResult voskResult = new EngineResult("vosk fallback", 0.8,
                List.of("vosk"), 50, "vosk", "{}");
        EngineResult whisperResult = new EngineResult("whisper fallback", 0.9,
                List.of("whisper"), 200, "whisper", "{}");

        boolean[] fullReconcileCalled = {false};
        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                fullReconcileCalled[0] = true;
                return new EnginePair(voskResult, whisperResult);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                throw new com.boombapcompile.blckvox.exception.TranscriptionException("Vosk failed");
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                return new EnginePair(vosk, whisperResult);
            }
        };

        TranscriptReconciler reconciler = (vosk, whisper) ->
                TranscriptionResult.of("fallback reconciled", 0.85, "reconciled");

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, reconciler, enabledProps());
        TranscriptionResult result = service.reconcile(new byte[100]);

        assertThat(fullReconcileCalled[0]).isTrue();
        assertThat(result.text()).isEqualTo("fallback reconciled");
    }

    @Test
    void shouldNeverReconcileWhenThresholdIsZero() {
        ReconciliationProperties zeroThreshold = new ReconciliationProperties(
                true, Strategy.SIMPLE, 0.6, 0.0);
        EngineResult voskResult = new EngineResult("vosk", 0.99,
                List.of("vosk"), 50, "vosk", "{}");
        EngineResult whisperResult = new EngineResult("whisper", 0.99,
                List.of("whisper"), 200, "whisper", "{}");

        boolean[] whisperCalled = {false};
        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                return new EnginePair(voskResult, whisperResult);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return voskResult;
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                whisperCalled[0] = true;
                return new EnginePair(vosk, whisperResult);
            }
        };

        TranscriptReconciler reconciler = (vosk, whisper) ->
                TranscriptionResult.of("reconciled", 0.99, "reconciled");

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, reconciler, zeroThreshold);

        // Even though Vosk has 0.99 confidence, threshold=0.0 means confidence < threshold is never true
        // 0.99 >= 0.0 is true, so Vosk is used directly
        // Actually: 0.0 threshold means even 0.0 confidence passes, so always use Vosk alone
        // To "always reconcile", threshold must be set to 1.0 (see next test)
        TranscriptionResult result = service.reconcile(new byte[100]);
        assertThat(result.text()).isEqualTo("vosk");
    }

    @Test
    void shouldUpgradeToWhisperWhenThresholdIsOne() {
        ReconciliationProperties fullThreshold = new ReconciliationProperties(
                true, Strategy.SIMPLE, 0.6, 1.0);
        EngineResult voskResult = new EngineResult("vosk only", 0.3,
                List.of("vosk"), 50, "vosk", "{}");
        EngineResult whisperResult = new EngineResult("whisper result", 0.95,
                List.of("whisper"), 200, "whisper", "{}");

        boolean[] whisperCalled = {false};
        ParallelSttService parallel = new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                return new EnginePair(voskResult, whisperResult);
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return voskResult;
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                whisperCalled[0] = true;
                return new EnginePair(vosk, whisperResult);
            }
        };

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, stubReconciler(), fullThreshold);

        // 0.3 < 1.0, so it WILL upgrade to Whisper
        service.reconcile(new byte[100]);
        assertThat(whisperCalled[0]).isTrue();
    }

    // --- Helpers ---

    private static ReconciliationProperties enabledProps() {
        return new ReconciliationProperties(true, Strategy.SIMPLE, 0.6, 0.7);
    }

    private static ReconciliationProperties disabledProps() {
        return new ReconciliationProperties(false, Strategy.SIMPLE, 0.6, 0.7);
    }

    private static ParallelSttService stubParallel() {
        return new ParallelSttService() {
            @Override
            public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
                return new EnginePair(
                        new EngineResult("stub", 1.0, List.of("stub"), 10, "vosk", "{}"),
                        new EngineResult("stub", 1.0, List.of("stub"), 10, "whisper", "{}")
                );
            }
            @Override
            public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
                return new EngineResult("stub", 1.0, List.of("stub"), 10, "vosk", "{}");
            }
            @Override
            public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult vosk) {
                return new EnginePair(vosk,
                        new EngineResult("stub", 1.0, List.of("stub"), 10, "whisper", "{}"));
            }
        };
    }

    private static TranscriptReconciler stubReconciler() {
        return (vosk, whisper) -> TranscriptionResult.of("stub", 1.0, "reconciled");
    }
}
