package com.boombapcompile.blckvox.service.stt.whisper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessLifecycleManagerTest {

    @Test
    void startProcessReturnsExecution() throws Exception {
        FakeProcess fakeProcess = new FakeProcess("stdout content", "stderr content");
        ProcessFactory factory = (cmd, dir) -> fakeProcess;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("echo", "test"), Path.of("/tmp"), 4096, 4096);

        assertThat(exec).isNotNull();
        assertThat(exec.getProcess()).isSameAs(fakeProcess);
    }

    @Test
    void startProcessCapturesStdoutAndStderr() throws Exception {
        FakeProcess fakeProcess = new FakeProcess("hello stdout", "hello stderr");
        ProcessFactory factory = (cmd, dir) -> fakeProcess;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("test"), Path.of("/tmp"), 4096, 4096);
        exec.flushGobblers();

        assertThat(exec.getStdout()).isEqualTo("hello stdout");
        assertThat(exec.getStderr()).isEqualTo("hello stderr");
    }

    @Test
    void startProcessThrowsWhenFactoryFails() {
        ProcessFactory factory = (cmd, dir) -> { throw new IOException("No such binary"); };
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        assertThatThrownBy(() -> mgr.startProcess(List.of("bad"), Path.of("/tmp"), 4096, 4096))
                .isInstanceOf(IOException.class);
    }

    @Test
    void waitForCompletionReturnsTrueWhenProcessExits() throws Exception {
        FakeProcess process = new FakeProcess("", "");
        process.exitValue = 0;
        process.waitForResult = true;
        ProcessFactory factory = (cmd, dir) -> process;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("test"), Path.of("/tmp"), 4096, 4096);
        boolean completed = mgr.waitForCompletion(exec, 5);

        assertThat(completed).isTrue();
    }

    @Test
    void waitForCompletionReturnsFalseOnTimeout() throws Exception {
        FakeProcess process = new FakeProcess("", "");
        process.waitForResult = false; // simulate timeout
        ProcessFactory factory = (cmd, dir) -> process;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("test"), Path.of("/tmp"), 4096, 4096);
        boolean completed = mgr.waitForCompletion(exec, 1);

        assertThat(completed).isFalse();
    }

    @Test
    void destroyProcessHandlesNull() {
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> null);
        assertThatCode(() -> mgr.destroyProcess(null)).doesNotThrowAnyException();
    }

    @Test
    void destroyProcessHandlesAlreadyDead() {
        FakeProcess process = new FakeProcess("", "");
        process.alive = false;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);
        assertThatCode(() -> mgr.destroyProcess(process)).doesNotThrowAnyException();
    }

    @Test
    void destroyProcessGracefulShutdown() {
        FakeProcess process = new FakeProcess("", "");
        process.alive = true;
        process.exitAfterDestroy = true;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);

        mgr.destroyProcess(process);

        assertThat(process.destroyCalled).isTrue();
    }

    @Test
    void destroyProcessEscalatesToForcible() {
        FakeProcess process = new FakeProcess("", "");
        process.alive = true;
        process.exitAfterDestroy = false; // graceful shutdown fails — stays alive
        process.exitAfterForceDestroy = true; // forcible works
        process.waitForResult = false; // timeout on graceful wait
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);

        mgr.destroyProcess(process);

        assertThat(process.destroyCalled).isTrue();
        assertThat(process.forceDestroyCalled).isTrue();
    }

    @Test
    void cleanupNullIsNoop() {
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> null);
        assertThatCode(() -> mgr.cleanup(null)).doesNotThrowAnyException();
    }

    @Test
    void cleanupDestroysAliveProcess() throws Exception {
        FakeProcess process = new FakeProcess("", "");
        process.alive = true;
        process.exitAfterDestroy = true;
        ProcessFactory factory = (cmd, dir) -> process;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("test"), Path.of("/tmp"), 4096, 4096);
        mgr.cleanup(exec);

        assertThat(process.destroyCalled).isTrue();
    }

    @Test
    void cleanupWithDeadProcessSkipsDestroy() throws Exception {
        FakeProcess process = new FakeProcess("stdout", "stderr");
        process.alive = false; // process already terminated
        ProcessFactory factory = (cmd, dir) -> process;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager(factory);

        ProcessLifecycleManager.ProcessExecution exec =
                mgr.startProcess(List.of("test"), Path.of("/tmp"), 4096, 4096);
        mgr.cleanup(exec);

        // process was not alive → destroyProcess should not be called
        assertThat(process.destroyCalled).isFalse();
    }

    @Test
    void destroyProcessLogsWarningWhenStillAliveAfterForcible() {
        FakeProcess process = new FakeProcess("", "");
        process.alive = true;
        process.exitAfterDestroy = false;
        process.exitAfterForceDestroy = false; // stays alive even after forcible
        process.waitForResult = false;
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);

        mgr.destroyProcess(process);

        assertThat(process.destroyCalled).isTrue();
        assertThat(process.forceDestroyCalled).isTrue();
        // Process is still alive — warning logged (coverage of lines 165-166)
        assertThat(process.alive).isTrue();
    }

    @Test
    void destroyProcessHandlesInterruptedException() {
        InterruptingProcess process = new InterruptingProcess();
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);

        mgr.destroyProcess(process);

        // Thread interrupt flag should be set (re-interrupted per convention)
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear the flag so it doesn't affect other tests
        Thread.interrupted();
    }

    @Test
    void destroyProcessHandlesThrowableDuringDestroy() {
        ThrowingDestroyProcess process = new ThrowingDestroyProcess();
        ProcessLifecycleManager mgr = new ProcessLifecycleManager((cmd, dir) -> process);

        // Should not propagate the RuntimeException from destroy()
        assertThatCode(() -> mgr.destroyProcess(process)).doesNotThrowAnyException();
    }

    @Test
    void cleanupWithNullExecIsNoop() {
        ProcessLifecycleManager manager = new ProcessLifecycleManager(
                (command, workDir) -> { throw new IOException("should not be called"); });
        // Should not throw
        manager.cleanup(null);
    }

    @Test
    void destroyProcessWithNullIsNoop() {
        ProcessLifecycleManager manager = new ProcessLifecycleManager(
                (command, workDir) -> { throw new IOException("should not be called"); });
        // Should not throw
        manager.destroyProcess(null);
    }

    // --- Fake Process ---

    private static class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        boolean alive = false;
        int exitValue = 0;
        boolean waitForResult = true;
        boolean destroyCalled = false;
        boolean forceDestroyCalled = false;
        boolean exitAfterDestroy = true;
        boolean exitAfterForceDestroy = true;

        FakeProcess(String stdoutContent, String stderrContent) {
            this.stdout = new ByteArrayInputStream(stdoutContent.getBytes());
            this.stderr = new ByteArrayInputStream(stderrContent.getBytes());
        }

        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public int exitValue() { return exitValue; }
        @Override public int waitFor() { return exitValue; }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            if (destroyCalled && exitAfterDestroy) {
                alive = false;
                return true;
            }
            if (forceDestroyCalled && exitAfterForceDestroy) {
                alive = false;
                return true;
            }
            return waitForResult;
        }

        @Override public boolean isAlive() { return alive; }

        @Override
        public void destroy() {
            destroyCalled = true;
            if (exitAfterDestroy) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            forceDestroyCalled = true;
            if (exitAfterForceDestroy) {
                alive = false;
            }
            return this;
        }
    }

    /**
     * A process whose destroy() throws RuntimeException,
     * covering the catch(Throwable) path in destroyProcess.
     */
    private static class ThrowingDestroyProcess extends Process {
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public int exitValue() { return 0; }
        @Override public int waitFor() { return 0; }
        @Override public boolean isAlive() { return true; } // must be alive so destroyProcess enters the block
        @Override public void destroy() { throw new RuntimeException("destroy boom"); }
        @Override public Process destroyForcibly() { return this; }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
    }

    /**
     * A process whose waitFor always throws InterruptedException,
     * covering the catch(InterruptedException) path in destroyProcess.
     */
    private static class InterruptingProcess extends Process {
        boolean alive = true;

        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public int exitValue() { return 0; }
        @Override public int waitFor() { return 0; }
        @Override public boolean isAlive() { return alive; }
        @Override public void destroy() { /* no-op, stays alive */ }
        @Override public Process destroyForcibly() { return this; }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit)
                throws InterruptedException {
            throw new InterruptedException("simulated interrupt");
        }
    }
}
