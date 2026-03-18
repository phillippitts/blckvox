package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Guards STT engine transcription operations with a semaphore to prevent concurrent overload.
 *
 * <p>This utility encapsulates the common concurrency control pattern used by both Vosk and
 * Whisper engines. It provides bounded waiting with timeout and publishes failure events
 * when concurrency limits are reached.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The underlying {@link Semaphore}
 * handles concurrent acquire/release operations safely.
 *
 * <p><b>Usage Pattern:</b>
 * <pre>{@code
 * ConcurrencyGuard guard = new ConcurrencyGuard(
 *     new Semaphore(2),
 *     5000,
 *     "vosk",
 *     eventPublisher
 * );
 *
 * try {
 *     guard.acquire(); // Blocks until permit available or timeout
 *     // ... perform transcription ...
 * } finally {
 *     guard.release();
 * }
 * }</pre>
 *
 * @since 1.0
 */
public final class ConcurrencyGuard implements TranscriptionGuard {

    private final Semaphore semaphore;
    private final long timeoutMs;
    private final String engineName;
    private final ApplicationEventPublisher publisher;

    /**
     * Constructs a ConcurrencyGuard with specified semaphore and timeout.
     *
     * @param semaphore the semaphore controlling concurrent access
     * @param timeoutMs maximum time to wait for permit in milliseconds
     * @param engineName engine name for error messages and events
     * @param publisher event publisher for failure notifications (nullable)
     */
    public ConcurrencyGuard(Semaphore semaphore,
                           long timeoutMs,
                           String engineName,
                           ApplicationEventPublisher publisher) {
        this.semaphore = semaphore;
        this.timeoutMs = timeoutMs;
        this.engineName = engineName;
        this.publisher = publisher;
    }

    /**
     * Acquires a concurrency permit, blocking up to the configured timeout.
     *
     * <p>Implements bounded waiting to gracefully handle brief load spikes. Publishes
     * a failure event if permit cannot be acquired within timeout.
     *
     * @throws TranscriptionException if permit cannot be acquired within timeout
     *         or if thread is interrupted while waiting
     */
    public void acquire() {
        try {
            boolean acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                Map<String, String> context = new HashMap<>();
                context.put("reason", "concurrency-limit");
                context.put("timeoutMs", String.valueOf(timeoutMs));
                EngineEventPublisher.publishFailure(
                    publisher,
                    engineName,
                    "concurrency limit reached after " + timeoutMs + "ms wait",
                    null,
                    context
                );
                throw new TranscriptionException(
                    engineName + " concurrency limit reached after " + timeoutMs + "ms wait",
                    engineName
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException(
                engineName + " transcription interrupted while waiting for semaphore",
                engineName,
                e
            );
        }
    }

    /**
     * Releases a previously acquired concurrency permit.
     *
     * <p>This method should be called in a finally block to ensure permits are
     * released even if transcription fails.
     */
    public void release() {
        semaphore.release();
    }

}
