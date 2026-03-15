package com.boombapcompile.blckvox.service.reconcile;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;

/**
 * Abstract base class for transcript reconcilers implementing common null-handling logic.
 *
 * <p>This class implements the Template Method pattern, where the {@link #reconcile(EngineResult, EngineResult)}
 * method handles null cases and delegates to {@link #doReconcile(EngineResult, EngineResult)} for
 * subclass-specific reconciliation logic.
 *
 * <p><b>Null Handling Strategy:</b>
 * <ul>
 *   <li>If both results are null → return empty result with 0.0 confidence</li>
 *   <li>If only vosk is null → return whisper's result</li>
 *   <li>If only whisper is null → return vosk's result</li>
 *   <li>If neither is null → delegate to {@link #doReconcile(EngineResult, EngineResult)}</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * public class MyReconciler extends AbstractReconciler {
 *     @Override
 *     protected TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper) {
 *         // Both vosk and whisper are guaranteed to be non-null here
 *         String text = vosk.confidence() > whisper.confidence() ? vosk.text() : whisper.text();
 *         return toResult(vosk.confidence() > whisper.confidence() ? vosk : whisper);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0
 */
public abstract class AbstractReconciler implements TranscriptReconciler {

    /**
     * Reconciles two engine results with null-safe handling.
     *
     * <p>This template method handles null cases and delegates to {@link #doReconcile(EngineResult, EngineResult)}
     * when both results are non-null.
     *
     * @param vosk Vosk engine result (may be null if engine failed)
     * @param whisper Whisper engine result (may be null if engine failed)
     * @return reconciled transcription result with {@link SttEngineNames#RECONCILED} engine name
     */
    @Override
    public final TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        // Handle null cases early
        if (vosk == null && whisper == null) {
            return emptyResult();
        }
        if (vosk == null) {
            return toResult(whisper);
        }
        if (whisper == null) {
            return toResult(vosk);
        }

        // Both non-null: delegate to subclass implementation
        return doReconcile(vosk, whisper);
    }

    /**
     * Reconciles two non-null engine results using subclass-specific logic.
     *
     * <p>This method is guaranteed to receive non-null parameters. Subclasses implement
     * their specific reconciliation strategy here without worrying about null handling.
     *
     * @param vosk Vosk engine result (never null)
     * @param whisper Whisper engine result (never null)
     * @return reconciled transcription result
     */
    protected abstract TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper);

    /**
     * Converts an engine result to a transcription result with "reconciled" engine name.
     *
     * <p>Protected helper method for subclasses to use when converting results.
     *
     * @param result engine result (may be null)
     * @return transcription result with {@link SttEngineNames#RECONCILED} engine name
     */
    protected final TranscriptionResult toResult(EngineResult result) {
        return TranscriptionResult.of(
                result.text(),
                result.confidence(),
                SttEngineNames.RECONCILED
        );
    }

    /**
     * Returns an empty transcription result.
     *
     * <p>Used when both engine results are null.
     *
     * @return empty transcription result with 0.0 confidence
     */
    protected final TranscriptionResult emptyResult() {
        return TranscriptionResult.of("", 0.0, SttEngineNames.RECONCILED);
    }
}
