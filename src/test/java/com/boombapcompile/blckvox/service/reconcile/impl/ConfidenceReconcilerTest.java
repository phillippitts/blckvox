package com.boombapcompile.blckvox.service.reconcile.impl;

import com.boombapcompile.blckvox.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceReconcilerTest {

    private final ConfidenceReconciler reconciler = new ConfidenceReconciler();

    @Test
    void prefersHigherConfidence() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void prefersVoskWhenVoskHasHigherConfidence() {
        var vosk = new EngineResult("vosk text", 0.98, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.85, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.98);
    }

    @Test
    void tieBreaksPrefersNonEmptyText() {
        var vosk = new EngineResult("", 0.9, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void tieBreaksDefaultsToVoskWhenBothNonEmpty() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void tieBreaksDefaultsToVoskWhenBothEmpty() {
        var vosk = new EngineResult("", 0.5, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.5, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.5);
    }

    @Test
    void handlesNullVosk() {
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void handlesNullWhisper() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);

        var result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesBothNull() {
        var result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void prefersNonBlankTextOnTie() {
        var vosk = new EngineResult("  ", 0.9, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
    }

    @Test
    void handlesVeryLowConfidence() {
        var vosk = new EngineResult("vosk text", 0.1, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.05, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.1);
    }

    @Test
    void handlesPerfectConfidence() {
        var vosk = new EngineResult("vosk text", 0.95, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 1.0, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void equalConfidenceTieBreakToNonEmptyText() {
        // Equal confidence, vosk empty, whisper non-empty → picks whisper
        // Kills conditions on L41, L44
        var vosk = new EngineResult("", 0.9, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void tieBreakBothNonEmptyDefaultsToVosk() {
        // Equal confidence, both non-empty → defaults to vosk (L49)
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        assertThat(result.text()).isEqualTo("vosk text");
    }

    @Test
    void confidenceJustAbovePicksHigher() {
        // vosk=0.91, whisper=0.90 → vosk.confidence() > whisper.confidence() is true
        // Kills > to >= on L30
        var vosk = new EngineResult("vosk text", 0.91, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.90, List.of("whisper"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.91);
    }

    @Test
    void tieBreakVoskNonEmptyWhisperEmptyPicksVosk() {
        // Equal confidence, vosk non-empty, whisper empty → picks vosk (L41)
        var vosk = new EngineResult("vosk text", 0.8, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.8, List.of(), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.8);
    }
}
