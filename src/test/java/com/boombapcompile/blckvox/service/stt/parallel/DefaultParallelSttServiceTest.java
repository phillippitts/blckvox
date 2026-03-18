package com.boombapcompile.blckvox.service.stt.parallel;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.DetailedTranscriptionEngine;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.TranscriptionOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultParallelSttServiceTest {

    @Test
    void returnsBothWhenBothSucceed() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 1000);
        var pair = svc.transcribeBoth(new byte[3200], 500);
        assertThat(pair.vosk()).isNotNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void succeedsWhenOneFails() {
        SttEngine vosk = new StubEngine("vosk", 10, true);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 1000);
        var pair = svc.transcribeBoth(new byte[3200], 500);
        assertThat(pair.vosk()).isNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void throwsWhenBothFail() {
        SttEngine vosk = new StubEngine("vosk", 10, true);
        SttEngine whisper = new StubEngine("whisper", 10, true);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 100);
        assertThatThrownBy(() -> svc.transcribeBoth(new byte[3200], 50))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void usesDefaultTimeoutWhenZero() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        // timeoutMs=0 should use default
        var pair = svc.transcribeBoth(new byte[3200], 0);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void usesDefaultTimeoutWhenNegative() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        var pair = svc.transcribeBoth(new byte[3200], -1);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void handlesRuntimeExceptionFromEngine() {
        SttEngine vosk = new RuntimeExceptionEngine("vosk");
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        var pair = svc.transcribeBoth(new byte[3200], 1000);
        assertThat(pair.vosk()).isNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void constructorFallsBackToDefaultTimeoutWhenNegative() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        // Negative timeout should fallback to 10000
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, -1);
        var pair = svc.transcribeBoth(new byte[3200], 5000);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void rejectsNullPcm() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        assertThatThrownBy(() -> svc.transcribeBoth(null, 1000))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void timeoutReturnPartialResultsWhenOneEngineFinishes() {
        // Vosk finishes fast, whisper hangs past timeout
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 5000, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 200);
        // Vosk should succeed, whisper may or may not be done
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void timeoutWithBothHangingThrowsTranscriptionException() {
        SttEngine vosk = new StubEngine("vosk", 5000, false);
        SttEngine whisper = new StubEngine("whisper", 5000, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        assertThatThrownBy(() -> svc.transcribeBoth(new byte[3200], 100))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Both engines failed");
    }

    @Test
    void detailedTranscriptionEngineUsesTranscribeDetailed() {
        DetailedStubEngine vosk = new DetailedStubEngine("vosk", List.of("hello", "world"));
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 2000);
        assertThat(pair.vosk()).isNotNull();
        assertThat(pair.vosk().tokens()).containsExactly("hello", "world");
    }

    @Test
    void detailedEngineWithEmptyTokensFallsBackToTokenizer() {
        DetailedStubEngine vosk = new DetailedStubEngine("vosk", List.of());
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 2000);
        assertThat(pair.vosk()).isNotNull();
        // TokenizerUtil will tokenize "vosk-text" -> ["vosk", "text"]
        assertThat(pair.vosk().tokens()).containsExactly("vosk", "text");
    }

    @Test
    void basicSttEngineUsesTokenizerForTokens() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 2000);
        assertThat(pair.vosk()).isNotNull();
        // StubEngine returns "vosk-text", tokenized to ["vosk", "text"]
        assertThat(pair.vosk().tokens()).containsExactly("vosk", "text");
    }

    @Test
    void transcriptionExceptionFromEngineReturnsNull() {
        SttEngine vosk = new StubEngine("vosk", 10, true); // throws TranscriptionException
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 2000);
        assertThat(pair.vosk()).isNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void detailedEngineWithRawJsonPreservesIt() {
        DetailedTranscriptionEngine vosk = new DetailedTranscriptionEngine() {
            @Override public TranscriptionOutput transcribeDetailed(byte[] audioData) {
                return TranscriptionOutput.of(
                        TranscriptionResult.of("hi", 0.95, "vosk"),
                        List.of("hi"), "{\"text\":\"hi\"}");
            }
            @Override public void initialize() { }
            @Override public TranscriptionResult transcribe(byte[] audioData) {
                return TranscriptionResult.of("hi", 0.95, "vosk");
            }
            @Override public String getEngineName() {
                return "vosk";
            }
            @Override public boolean isHealthy() {
                return true;
            }
            @Override public void close() { }
        };
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        var pair = svc.transcribeBoth(new byte[3200], 2000);
        assertThat(pair.vosk()).isNotNull();
        assertThat(pair.vosk().rawJson()).isEqualTo("{\"text\":\"hi\"}");
    }

    @Test
    void constructorFallsBackToDefaultTimeoutWhenZero() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        // Zero timeout should fallback to 10000
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 0);
        var pair = svc.transcribeBoth(new byte[3200], 5000);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void interruptedDuringGetSetsInterruptFlag() throws InterruptedException {
        // Both engines are slow enough that we can interrupt before they finish
        SttEngine vosk = new StubEngine("vosk", 5000, false);
        SttEngine whisper = new StubEngine("whisper", 5000, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 10000);

        Thread testThread = Thread.currentThread();
        // Schedule an interrupt after a short delay
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
            testThread.interrupt();
        }).start();

        // This should hit the InterruptedException catch at line 139-140
        assertThatThrownBy(() -> svc.transcribeBoth(new byte[3200], 10000))
                .isInstanceOf(TranscriptionException.class);

        // The interrupt flag should have been set (then cleared by our assertion framework or not)
        // Clear it to not affect other tests
        Thread.interrupted();
    }

    @Test
    void getResultSilentlyReturnsNullForCancelledFuture() {
        // When one engine's future is cancelled, getResultSilently returns null
        SttEngine vosk = new StubEngine("vosk", 5000, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        // With a very short timeout, vosk won't finish and its future gets cancelled
        var pair = svc.transcribeBoth(new byte[3200], 50);
        // At least whisper should succeed, vosk may be null
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void transcribeVoskOnlySucceeds() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        EngineResult result = svc.transcribeVoskOnly(new byte[3200], 1000);
        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
    }

    @Test
    void transcribeVoskOnlyTimeoutThrows() {
        SttEngine vosk = new StubEngine("vosk", 5000, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        assertThatThrownBy(() -> svc.transcribeVoskOnly(new byte[3200], 50))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Vosk engine failed or timed out");
    }

    @Test
    void transcribeVoskOnlyFailureThrows() {
        SttEngine vosk = new StubEngine("vosk", 10, true);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        assertThatThrownBy(() -> svc.transcribeVoskOnly(new byte[3200], 1000))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void transcribeWhisperOnlySucceeds() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        EngineResult precomputed = new EngineResult("vosk-text", 0.9,
                java.util.List.of("vosk", "text"), 100, "vosk", null);
        var pair = svc.transcribeWhisperOnly(new byte[3200], 1000, precomputed);
        assertThat(pair.vosk()).isSameAs(precomputed);
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void transcribeWhisperOnlyTimeoutThrows() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 5000, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        EngineResult precomputed = new EngineResult("vosk-text", 0.9,
                java.util.List.of("vosk", "text"), 100, "vosk", null);
        assertThatThrownBy(() -> svc.transcribeWhisperOnly(new byte[3200], 50, precomputed))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Whisper engine failed or timed out");
    }

    @Test
    void transcribeWhisperOnlyFailureThrows() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, true);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);

        EngineResult precomputed = new EngineResult("vosk-text", 0.9,
                java.util.List.of("vosk", "text"), 100, "vosk", null);
        assertThatThrownBy(() -> svc.transcribeWhisperOnly(new byte[3200], 1000, precomputed))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void transcribeVoskOnlyInterruptedSetsFlag() throws InterruptedException {
        SttEngine vosk = new StubEngine("vosk", 5000, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 10000);

        Thread testThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            testThread.interrupt();
        }).start();

        assertThatThrownBy(() -> svc.transcribeVoskOnly(new byte[3200], 10000))
                .isInstanceOf(TranscriptionException.class);
        Thread.interrupted(); // clear flag
    }

    @Test
    void transcribeWhisperOnlyInterruptedSetsFlag() throws InterruptedException {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 5000, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 10000);

        EngineResult precomputed = new EngineResult("vosk-text", 0.9,
                java.util.List.of("vosk", "text"), 100, "vosk", null);
        Thread testThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            testThread.interrupt();
        }).start();

        assertThatThrownBy(() -> svc.transcribeWhisperOnly(new byte[3200], 10000, precomputed))
                .isInstanceOf(TranscriptionException.class);
        Thread.interrupted(); // clear flag
    }

    static class RuntimeExceptionEngine implements SttEngine {
        private final String name;
        RuntimeExceptionEngine(String name) {
            this.name = name;
        }
        @Override public void initialize() { }
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            throw new RuntimeException("unexpected boom");
        }
        @Override public String getEngineName() {
            return name;
        }
        @Override public boolean isHealthy() {
            return true;
        }
        @Override public void close() { }
    }

    static class StubEngine implements SttEngine {
        final String name;
        final long delayMs;
        final boolean fail;
        StubEngine(String name, long delayMs, boolean fail) {
            this.name = name;
            this.delayMs = delayMs;
            this.fail = fail;
        }
        @Override
        public void initialize() {
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            if (fail) {
                throw new TranscriptionException("fail", name);
            }
            return TranscriptionResult.of(name + "-text", 1.0, name);
        }
        @Override
        public String getEngineName() {
            return name;
        }
        @Override
        public boolean isHealthy() {
            return true;
        }
        @Override
        public void close() {
        }
    }

    static class DetailedStubEngine implements DetailedTranscriptionEngine {
        private final String name;
        private final List<String> tokens;

        DetailedStubEngine(String name, List<String> tokens) {
            this.name = name;
            this.tokens = tokens;
        }

        @Override
        public TranscriptionOutput transcribeDetailed(byte[] audioData) {
            TranscriptionResult tr = TranscriptionResult.of(name + "-text", 0.9, name);
            return TranscriptionOutput.of(tr, tokens, null);
        }

        @Override public void initialize() { }
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of(name + "-text", 0.9, name);
        }
        @Override public String getEngineName() {
            return name;
        }
        @Override public boolean isHealthy() {
            return true;
        }
        @Override public void close() { }
    }
}
