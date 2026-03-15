package com.boombapcompile.blckvox.service.reconcile;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractReconcilerTest {

    /**
     * Minimal concrete subclass for testing the template method.
     */
    private static final class TestReconciler extends AbstractReconciler {
        EngineResult lastVosk;
        EngineResult lastWhisper;

        @Override
        protected TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper) {
            this.lastVosk = vosk;
            this.lastWhisper = whisper;
            // Simple: pick higher confidence
            return vosk.confidence() >= whisper.confidence() ? toResult(vosk) : toResult(whisper);
        }
    }

    private static EngineResult engineResult(String text, double confidence, String name) {
        return new EngineResult(text, confidence, List.of(), 100, name, null);
    }

    @Test
    void bothNullReturnsEmptyResult() {
        TestReconciler reconciler = new TestReconciler();
        TranscriptionResult result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.engineName()).isEqualTo(SttEngineNames.RECONCILED);
    }

    @Test
    void voskNullReturnsWhisperResult() {
        TestReconciler reconciler = new TestReconciler();
        EngineResult whisper = engineResult("whisper text", 0.9, "whisper");

        TranscriptionResult result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.engineName()).isEqualTo(SttEngineNames.RECONCILED);
    }

    @Test
    void whisperNullReturnsVoskResult() {
        TestReconciler reconciler = new TestReconciler();
        EngineResult vosk = engineResult("vosk text", 0.85, "vosk");

        TranscriptionResult result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.engineName()).isEqualTo(SttEngineNames.RECONCILED);
    }

    @Test
    void bothNonNullDelegatesToDoReconcile() {
        TestReconciler reconciler = new TestReconciler();
        EngineResult vosk = engineResult("vosk", 0.7, "vosk");
        EngineResult whisper = engineResult("whisper", 0.95, "whisper");

        TranscriptionResult result = reconciler.reconcile(vosk, whisper);

        // doReconcile was called with both results
        assertThat(reconciler.lastVosk).isSameAs(vosk);
        assertThat(reconciler.lastWhisper).isSameAs(whisper);
        // Our test reconciler picks higher confidence (whisper)
        assertThat(result.text()).isEqualTo("whisper");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.engineName()).isEqualTo(SttEngineNames.RECONCILED);
    }

    @Test
    void toResultPreservesTextAndConfidence() {
        TestReconciler reconciler = new TestReconciler();
        EngineResult vosk = engineResult("preserved", 0.42, "vosk");

        TranscriptionResult result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("preserved");
        assertThat(result.confidence()).isEqualTo(0.42);
    }

    @Test
    void emptyResultReturnsCorrectDefaults() {
        TestReconciler reconciler = new TestReconciler();
        TranscriptionResult result = reconciler.emptyResult();

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.engineName()).isEqualTo(SttEngineNames.RECONCILED);
    }

    @Test
    void reconcileIsFinal_bothNonNullAlwaysDelegates() {
        TestReconciler reconciler = new TestReconciler();
        EngineResult vosk = engineResult("a", 0.5, "vosk");
        EngineResult whisper = engineResult("b", 0.5, "whisper");

        reconciler.reconcile(vosk, whisper);

        // Verify delegation happened
        assertThat(reconciler.lastVosk).isNotNull();
        assertThat(reconciler.lastWhisper).isNotNull();
    }
}
