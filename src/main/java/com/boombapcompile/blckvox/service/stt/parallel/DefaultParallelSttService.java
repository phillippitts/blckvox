package com.boombapcompile.blckvox.service.stt.parallel;

import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.DetailedTranscriptionEngine;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.TokenizerUtil;
import com.boombapcompile.blckvox.service.stt.TranscriptionOutput;
import com.boombapcompile.blckvox.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of parallel dual-engine transcription service.
 *
 * <p>This service runs Vosk and Whisper STT engines concurrently using a dedicated executor,
 * enabling Phase 4 reconciliation strategies. Key features:
 * <ul>
 *   <li><b>Parallel Execution:</b> Both engines run simultaneously on separate threads</li>
 *   <li><b>Timeout Protection:</b> Configurable timeout prevents indefinite blocking</li>
 *   <li><b>Graceful Degradation:</b> Returns partial results if one engine fails</li>
 *   <li><b>Token Extraction:</b> Extracts word-level tokens for overlap-based reconciliation</li>
 *   <li><b>JSON Support:</b> Whisper JSON output provides enhanced token accuracy</li>
 * </ul>
 *
 * <p><b>Thread Model:</b> Uses {@code sttExecutor} (bounded thread pool) to prevent resource
 * exhaustion. Each {@link #transcribeBoth(byte[], long)} call spawns two tasks that complete
 * independently. The method blocks until both finish or timeout occurs.
 *
 * <p><b>Error Handling:</b> Individual engine failures are caught and logged. If both engines
 * fail or timeout, a {@link TranscriptionException} is thrown. If only one engine fails, the
 * successful result is still returned for reconciliation (may trigger fallback in reconciler).
 *
 * <p><b>Performance:</b> Parallel execution typically reduces total latency by 40-60% compared
 * to sequential execution. Actual speedup depends on CPU cores and engine implementations.
 *
 * @see ParallelSttService
 * @see TranscriptReconciler
 * @since 1.0 (Phase 4)
 */
@Service
public class DefaultParallelSttService implements ParallelSttService {
    private static final Logger LOG = LogManager.getLogger(DefaultParallelSttService.class);

    private final SttEngine vosk;
    private final SttEngine whisper;
    private final Executor executor;

    // Re-use orchestration timeout for now (could introduce dedicated property later)
    private final long defaultTimeoutMs;

    /**
     * Constructs a DefaultParallelSttService with dependency injection.
     *
     * <p>This constructor is wired by Spring using {@code @Qualifier} annotations to
     * ensure the correct engine and executor beans are injected.
     *
     * @param vosk Vosk STT engine (qualified as "voskSttEngine")
     * @param whisper Whisper STT engine (qualified as "whisperSttEngine")
     * @param executor bounded thread pool for parallel execution (qualified as "sttExecutor")
     * @param timeoutMs default timeout in milliseconds from {@code stt.parallel.timeout-ms} property
     *                  (defaults to 10000ms if not configured or invalid)
     * @throws NullPointerException if vosk, whisper, or executor is null
     */
    public DefaultParallelSttService(@Qualifier("voskSttEngine") SttEngine vosk,
                                     @Qualifier("whisperSttEngine") SttEngine whisper,
                                     @Qualifier("sttExecutor") Executor executor,
                                     @Value("${stt.parallel.timeout-ms:10000}")
                                     long timeoutMs) {
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.executor = Objects.requireNonNull(executor);
        this.defaultTimeoutMs = timeoutMs <= 0 ? 10_000 : timeoutMs;
    }

    /**
     * Transcribes audio using both Vosk and Whisper engines in parallel.
     *
     * <p>This method spawns two asynchronous tasks (one per engine) and waits for both to
     * complete or for the timeout to expire. Results are packaged into an {@link EnginePair}
     * for downstream reconciliation.
     *
     * <p><b>Timeout Behavior:</b>
     * <ul>
     *   <li>If {@code timeoutMs > 0}, uses the specified timeout</li>
     *   <li>If {@code timeoutMs <= 0}, uses the default timeout from configuration</li>
     *   <li>If timeout expires, cancels remaining tasks and returns partial results (if any)</li>
     * </ul>
     *
     * <p><b>Partial Results:</b> If one engine succeeds and the other fails or times out,
     * the pair will contain one non-null result. Reconcilers must handle this gracefully
     * (typically by using the available result or throwing an exception).
     *
     * <p><b>Token Extraction:</b> This method attempts to extract engine-provided tokens
     * from engines implementing {@link DetailedTranscriptionEngine}.
     * For Whisper, this provides JSON-derived tokens with accurate word boundaries for
     * overlap-based reconciliation.
     *
     * @param pcm PCM audio data (16-bit, 16kHz, mono)
     * @param timeoutMs timeout in milliseconds (use 0 for default)
     * @return pair of engine results (either may be null if that engine failed)
     * @throws NullPointerException if pcm is null
     * @throws TranscriptionException if both engines fail or timeout
     * @see EnginePair
     * @see TranscriptReconciler
     */
    @Override
    public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
        Objects.requireNonNull(pcm, "pcm");
        final long toMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        List<CompletableFuture<EngineResult>> futures = new ArrayList<>(2);
        futures.add(CompletableFuture.supplyAsync(() -> runEngine(vosk, pcm), executor));
        futures.add(CompletableFuture.supplyAsync(() -> runEngine(whisper, pcm), executor));

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(toMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            LOG.warn("Parallel transcription timed out after {} ms - tasks will continue in background", toMs);
            // Note: CompletableFuture.cancel(true) does NOT interrupt underlying threads.
            // The engine tasks will continue running to completion to avoid leaking resources
            // (semaphore permits, whisper subprocesses). We simply don't wait for their results.
            futures.forEach(f -> f.cancel(false));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ee) {
            // Already handled per-engine; continue to collect results
        }

        EngineResult rVosk = getResultSilently(futures.get(0));
        EngineResult rWhisper = getResultSilently(futures.get(1));

        if (rVosk == null && rWhisper == null) {
            throw new TranscriptionException("Both engines failed or timed out");
        }
        return new EnginePair(rVosk, rWhisper);
    }

    @Override
    public EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
        Objects.requireNonNull(pcm, "pcm");
        final long toMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        CompletableFuture<EngineResult> future =
                CompletableFuture.supplyAsync(() -> runEngine(vosk, pcm), executor);
        try {
            future.get(toMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            LOG.warn("Vosk-only transcription timed out after {} ms", toMs);
            future.cancel(false);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Handled inside runEngine
        }

        EngineResult result = getResultSilently(future);
        if (result == null) {
            throw new TranscriptionException("Vosk engine failed or timed out");
        }
        return result;
    }

    @Override
    public EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs,
                                             EngineResult precomputedVosk) {
        Objects.requireNonNull(pcm, "pcm");
        Objects.requireNonNull(precomputedVosk, "precomputedVosk");
        final long toMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        CompletableFuture<EngineResult> future =
                CompletableFuture.supplyAsync(() -> runEngine(whisper, pcm), executor);
        try {
            future.get(toMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            LOG.warn("Whisper-only transcription timed out after {} ms", toMs);
            future.cancel(false);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Handled inside runEngine
        }

        EngineResult rWhisper = getResultSilently(future);
        if (rWhisper == null) {
            throw new TranscriptionException("Whisper engine failed or timed out");
        }
        return new EnginePair(precomputedVosk, rWhisper);
    }

    private EngineResult getResultSilently(CompletableFuture<EngineResult> f) {
        try {
            return f.isDone() && !f.isCompletedExceptionally() && !f.isCancelled() ? f.get() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private EngineResult runEngine(SttEngine engine, byte[] pcm) {
        long startTime = System.nanoTime();
        try {
            List<String> tokens;
            String rawJson = null;
            TranscriptionResult tr;

            if (engine instanceof DetailedTranscriptionEngine detailedEngine) {
                // Use transcribeDetailed() to get tokens/JSON directly
                TranscriptionOutput output = detailedEngine.transcribeDetailed(pcm);
                tr = output.result();
                tokens = output.tokens().isEmpty()
                        ? TokenizerUtil.tokenize(tr.text())
                        : output.tokens();
                rawJson = output.rawJson();
            } else {
                tr = engine.transcribe(pcm);
                tokens = TokenizerUtil.tokenize(tr.text());
            }

            long elapsedMs = TimeUtils.elapsedMillis(startTime);
            LOG.info("[engine-result] engine={}, confidence={}, chars={}",
                    engine.getEngineName(), String.format("%.2f", tr.confidence()),
                    tr.text().length());
            LOG.debug("[engine-result] text='{}'", tr.text());
            return new EngineResult(tr.text(), tr.confidence(), tokens, elapsedMs, engine.getEngineName(), rawJson);
        } catch (TranscriptionException te) {
            LOG.warn("{} failed: {}", engine.getEngineName(), te.getMessage());
            return null;
        } catch (RuntimeException re) {
            LOG.error("{} unexpected error", engine.getEngineName(), re);
            return null;
        }
    }
}
