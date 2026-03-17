package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperProcessManagerJsonTest {

    @Test
    void includesJsonFlagWhenOutputModeJson() throws Exception {
        CapturingFactory factory = new CapturingFactory("{}\n");
        WhisperProcessManager mgr = new WhisperProcessManager(factory, "json");
        Path wav = Files.createTempFile("wpmt-", ".wav");
        try {
            String out = mgr.transcribe(wav, new WhisperConfig());
            assertThat(out).isEqualTo("{}");
            // Verify -oj is present
            assertThat(factory.lastCommand).contains("-oj");
        } finally {
            Files.deleteIfExists(wav);
            mgr.close();
        }
    }

    @Test
    void capsStdoutAtConfiguredMaxBytes() throws Exception {
        // Configure a very small cap: 16 bytes
        WhisperConfig cfg = new WhisperConfig("bin", "model", 10, "en", 4, 16, 0.85);
        String longJson = "{" + "\"text\":\"" + "x".repeat(100) + "\"}"; // ~100 chars
        CapturingFactory factory = new CapturingFactory(longJson + "\n");
        WhisperProcessManager mgr = new WhisperProcessManager(factory, "json");
        Path wav = Files.createTempFile("wpmt-", ".wav");
        try {
            String out = mgr.transcribe(wav, cfg);
            assertThat(out.length()).isLessThanOrEqualTo(16);
        } finally {
            Files.deleteIfExists(wav);
            mgr.close();
        }
    }

    // ---- Test doubles ----
    static final class CapturingFactory implements ProcessFactory {
        volatile List<String> lastCommand = new ArrayList<>();
        private final String stdout;
        CapturingFactory(String stdout) {
            this.stdout = stdout;
        }
        @Override
        public Process start(List<String> command, Path workingDir) {
            lastCommand = List.copyOf(command);
            return new FakeProcess(stdout);
        }
    }

    static final class FakeProcess extends Process {
        private final ByteArrayInputStream out;
        private final ByteArrayInputStream err = new ByteArrayInputStream(new byte[0]);
        private boolean alive = true;
        FakeProcess(String stdout) {
            this.out = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
        }
        @Override public java.io.OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream();
        }
        @Override public InputStream getInputStream() {
            return out;
        }
        @Override public InputStream getErrorStream() {
            return err;
        }
        @Override public int waitFor() {
            alive = false;
            return 0;
        }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            alive = false;
            return true;
        }
        @Override public int exitValue() {
            return 0;
        }
        @Override public void destroy() {
            alive = false;
        }
        @Override public Process destroyForcibly() {
            alive = false;
            return this;
        }
        @Override public boolean isAlive() {
            return alive;
        }
    }
}
