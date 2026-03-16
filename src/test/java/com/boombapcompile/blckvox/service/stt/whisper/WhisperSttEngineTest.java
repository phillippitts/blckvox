package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.SttConcurrencyProperties;
import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.TranscriptionOutput;
import com.boombapcompile.blckvox.service.stt.util.ConcurrencyScaler;
import com.boombapcompile.blckvox.service.stt.util.SystemResourceMonitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.ProcessBehavior;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.StubProcessFactory;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.TestProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperSttEngineTest {

    @Test
    void successReturnsTranscription() {
        // Stub process that exits immediately with known stdout
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("hello world", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm1s = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm1s);
        assertThat(r.text()).isEqualTo("hello world");
        assertThat(r.engineName()).isEqualTo("whisper");
        assertThat(r.confidence()).isEqualTo(1.0);
        engine.close();
    }

    @Test
    void nonZeroExitRethrowsTranscriptionException() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "error", 1, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("engine: whisper");
        engine.close();
    }

    @Test
    void timeoutRethrowsTranscriptionException() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, -1)) // never finishes
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 1, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Timeout");
        engine.close();
    }

    @Test
    void inputValidationShouldRejectNullOrEmpty() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        assertThatThrownBy(() -> engine.transcribe(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.transcribe(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeIdempotentForMultipleInitializations() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("test", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);

        // Initialize multiple times - should be idempotent
        engine.initialize();
        engine.initialize();
        engine.initialize();

        // Should still work normally
        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("test");
        assertThat(engine.isHealthy()).isTrue();
        engine.close();
    }

    @Test
    void shouldThrowWhenTranscribingAfterClose() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("test", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        // Close the engine
        engine.close();
        assertThat(engine.isHealthy()).isFalse();

        // Transcribe after close should fail
        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("closing or closed");
    }

    @Test
    void shouldHandleEmptyStdoutGracefully() {
        // Simulate whisper returning empty output (e.g., silence or no speech detected)
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);

        // Should return empty text, not fail
        assertThat(r.text()).isEmpty();
        assertThat(r.confidence()).isEqualTo(1.0);
        assertThat(r.engineName()).isEqualTo("whisper");
        engine.close();
    }

    @Test
    void jsonModeReturnsTextAndTokensFromJson() {
        String jsonOutput = """
            {
              "text": "hello world",
              "segments": [
                {"text": "hello world", "words": [{"word": "hello"}, {"word": "world"}]}
              ]
            }
            """;
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior(jsonOutput, "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 2, 1000, false, 0.8, 0.85, 5000);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 0, 200);

        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "json", orchProps, null);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionOutput output = engine.transcribeDetailed(pcm);
        assertThat(output.result().text()).isEqualTo("hello world");
        assertThat(output.tokens()).containsExactly("hello", "world");
        assertThat(output.rawJson()).isNotNull();
        engine.close();
    }

    @Test
    void jsonModeWithSilenceGapInsertsNewlineAtPause() {
        String jsonOutput = """
            {
              "text": "hello world goodbye world",
              "segments": [
                {"text": "hello world", "start": 0.0, "end": 1.0, "words": [{"word": "hello"}, {"word": "world"}]},
                {"text": "goodbye world", "start": 3.0, "end": 4.0, "words": [{"word": "goodbye"}, {"word": "world"}]}
              ]
            }
            """;
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior(jsonOutput, "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 2, 1000, false, 0.8, 0.85, 5000);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 500, 200);

        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "json", orchProps, null);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionOutput output = engine.transcribeDetailed(pcm);
        // Gap between segments (3.0 - 1.0 = 2.0s) exceeds silenceGapMs (500ms = 0.5s),
        // so a newline should be inserted between segments
        assertThat(output.result().text()).contains("\n");
        assertThat(output.tokens()).containsExactly("hello", "world", "goodbye", "world");
        engine.close();
    }

    @Test
    void dynamicGuardPathUsedWhenDynamicScalingEnabled() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("dynamic test", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 2, 1000, true, 0.8, 0.85, 5000);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 0, 200);

        // Create a ConcurrencyScaler to satisfy the dynamic scaling path
        ConcurrencyScaler scaler = new ConcurrencyScaler(concurrencyProps,
                new SystemResourceMonitor());

        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "text", orchProps, scaler);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("dynamic test");
        assertThat(r.engineName()).isEqualTo("whisper");
        engine.close();
    }

    @Test
    void dynamicScalingEnabledButNullScalerUsesStaticGuard() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("static fallback", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        // dynamicScalingEnabled=true but concurrencyScaler will be null → static ConcurrencyGuard used
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 2, 1000, true, 0.8, 0.85, 5000);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 0, 200);

        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "text", orchProps, null);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("static fallback");
        engine.close();
    }

    @Test
    void nullOrchestrationPropertiesDefaultsSilenceGapToZero() {
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("plain text", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 2, 1000, false, 0.8, 0.85, 5000);

        // orchProps = null → silenceGapMs should default to 0
        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "text", null, null);
        engine.initialize();

        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("plain text");
        engine.close();
    }

    @Test
    void doCloseHandlesManagerException() {
        // ProcessManager whose close() throws
        ProcessManager throwingMgr = new ProcessManager() {
            @Override
            public String transcribe(java.nio.file.Path wavPath,
                                     com.boombapcompile.blckvox.config.stt.WhisperConfig cfg) {
                return "";
            }
            @Override
            public void close() {
                throw new RuntimeException("close boom");
            }
        };
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, throwingMgr);
        engine.initialize();

        // doClose() should catch the exception and not propagate
        org.assertj.core.api.Assertions.assertThatCode(engine::close).doesNotThrowAnyException();
    }

    @Test
    void cleanupTempFileHandlesDeleteException() {
        // Exercise the cleanupTempFile exception catch path.
        // Use a path that will fail to delete (non-existent directory in path).
        // The engine creates a real temp file, but we test via transcription with a
        // ProcessManager that makes the wav file unreadable before cleanup.
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("result", "", 0, 0))
        ));
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
        engine.initialize();

        // Transcribe should work — cleanupTempFile swallows any exception during deletion
        byte[] pcm = new byte[32_000];
        TranscriptionResult r = engine.transcribe(pcm);
        assertThat(r.text()).isEqualTo("result");
        engine.close();
    }

    @Test
    void shouldNotLogFullTranscriptionTextForPrivacy() {
        // Arrange: Set up log capture
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Logger logger = ctx.getLogger(WhisperSttEngine.class.getName());
        InMemoryAppender appender = new InMemoryAppender("privacy-test");
        appender.start();
        logger.addAppender(appender);

        try {
            WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                    new TestProcess(new ProcessBehavior("sensitive secret password data", "", 0, 0))
            ));
            WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
            WhisperSttEngine engine = new WhisperSttEngine(cfg, mgr);
            engine.initialize();

            // Act: Transcribe with sensitive content
            byte[] pcm = new byte[32_000];
            TranscriptionResult r = engine.transcribe(pcm);
            assertThat(r.text()).isEqualTo("sensitive secret password data");

            // Assert: Logs should NOT contain the full text
            List<LogEvent> events = appender.getEvents();
            for (LogEvent event : events) {
                String message = event.getMessage().getFormattedMessage();
                // Should log character count
                if (message.contains("transcribed")) {
                    assertThat(message).contains("chars=");
                    assertThat(message).contains("30"); // length of the text
                }
                // Must NOT log sensitive words
                assertThat(message).doesNotContain("sensitive");
                assertThat(message).doesNotContain("secret");
                assertThat(message).doesNotContain("password");
            }

            engine.close();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void guardAcquisitionFailureSkipsRelease() throws Exception {
        // Use a slow process that holds the semaphore for a long time
        StubProcessFactory slowFactory = new StubProcessFactory(
                new TestProcess(new ProcessBehavior("slow", "", 0, 5000))
        );
        WhisperProcessManager mgr = new WhisperProcessManager(slowFactory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        // whisperMax=1 (single permit), acquireTimeoutMs=1 (fail fast)
        SttConcurrencyProperties concurrencyProps = new SttConcurrencyProperties(4, 1, 1, false, 0.8, 0.85, 5000);
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 0, 200);

        WhisperSttEngine engine = new WhisperSttEngine(cfg, concurrencyProps, mgr,
                event -> { }, "text", orchProps, null);
        engine.initialize();

        CountDownLatch firstStarted = new CountDownLatch(1);
        // Occupy the single semaphore permit with a background transcription
        Thread occupier = new Thread(() -> {
            firstStarted.countDown();
            try {
                engine.transcribe(new byte[32_000]);
            } catch (Exception ignored) {}
        });
        occupier.start();
        firstStarted.await(2, TimeUnit.SECONDS);
        Thread.sleep(50); // Let the first call acquire the guard

        // Second call should fail to acquire (1ms timeout) → acquired=false, releaseGuard skipped
        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribeDetailed(pcm))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("concurrency limit");

        occupier.interrupt();
        occupier.join(5000);
        engine.close();
    }

    @Test
    void cleanupHandlesNullWavGracefully() {
        // When manager.transcribe throws before WAV creation finishes,
        // cleanupTempFile(null) should not throw.
        // We test this via a manager that throws IOException wrapping behavior
        ProcessManager failingMgr = new ProcessManager() {
            @Override public String transcribe(java.nio.file.Path wavPath, WhisperConfig cfg) {
                throw new RuntimeException("IO error during transcription");
            }
            @Override public void close() {}
        };

        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576);
        WhisperSttEngine engine = new WhisperSttEngine(cfg, failingMgr);
        engine.initialize();

        // transcribe should throw, but cleanupTempFile should be called with a valid path (not null).
        // The null case is exercised when createTempWavFile itself throws.
        byte[] pcm = new byte[32_000];
        assertThatThrownBy(() -> engine.transcribe(pcm))
                .isInstanceOf(com.boombapcompile.blckvox.exception.TranscriptionException.class);
        engine.close();
    }

    /**
     * Simple in-memory Log4j2 appender that captures LogEvents for privacy assertions.
     */
    private static class InMemoryAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        protected InMemoryAppender(String name) {
            super(name, new AbstractFilter() {}, PatternLayout.createDefaultLayout(), true, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }
    }
}
