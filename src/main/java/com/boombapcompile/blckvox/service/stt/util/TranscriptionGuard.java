package com.boombapcompile.blckvox.service.stt.util;

/**
 * Abstraction for concurrency guards protecting STT engine transcription operations.
 *
 * <p>Implementations control concurrent access to engine resources, either with
 * a fixed permit count ({@link ConcurrencyGuard}) or dynamically adjustable
 * permits ({@link DynamicConcurrencyGuard}).
 *
 * @since 1.0
 */
public interface TranscriptionGuard {

    /**
     * Acquires a concurrency permit, blocking up to the configured timeout.
     *
     * @throws com.boombapcompile.blckvox.exception.TranscriptionException
     *         if permit cannot be acquired within timeout or thread is interrupted
     */
    void acquire();

    /**
     * Releases a previously acquired concurrency permit.
     */
    void release();
}
