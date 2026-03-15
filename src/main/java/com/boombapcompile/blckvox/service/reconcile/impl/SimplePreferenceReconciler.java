package com.boombapcompile.blckvox.service.reconcile.impl;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.reconcile.AbstractReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;

import java.util.Objects;

/**
 * Reconciles two engine results by preferring the configured primary engine.
 *
 * <p>This simple strategy always prefers the primary engine's result unless
 * its text is empty, in which case it falls back to the secondary engine.
 *
 * <p>This strategy is useful when you have a clear preference for one engine
 * based on accuracy, speed, or other factors, but want fallback protection
 * when the primary engine fails or produces no output.
 */
public final class SimplePreferenceReconciler extends AbstractReconciler {
    private final OrchestrationProperties.PrimaryEngine primary;

    /**
     * Creates a simple preference reconciler.
     *
     * @param primary the preferred engine (VOSK or WHISPER)
     * @throws NullPointerException if primary is null
     */
    public SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine primary) {
        this.primary = Objects.requireNonNull(primary);
    }

    /**
     * Reconciles two non-null engine results by preferring the primary engine.
     *
     * <p>Null handling is performed by {@link AbstractReconciler}.
     *
     * @param vosk Vosk engine result (never null)
     * @param whisper Whisper engine result (never null)
     * @return reconciled transcription result with "reconciled" engine name
     */
    @Override
    protected TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper) {
        EngineResult first = primary == OrchestrationProperties.PrimaryEngine.VOSK ? vosk : whisper;
        EngineResult second = primary == OrchestrationProperties.PrimaryEngine.VOSK ? whisper : vosk;
        EngineResult pick = pickNonEmpty(first, second);
        return toResult(pick);
    }

    /**
     * Picks the first non-empty result, or falls back to second, or null.
     *
     * @param first first choice result
     * @param second second choice result (fallback)
     * @return first if non-empty, otherwise second, otherwise null
     */
    private EngineResult pickNonEmpty(EngineResult first, EngineResult second) {
        if (first != null && !first.text().isBlank()) {
            return first;
        }
        if (second != null && !second.text().isBlank()) {
            return second;
        }
        return first != null ? first : second; // both empty or one null -> default to first if not null
    }
}