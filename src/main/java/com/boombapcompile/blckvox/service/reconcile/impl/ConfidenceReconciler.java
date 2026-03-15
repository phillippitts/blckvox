package com.boombapcompile.blckvox.service.reconcile.impl;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.reconcile.AbstractReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;

/**
 * Reconciles two engine results by selecting the one with higher confidence.
 *
 * <p>Tie-breaking strategy: If confidences are equal, prefers non-empty text.
 * If both are empty or both non-empty, defaults to Vosk result.
 *
 * <p>This strategy is optimal when both engines are equally accurate but
 * their confidence scores reliably indicate transcription quality.
 */
public final class ConfidenceReconciler extends AbstractReconciler {

    /**
     * Reconciles two non-null engine results by selecting the one with higher confidence.
     *
     * <p>Null handling is performed by {@link AbstractReconciler}.
     *
     * @param vosk Vosk engine result (never null)
     * @param whisper Whisper engine result (never null)
     * @return reconciled transcription result with "reconciled" engine name
     */
    @Override
    protected TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper) {
        // Pick by confidence
        if (vosk.confidence() > whisper.confidence()) {
            return toResult(vosk);
        }
        if (whisper.confidence() > vosk.confidence()) {
            return toResult(whisper);
        }

        // Tie-breaker: prefer non-empty text
        boolean voskNonEmpty = isNonEmpty(vosk.text());
        boolean whisperNonEmpty = isNonEmpty(whisper.text());

        if (voskNonEmpty && !whisperNonEmpty) {
            return toResult(vosk);
        }
        if (whisperNonEmpty && !voskNonEmpty) {
            return toResult(whisper);
        }

        // Both empty or both non-empty: default to Vosk
        return toResult(vosk);
    }

    /**
     * Checks if text is non-empty (not blank).
     *
     * @param text text to check (never null — guaranteed by EngineResult)
     * @return true if text is non-empty
     */
    private boolean isNonEmpty(String text) {
        return !text.isBlank();
    }
}