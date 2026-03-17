package com.boombapcompile.blckvox.service.stt.parallel;

import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.EngineResult;

/**
 * Runs Vosk and Whisper in parallel and returns both results for reconciliation.
 * Implementations must be hermetic-test friendly.
 */
public interface ParallelSttService {

    /**
     * Holds both engine results (either may be null if a failure occurred).
     */
    record EnginePair(EngineResult vosk, EngineResult whisper) {}

    /**
     * Transcribes with both engines, respecting parallel timeout.
     * @throws TranscriptionException when both engines fail or timeout occurs without any result
     */
    EnginePair transcribeBoth(byte[] pcm, long timeoutMs);

    /**
     * Transcribes with Vosk only and returns the result.
     * @throws TranscriptionException when Vosk fails
     */
    default EngineResult transcribeVoskOnly(byte[] pcm, long timeoutMs) {
        throw new UnsupportedOperationException("transcribeVoskOnly not implemented");
    }

    /**
     * Runs Whisper only and pairs it with a pre-computed Vosk result.
     * @throws TranscriptionException when Whisper fails
     */
    default EnginePair transcribeWhisperOnly(byte[] pcm, long timeoutMs, EngineResult precomputedVosk) {
        throw new UnsupportedOperationException("transcribeWhisperOnly not implemented");
    }
}
