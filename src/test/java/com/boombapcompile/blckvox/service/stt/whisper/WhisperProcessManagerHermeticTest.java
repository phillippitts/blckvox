package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.ProcessBehavior;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.StubProcessFactory;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.TestProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperProcessManagerHermeticTest {

    @Test
    void successReturnsStdout() throws Exception {
        // Arrange
        ProcessBehavior behavior = new ProcessBehavior("hello world", "", 0, 0);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576, 0.85);
        Path wav = Files.createTempFile("whisper-", ".wav");

        // Act
        String out = mgr.transcribe(wav, cfg);

        // Assert
        assertThat(out).contains("hello world");
    }

    @Test
    void nonZeroExitThrowsWithStderrSnippet() throws Exception {
        ProcessBehavior behavior = new ProcessBehavior("", "something went wrong", 1, 0);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2, 1048576, 0.85);
        Path wav = Files.createTempFile("whisper-", ".wav");

        assertThatThrownBy(() -> mgr.transcribe(wav, cfg))
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("Non-zero exit")
            .hasMessageContaining("stderr=")
            .hasMessageContaining("engine: whisper");
    }

    @Test
    void timeoutKillsProcessAndThrows() throws Exception {
        // Process that never terminates by itself
        ProcessBehavior behavior = new ProcessBehavior("", "", 0, -1 /*never finish*/);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 1, "en", 2, 1048576, 0.85);
        Path wav = Files.createTempFile("whisper-", ".wav");

        long start = System.nanoTime();
        assertThatThrownBy(() -> mgr.transcribe(wav, cfg))
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("Timeout after 1s");
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        // Should not take excessively longer than timeout (allow some overhead)
        assertThat(durationMs).isLessThan(5000);

        // Manager close should terminate gobbler threads
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> tp.wasDestroyCalled());
    }
}
