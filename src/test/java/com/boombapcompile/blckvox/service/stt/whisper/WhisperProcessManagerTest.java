package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperProcessManagerTest {

    @TempDir
    Path tempDir;

    private WhisperConfig config() {
        return new WhisperConfig(
                "/usr/bin/echo",
                tempDir.resolve("model.bin").toString(),
                2, "en", 1, 1_048_576
        );
    }

    private Path wavFile() throws Exception {
        Path wav = tempDir.resolve("test.wav");
        Files.write(wav, new byte[100]);
        return wav;
    }

    @Test
    void successfulTranscriptionReturnsStdout() throws Exception {
        String expectedOutput = "hello world";
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(0, expectedOutput, "");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        String result = manager.transcribe(wavFile(), config());
        assertThat(result).isEqualTo(expectedOutput);
    }

    @Test
    void nonZeroExitCodeThrowsTranscriptionException() throws Exception {
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(1, "", "some error");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), config()))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Non-zero exit");
    }

    @Test
    void processTimeoutThrowsTranscriptionException() throws Exception {
        // Create a config with a very short timeout
        WhisperConfig shortTimeout = new WhisperConfig(
                "/usr/bin/echo", tempDir.resolve("model.bin").toString(),
                1, "en", 1, 1_048_576
        );
        ProcessFactory factory = (cmd, workDir) -> new HangingProcess();
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), shortTimeout))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Timeout");
    }

    @Test
    void stderrSnippetTruncatedToMaxChars() throws Exception {
        String longStderr = "x".repeat(WhisperConstants.ERROR_SNIPPET_MAX_CHARS + 500);
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(1, "", longStderr);
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), config()))
                .isInstanceOf(TranscriptionException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    // The stderr snippet in the message should not exceed ERROR_SNIPPET_MAX_CHARS
                    // (the full longStderr is not in the message)
                    assertThat(msg).doesNotContain(longStderr);
                });
    }

    @Test
    void closeIsIdempotent() {
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(0, "", "");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        // Call close twice — should not throw
        manager.close();
        manager.close();
    }

    @Test
    void nullWavPathThrowsNpe() {
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(0, "", "");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(null, config()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("wavPath");
    }

    @Test
    void nullConfigThrowsNpe() throws Exception {
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(0, "", "");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cfg");
    }

    @Test
    void ioExceptionDuringProcessStartThrowsTranscriptionException() throws Exception {
        ProcessFactory factory = (cmd, workDir) -> {
            throw new java.io.IOException("cannot start process");
        };
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), config()))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("I/O failure");
    }

    @Test
    void emptyStdoutReturnsEmptyString() throws Exception {
        ProcessFactory factory = (cmd, workDir) -> new FakeProcess(0, "", "");
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        String result = manager.transcribe(wavFile(), config());
        assertThat(result).isEmpty();
    }

    @Test
    void closeWithActiveExecutionsCleansThem() throws Exception {
        CountDownLatch processStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);

        ProcessFactory factory = (cmd, workDir) ->
                new BlockingProcess(processStarted, allowComplete);
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        Thread transcribeThread = new Thread(() -> {
            try {
                manager.transcribe(wavFile(), config());
            } catch (Exception ignored) { }
        });
        transcribeThread.start();

        assertThat(processStarted.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50); // Small delay for activeExecutions.add() to complete

        manager.close();

        allowComplete.countDown();
        transcribeThread.join(5000);
    }

    @Test
    void interruptedDuringWaitSetsInterruptFlagAndThrows() throws Exception {
        ProcessFactory factory = (cmd, workDir) -> new InterruptingWaitProcess();
        WhisperProcessManager manager = new WhisperProcessManager(factory);

        assertThatThrownBy(() -> manager.transcribe(wavFile(), config()))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("I/O failure");

        // InterruptedException handler should have set the interrupt flag
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // Clear flag so it doesn't affect other tests
    }

    // --- Fake Process implementations ---

    /**
     * A Process that completes immediately with configurable exit code, stdout, stderr.
     */
    private static final class FakeProcess extends Process {
        private final int exitCode;
        private final InputStream stdout;
        private final InputStream stderr;
        private volatile boolean destroyed;

        FakeProcess(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
        @Override
        public InputStream getInputStream() {
            return stdout;
        }
        @Override
        public InputStream getErrorStream() {
            return stderr;
        }
        @Override
        public int waitFor() {
            return exitCode;
        }
        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true; // completes immediately
        }
        @Override
        public int exitValue() {
            return exitCode;
        }
        @Override
        public void destroy() {
            destroyed = true;
        }
        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }
        @Override
        public boolean isAlive() {
            return false;
        }
    }

    /**
     * A Process that blocks on waitFor until released via a latch.
     */
    private static final class BlockingProcess extends Process {
        private final CountDownLatch processStarted;
        private final CountDownLatch allowComplete;
        private volatile boolean destroyed;

        BlockingProcess(CountDownLatch processStarted,
                        CountDownLatch allowComplete) {
            this.processStarted = processStarted;
            this.allowComplete = allowComplete;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(
                    "result".getBytes(StandardCharsets.UTF_8));
        }
        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public int waitFor() throws InterruptedException {
            allowComplete.await(10, TimeUnit.SECONDS);
            return 0;
        }
        @Override
        public boolean waitFor(long timeout, TimeUnit unit)
                throws InterruptedException {
            processStarted.countDown();
            return allowComplete.await(timeout, unit);
        }
        @Override
        public int exitValue() {
            if (allowComplete.getCount() != 0) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }
        @Override
        public void destroy() {
            destroyed = true;
            allowComplete.countDown();
        }
        @Override
        public Process destroyForcibly() {
            destroyed = true;
            allowComplete.countDown();
            return this;
        }
        @Override
        public boolean isAlive() {
            return !destroyed && allowComplete.getCount() > 0;
        }
    }

    /**
     * A Process whose waitFor throws InterruptedException.
     */
    private static final class InterruptingWaitProcess extends Process {
        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public int waitFor() throws InterruptedException {
            throw new InterruptedException("test");
        }
        @Override
        public boolean waitFor(long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new InterruptedException("simulated interrupt");
        }
        @Override
        public int exitValue() {
            return 0;
        }
        @Override
        public void destroy() { }
        @Override
        public Process destroyForcibly() {
            return this;
        }
        @Override
        public boolean isAlive() {
            return false;
        }
    }

    /**
     * A Process that never completes (simulates timeout).
     */
    private static final class HangingProcess extends Process {
        private volatile boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public int waitFor() throws InterruptedException {
            Thread.sleep(Long.MAX_VALUE);
            return -1;
        }
        @Override
        public boolean waitFor(long timeout, TimeUnit unit)
                throws InterruptedException {
            Thread.sleep(unit.toMillis(timeout));
            return false; // never completes
        }
        @Override
        public int exitValue() {
            throw new IllegalThreadStateException("still running");
        }
        @Override
        public void destroy() {
            destroyed = true;
        }
        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }
        @Override
        public boolean isAlive() {
            return !destroyed;
        }
    }
}
