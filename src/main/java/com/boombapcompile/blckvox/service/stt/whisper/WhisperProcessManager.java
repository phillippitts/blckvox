package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.exception.TranscriptionExceptionBuilder;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates execution of the external whisper.cpp process for transcription.
 *
 * <p>This class coordinates three specialized components to execute whisper.cpp:
 * <ul>
 *   <li>{@link WhisperCommandBuilder} - builds CLI command arrays</li>
 *   <li>{@link ProcessLifecycleManager} - manages process lifecycle and timeouts</li>
 *   <li>{@link ProcessStreamHandler} - handles concurrent stream consumption</li>
 * </ul>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Coordinating command building, process execution, and cleanup</li>
 *   <li>Enforcing timeout constraints via lifecycle manager</li>
 *   <li>Providing structured error context in {@link TranscriptionException}</li>
 *   <li>Idempotent {@link #close()} for cleanup</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe for concurrent transcription calls.
 * Each transcription creates an isolated process with dedicated stream handlers.
 *
 * <p>Temp-file WAV handling is performed by the caller (engine).
 *
 * @see WhisperCommandBuilder
 * @see ProcessLifecycleManager
 * @see ProcessStreamHandler
 * @since 1.0
 */
@Component
public final class WhisperProcessManager implements ProcessManager {

    private static final Logger LOG = LogManager.getLogger(WhisperProcessManager.class);

    private final WhisperCommandBuilder commandBuilder;
    private final ProcessLifecycleManager lifecycleManager;

    private final Set<ProcessLifecycleManager.ProcessExecution> activeExecutions =
            ConcurrentHashMap.newKeySet();

    /**
     * Context for creating detailed error messages.
     */
    private record ErrorContext(
            WhisperConfig cfg,
            int exitCode,
            String stderr,
            long startNano,
            Throwable cause
    ) {}

    /**
     * Spring-friendly constructor. Automatically wired with output mode from configuration.
     *
     * @param outputMode output format from configuration ("text" or "json")
     */
    @org.springframework.beans.factory.annotation.Autowired
    public WhisperProcessManager(
            @org.springframework.beans.factory.annotation.Value("${stt.whisper.output:text}")
            String outputMode) {
        this(new DefaultProcessFactory(), outputMode);
    }

    /**
     * Package-private constructor for testing with custom process factory.
     *
     * @param processFactory factory for creating processes
     */
    WhisperProcessManager(ProcessFactory processFactory) {
        this(processFactory, "text");
    }

    /**
     * Full constructor for testing with custom process factory and output mode.
     *
     * @param processFactory factory for creating processes
     * @param outputMode output format ("text" or "json")
     */
    WhisperProcessManager(ProcessFactory processFactory, String outputMode) {
        this.commandBuilder = new WhisperCommandBuilder(outputMode);
        this.lifecycleManager = new ProcessLifecycleManager(
                Objects.requireNonNull(processFactory, "processFactory"));
    }

    /**
     * Executes whisper.cpp for the given WAV file and returns its stdout as the transcription output.
     *
     * <p>CLI contract (text mode by default):
     *   <pre>
     *   ${binary} -m ${model} -f ${wav} -l ${language} -otxt -of stdout -t ${threads}
     *   </pre>
     *
     * @param wavPath path to WAV file (created by caller)
     * @param cfg whisper configuration
     * @return stdout content produced by whisper (may be empty)
     * @throws TranscriptionException on timeout, non-zero exit, or I/O error
     */
    public String transcribe(Path wavPath, WhisperConfig cfg) {
        Objects.requireNonNull(wavPath, "wavPath");
        Objects.requireNonNull(cfg, "cfg");

        List<String> command = commandBuilder.buildCommand(cfg, wavPath);
        long startTime = System.nanoTime();
        ProcessLifecycleManager.ProcessExecution exec = null;

        try {
            // Start process with stream gobblers
            exec = lifecycleManager.startProcess(
                    command,
                    wavPath.getParent(),
                    cfg.maxStdoutBytes(),
                    WhisperConstants.STDERR_MAX_BYTES);
            activeExecutions.add(exec);

            // Wait for completion with timeout
            boolean completed = lifecycleManager.waitForCompletion(exec, cfg.timeoutSeconds());
            if (!completed) {
                lifecycleManager.destroyProcess(exec.getProcess());
                ErrorContext ctx = new ErrorContext(cfg, -1, exec.getStderr(), startTime, null);
                throw whisperError("Timeout after " + cfg.timeoutSeconds() + "s", ctx);
            }

            // Validate exit code
            int exitCode = exec.getProcess().exitValue();
            if (exitCode != 0) {
                ErrorContext ctx = new ErrorContext(cfg, exitCode, exec.getStderr(), startTime, null);
                throw whisperError("Non-zero exit: " + exitCode, ctx);
            }

            // Return stdout output
            String output = exec.getStdout();
            LOG.debug("Whisper stdout size={} bytes", output.length());
            return output;

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (e instanceof TranscriptionException te) {
                throw te;
            }
            ErrorContext ctx = new ErrorContext(cfg, -1, null, startTime, e);
            throw whisperError("I/O failure: " + e.getMessage(), ctx);
        } finally {
            if (exec != null) {
                activeExecutions.remove(exec);
            }
            lifecycleManager.cleanup(exec);
        }
    }

    /**
     * Creates a TranscriptionException with detailed context.
     *
     * @param msg primary error message
     * @param ctx error context with configuration and diagnostic details
     * @return TranscriptionException with metadata
     */
    private TranscriptionException whisperError(String msg, ErrorContext ctx) {
        long durationMs = TimeUtils.nanosToMillis(System.nanoTime() - ctx.startNano());
        String stderrSnippet = ctx.stderr() == null ? ""
                : snippet(ctx.stderr());

        TranscriptionExceptionBuilder builder =
                TranscriptionExceptionBuilder.create(msg)
                        .engine("whisper")
                        .exitCode(ctx.exitCode())
                        .durationMs(durationMs)
                        .metadata("binaryPath", ctx.cfg().binaryPath())
                        .metadata("modelPath", ctx.cfg().modelPath())
                        .metadata("stderr", stderrSnippet);

        if (ctx.cause() != null) {
            builder.cause(ctx.cause());
        }

        return builder.build();
    }

    /**
     * Returns the first {@link WhisperConstants#ERROR_SNIPPET_MAX_CHARS} characters of a string.
     *
     * @param s string to truncate
     * @return truncated string
     */
    private static String snippet(String s) {
        int len = Math.min(WhisperConstants.ERROR_SNIPPET_MAX_CHARS, s.length());
        return s.substring(0, len);
    }

    /**
     * Idempotent cleanup of any running process and gobbler threads.
     */
    @Override
    public void close() {
        // Atomically drain to prevent zombie processes from concurrent transcribe() calls
        var snapshot = new java.util.ArrayList<>(activeExecutions);
        activeExecutions.removeAll(snapshot);
        for (ProcessLifecycleManager.ProcessExecution exec : snapshot) {
            lifecycleManager.cleanup(exec);
        }
    }
}
