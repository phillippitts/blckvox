package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.service.audio.AudioFormat;
import com.boombapcompile.blckvox.service.reconcile.impl.WordOverlapReconciler;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.service.stt.AbstractSttEngine;
import com.boombapcompile.blckvox.service.stt.DetailedTranscriptionEngine;
import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.config.properties.SttConcurrencyProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.audio.WavWriter;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;
import com.boombapcompile.blckvox.service.stt.TranscriptionOutput;
import com.boombapcompile.blckvox.service.stt.util.ConcurrencyScaler;
import com.boombapcompile.blckvox.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Map;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Whisper-based implementation of {@link SttEngine} using the external whisper.cpp binary.
 *
 * <p>This engine provides high-accuracy offline speech-to-text transcription by invoking
 * the whisper.cpp command-line binary as a subprocess. It supports both text and JSON output
 * modes, with JSON mode enabling advanced features like word-level tokens for reconciliation.
 *
 * <p><b>Architecture:</b>
 * <ul>
 *   <li>Converts PCM audio to temporary WAV file using {@link WavWriter}</li>
 *   <li>Invokes whisper.cpp via {@link ProcessManager} with configured timeout</li>
 *   <li>Parses output (plain text or JSON) and cleans up temporary files</li>
 *   <li>Supports concurrency limiting via semaphore to prevent CPU saturation</li>
 * </ul>
 *
 * <p><b>Output Modes:</b>
 * <ul>
 *   <li><b>Text Mode (default):</b> Simple text output from whisper.cpp (stdout)</li>
 *   <li><b>JSON Mode:</b> Structured JSON output with word-level tokens and timestamps.
 *       Enabled via {@code stt.whisper.output=json}. Provides enhanced accuracy for
 *       {@link WordOverlapReconciler}.</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This engine is thread-safe. Concurrent transcriptions are
 * protected by a semaphore (configurable max). Each transcription runs in isolation with
 * its own temporary WAV file and subprocess.
 *
 * <p><b>Privacy:</b> Never logs full transcription text at INFO level. Only duration
 * and character count are logged to protect user privacy.
 *
 * <p><b>Audio Contract:</b> Expects raw PCM audio in the format defined by
 * {@link AudioFormat} (16kHz, 16-bit, mono, little-endian).
 * Callers must validate/convert inputs before invoking {@link #transcribe(byte[])}.
 *
 * @see WhisperProcessManager
 * @see WhisperConfig
 * @see WhisperJsonParser
 * @since 1.0
 */
@Component
public final class WhisperSttEngine extends AbstractSttEngine
        implements DetailedTranscriptionEngine {

    private static final Logger LOG = LogManager.getLogger(WhisperSttEngine.class);

    private final WhisperConfig cfg;
    private final ProcessManager manager;
    private ApplicationEventPublisher publisher;
    private final boolean jsonMode;
    private final int silenceGapMs;

    public WhisperSttEngine(WhisperConfig cfg, ProcessManager manager) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        initializeDefaultGuard(SttEngineNames.WHISPER, 2, 1000);
        this.jsonMode = false;
        this.silenceGapMs = 0; // Disabled in basic constructor
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WhisperSttEngine(WhisperConfig cfg,
                             SttConcurrencyProperties concurrencyProperties,
                             ProcessManager manager,
                             ApplicationEventPublisher publisher,
                             @org.springframework.beans.factory.annotation.Value("${stt.whisper.output:text}")
                             String outputMode,
                             OrchestrationProperties orchestrationProperties,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             ConcurrencyScaler concurrencyScaler) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        int max = Math.max(1, concurrencyProperties.getWhisperMax());
        long timeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        initializeGuard(SttEngineNames.WHISPER, max, timeoutMs, publisher, concurrencyProperties, concurrencyScaler);
        this.publisher = publisher;
        this.jsonMode = "json".equalsIgnoreCase(outputMode);
        this.silenceGapMs = orchestrationProperties != null ? orchestrationProperties.getSilenceGapMs() : 0;
    }

    @Override
    protected void doInitialize() {
        cleanupStaleTempFiles();
        // Allow reinitialization after close() for watchdog restart support
        closed = false;
        // Fail-fast validation already ran at startup
        LOG.info("Whisper engine initialized: bin={}, model={}, timeout={}s, lang={}, threads={}",
                cfg.binaryPath(), cfg.modelPath(), cfg.timeoutSeconds(), cfg.language(), cfg.threads());
    }

    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        return transcribeDetailed(audioData).result();
    }

    @Override
    public TranscriptionOutput transcribeDetailed(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        acquireTranscriptionLock();
        try {
            acquireGuard();
            try {
                ensureInitialized();
                Path wav = null;
                long startTime = System.nanoTime();
                try {
                    wav = createTempWavFile(audioData);
                    String stdout = manager.transcribe(wav, cfg);
                    LOG.debug("WhisperSttEngine: Whisper returned {} output (length={} chars)",
                             jsonMode ? "JSON" : "text", stdout.length());

                    String text;
                    java.util.List<String> tokens = java.util.List.of();
                    String rawJson = null;

                    if (jsonMode) {
                        text = WhisperJsonParser.extractTextWithPauseDetection(stdout, silenceGapMs);
                        rawJson = stdout;
                        tokens = WhisperJsonParser.extractTokens(stdout);
                    } else {
                        text = stdout.trim();
                    }

                    double confidence = jsonMode
                            ? WhisperJsonParser.extractConfidence(stdout)
                            : cfg.textModeConfidence();
                    long elapsedMs = TimeUtils.elapsedMillis(startTime);
                    LOG.debug("Whisper transcribed clip in {} ms (chars={})", elapsedMs, text.length());

                    TranscriptionResult result = TranscriptionResult.of(text, confidence, SttEngineNames.WHISPER);
                    return TranscriptionOutput.of(result, tokens, rawJson);
                } catch (Exception e) {
                    throw handleTranscriptionError(e, publisher,
                            Map.of("binaryPath", cfg.binaryPath(), "modelPath", cfg.modelPath()));
                } finally {
                    cleanupTempFile(wav);
                }
            } finally {
                releaseGuard();
            }
        } finally {
            releaseTranscriptionLock();
        }
    }

    /**
     * Creates a temporary WAV file from PCM audio data.
     *
     * @param audioData PCM16LE mono 16kHz audio data
     * @return path to temporary WAV file
     * @throws Exception if file creation or write fails
     */
    private Path createTempWavFile(byte[] audioData) throws Exception {
        Path wav = Files.createTempFile("whisper-", ".wav");
        WavWriter.writePcm16LeMono16kHz(audioData, wav);
        return wav;
    }

    /**
     * Scans the temp directory for stale {@code whisper-*.wav} files older than 1 hour
     * and deletes them. This handles cleanup after abnormal shutdowns where the normal
     * finally-block cleanup didn't run.
     */
    private void cleanupStaleTempFiles() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "whisper-*.wav")) {
            for (Path file : stream) {
                try {
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    if (modified.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        LOG.debug("Cleaned up stale temp file: {}", file);
                    }
                } catch (IOException e) {
                    LOG.debug("Failed to clean up stale temp file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.debug("Failed to scan temp directory for stale whisper files: {}", e.getMessage());
        }
    }

    /**
     * Cleans up temporary WAV file, ignoring any errors.
     *
     * @param wav path to temporary WAV file (may be null)
     */
    private void cleanupTempFile(Path wav) {
        if (wav != null) {
            try {
                Files.deleteIfExists(wav);
            } catch (Exception ignore) {
                // Cleanup failure is non-critical
            }
        }
    }

    @Override
    public String getEngineName() {
        return SttEngineNames.WHISPER;
    }

    /**
     * Closes the Whisper engine and releases process resources.
     *
     * <p>Called by {@link #close()} within synchronized context.
     */
    @Override
    protected void doClose() {
        try {
            manager.close();
        } catch (Exception e) {
            LOG.debug("manager.close()", e);
        }
        LOG.info("Whisper engine closed");
    }
}
