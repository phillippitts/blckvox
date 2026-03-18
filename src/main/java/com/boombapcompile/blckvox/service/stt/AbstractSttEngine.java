package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.service.stt.vosk.VoskSttEngine;
import com.boombapcompile.blckvox.service.stt.whisper.WhisperSttEngine;
import com.boombapcompile.blckvox.config.properties.SttConcurrencyProperties;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.util.ConcurrencyGuard;
import com.boombapcompile.blckvox.service.stt.util.ConcurrencyScaler;
import com.boombapcompile.blckvox.service.stt.util.DynamicConcurrencyGuard;
import com.boombapcompile.blckvox.service.stt.util.EngineEventPublisher;
import com.boombapcompile.blckvox.service.stt.util.TranscriptionGuard;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract base class for STT engine implementations providing common lifecycle and state management.
 *
 * <p>This class implements the Template Method pattern, providing a thread-safe framework for
 * engine initialization, health checking, and resource cleanup while allowing subclasses to
 * customize engine-specific behavior.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All state-modifying operations are
 * guarded by a {@link ReentrantLock}. Subclasses must ensure their implementations of
 * {@link #doInitialize()} and {@link #doClose()} are thread-safe when called within
 * the lock context.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li><b>Uninitialized:</b> Engine created but not yet initialized</li>
 *   <li><b>Initialized:</b> {@link #initialize()} called successfully</li>
 *   <li><b>Closed:</b> {@link #close()} called, engine no longer usable</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> Both {@link #initialize()} and {@link #close()} are idempotent -
 * multiple calls are safe and will not cause errors or duplicate operations.
 *
 * <p><b>Subclass Responsibilities:</b>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #doInitialize()} - Engine-specific initialization logic</li>
 *   <li>{@link #doClose()} - Engine-specific cleanup logic</li>
 *   <li>{@link #transcribe(byte[])} - Core transcription functionality</li>
 *   <li>{@link #getEngineName()} - Engine identifier</li>
 * </ul>
 *
 * @since 1.1
 * @see SttEngine
 * @see VoskSttEngine
 * @see WhisperSttEngine
 */
public abstract class AbstractSttEngine implements SttEngine {

    /**
     * Lock for synchronizing state transitions and ensuring thread-safety.
     * All access to {@link #initialized} and {@link #closed} must be guarded by this lock.
     */
    protected final ReentrantLock lock = new ReentrantLock();

    /**
     * Read-write lock protecting JNI resources during transcription.
     * Read lock: held during transcribe() — allows concurrent transcriptions.
     * Write lock: acquired by close() — blocks until all active transcriptions finish.
     */
    private final ReentrantReadWriteLock transcriptionLock = new ReentrantReadWriteLock();

    /**
     * Tracks whether the engine has been successfully initialized.
     * Access must be guarded by {@link #lock}.
     */
    protected boolean initialized = false;

    /**
     * Tracks whether the engine has been closed and is no longer usable.
     * Access must be guarded by {@link #lock}.
     */
    protected boolean closed = false;

    /**
     * Concurrency guard protecting transcription operations.
     * Initialized by subclass constructors via {@link #initializeGuard} or {@link #initializeDefaultGuard}.
     */
    private TranscriptionGuard guard;

    /**
     * Initializes the concurrency guard with dynamic scaling support.
     *
     * <p>Selects between {@link DynamicConcurrencyGuard} (if dynamic scaling is enabled and
     * a scaler is available) and {@link ConcurrencyGuard} (static semaphore).
     *
     * @param engineName engine name for error messages and events
     * @param max maximum concurrent permits
     * @param timeoutMs acquire timeout in milliseconds
     * @param publisher event publisher for failure notifications
     * @param concurrencyProps concurrency configuration
     * @param scaler optional dynamic concurrency scaler (may be null)
     */
    protected final void initializeGuard(String engineName,
                                          int max,
                                          long timeoutMs,
                                          ApplicationEventPublisher publisher,
                                          SttConcurrencyProperties concurrencyProps,
                                          ConcurrencyScaler scaler) {
        if (concurrencyProps.isDynamicScalingEnabled() && scaler != null) {
            DynamicConcurrencyGuard dynamicGuard =
                    new DynamicConcurrencyGuard(max, timeoutMs, engineName, publisher);
            scaler.registerGuard(engineName, dynamicGuard);
            this.guard = dynamicGuard;
        } else {
            this.guard = new ConcurrencyGuard(
                    new Semaphore(max), timeoutMs, engineName, publisher);
        }
    }

    /**
     * Initializes the concurrency guard with default static settings.
     *
     * <p>Used by basic/test constructors that don't need dynamic scaling.
     *
     * @param engineName engine name for error messages
     * @param max maximum concurrent permits
     * @param timeoutMs acquire timeout in milliseconds
     */
    protected final void initializeDefaultGuard(String engineName, int max, long timeoutMs) {
        this.guard = new ConcurrencyGuard(
                new Semaphore(max), timeoutMs, engineName, null);
    }

    /**
     * Acquires the concurrency guard permit.
     *
     * @throws TranscriptionException if permit cannot be acquired
     */
    protected final void acquireGuard() {
        guard.acquire();
    }

    /**
     * Releases the concurrency guard permit.
     */
    protected final void releaseGuard() {
        guard.release();
    }

    /**
     * Initializes the STT engine using the Template Method pattern.
     *
     * <p>This method is idempotent - multiple calls will not re-initialize the engine.
     * Once closed, the engine cannot be reinitialized unless the subclass explicitly
     * supports reinitialization by resetting the {@code closed} flag in {@link #doInitialize()}.
     *
     * <p><b>Thread-safe:</b> Guarded by {@link ReentrantLock} to prevent concurrent initialization.
     *
     * <p><b>Template Method:</b> Calls {@link #doInitialize()} for engine-specific logic.
     *
     * @throws TranscriptionException if initialization fails
     */
    @Override
    public final void initialize() {
        lock.lock();
        try {
            if (initialized && !closed) {
                return; // Already initialized and not closed
            }
            doInitialize();
            initialized = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Engine-specific initialization logic.
     *
     * <p>Called by {@link #initialize()} within a locked context. Subclasses should
     * perform all engine-specific initialization here (e.g., loading models, validating
     * configuration, initializing native libraries).
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Must be idempotent if {@link #initialize()} can be called multiple times</li>
     *   <li>Must throw {@link TranscriptionException} on failure</li>
     *   <li>Should log appropriate messages for initialization progress/completion</li>
     *   <li>If supporting reinitialization after close, must reset {@code closed = false}</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Called within locked context - implementations do not
     * need additional synchronization for state fields.
     *
     * @throws TranscriptionException if initialization fails
     */
    protected abstract void doInitialize();

    /**
     * Checks if the engine is healthy and ready to transcribe.
     *
     * <p>Returns {@code true} if the engine has been initialized, is not closed, and is
     * ready to process transcription requests.
     *
     * <p><b>Thread-safe:</b> Guarded read of engine state.
     *
     * @return true if engine is initialized and not closed, false otherwise
     */
    @Override
    public final boolean isHealthy() {
        lock.lock();
        try {
            return initialized && !closed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the STT engine and releases all resources using the Template Method pattern.
     *
     * <p>This method is idempotent - multiple calls are safe and will only close resources once.
     *
     * <p><b>Thread-safe:</b> Guarded by {@link ReentrantLock} to prevent concurrent closure.
     *
     * <p><b>Template Method:</b> Calls {@link #doClose()} for engine-specific cleanup.
     *
     * <p><b>Lifecycle:</b> Automatically invoked by Spring container on shutdown via
     * {@link PreDestroy} annotation.
     */
    @Override
    @PreDestroy
    public final void close() {
        // Acquire write lock to wait for all active transcriptions to finish
        transcriptionLock.writeLock().lock();
        try {
            lock.lock();
            try {
                if (closed) {
                    return; // Already closed
                }
                try {
                    doClose();
                } finally {
                    // Always mark as closed even if doClose() throws, to prevent
                    // repeated close attempts and to ensure isHealthy() returns false
                    closed = true;
                    initialized = false;
                }
            } finally {
                lock.unlock();
            }
        } finally {
            transcriptionLock.writeLock().unlock();
        }
    }

    /**
     * Engine-specific cleanup logic.
     *
     * <p>Called by {@link #close()} within a locked context. Subclasses should
     * release all engine-specific resources here (e.g., close native libraries,
     * free models, terminate processes).
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Must be idempotent - safe to call multiple times</li>
     *   <li>Should never throw exceptions - log errors instead</li>
     *   <li>Should gracefully handle partially-initialized state</li>
     *   <li>Should log appropriate messages for cleanup progress/completion</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Called within locked context - implementations do not
     * need additional synchronization for state fields.
     */
    protected abstract void doClose();

    /**
     * Acquires the transcription read lock. Must be paired with {@link #releaseTranscriptionLock()}.
     * Prevents close() from destroying resources while transcription is in progress.
     *
     * @throws TranscriptionException if engine is closing or closed
     */
    protected final void acquireTranscriptionLock() {
        transcriptionLock.readLock().lock();
        lock.lock();
        try {
            if (closed) {
                transcriptionLock.readLock().unlock();
                throw new TranscriptionException(
                    getEngineName() + " engine is closing or closed",
                    getEngineName()
                );
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases the transcription read lock. Must be called in a finally block
     * after {@link #acquireTranscriptionLock()}.
     */
    protected final void releaseTranscriptionLock() {
        transcriptionLock.readLock().unlock();
    }

    /**
     * Validates that the engine is initialized and ready for transcription.
     *
     * <p>Utility method for subclasses to call at the start of {@link #transcribe(byte[])}
     * to ensure the engine is in a valid state.
     *
     * <p><b>Thread-safe:</b> Guarded read of engine state.
     *
     * @throws TranscriptionException if engine is not initialized or is closed
     */
    protected final void ensureInitialized() {
        lock.lock();
        try {
            if (!initialized || closed) {
                throw new TranscriptionException(
                    getEngineName() + " engine not initialized or closed",
                    getEngineName()
                );
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles transcription errors with consistent event publishing and exception wrapping.
     *
     * <p>This method provides common error handling for transcription failures:
     * <ul>
     *   <li>Publishes failure event with context for monitoring</li>
     *   <li>Preserves TranscriptionException instances without double-wrapping</li>
     *   <li>Wraps other exceptions with engine context</li>
     * </ul>
     *
     * @param exception the exception that occurred during transcription
     * @param publisher Spring event publisher for failure events (may be null)
     * @param context additional context to include in failure event (may be null)
     * @return TranscriptionException (always throws, never returns)
     * @throws TranscriptionException always thrown with appropriate context
     */
    protected final TranscriptionException handleTranscriptionError(
            Exception exception,
            ApplicationEventPublisher publisher,
            Map<String, String> context) {

        // Publish failure event if publisher available
        if (publisher != null) {
            EngineEventPublisher.publishFailure(
                publisher,
                getEngineName(),
                "transcription failure",
                exception,
                context
            );
        }

        // Preserve TranscriptionException without double-wrapping
        if (exception instanceof TranscriptionException te) {
            return te;
        }

        // Wrap other exceptions with engine context
        return new TranscriptionException(
            getEngineName() + " transcription failed: " + exception.getMessage(),
            getEngineName(),
            exception
        );
    }
}