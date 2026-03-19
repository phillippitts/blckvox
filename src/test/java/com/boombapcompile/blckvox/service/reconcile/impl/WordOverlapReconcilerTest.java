package com.boombapcompile.blckvox.service.reconcile.impl;

import com.boombapcompile.blckvox.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordOverlapReconcilerTest {

    @Test
    void throwsWhenThresholdOutOfRange() {
        assertThatThrownBy(() -> new WordOverlapReconciler(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold in [0,1]");

        assertThatThrownBy(() -> new WordOverlapReconciler(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold in [0,1]");
    }

    @Test
    void acceptsValidThreshold() {
        var reconciler = new WordOverlapReconciler(0.6);
        assertThat(reconciler).isNotNull();
    }

    @Test
    void prefersHigherJaccardSimilarity() {
        var reconciler = new WordOverlapReconciler(0.5);
        // "hello world" vs "world hello" - whisper has better overlap with union
        var vosk = new EngineResult("hello", 0.9, List.of("hello"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.9, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Whisper has 2/2 = 1.0 similarity, Vosk has 1/2 = 0.5 similarity
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void fallsBackToLongerTextWhenBothBelowThreshold() {
        var reconciler = new WordOverlapReconciler(0.9); // High threshold
        // Completely different words
        var vosk = new EngineResult("cat", 0.9, List.of("cat"), 100L, "vosk", null);
        var whisper = new EngineResult("dog bird", 0.9, List.of("dog", "bird"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both have low overlap (<0.9), so pick longer text
        assertThat(result.text()).isEqualTo("dog bird");
    }

    @Test
    void handlesIdenticalTokens() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("hello world", 0.9, List.of("hello", "world"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.95, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both have 100% overlap, should pick first (vosk)
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesDifferentTokenization() {
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("testing one two three", 0.9,
                List.of("testing", "one", "two", "three"), 100L, "vosk", null);
        var whisper = new EngineResult("testing three", 0.95,
                List.of("testing", "three"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Vosk: 4/4 = 1.0, Whisper: 2/4 = 0.5
        assertThat(result.text()).isEqualTo("testing one two three");
    }

    @Test
    void handlesEmptyTokens() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("hello", 0.9, List.of("hello"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("hello");
    }

    @Test
    void handlesBothEmpty() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void handlesNullVosk() {
        var reconciler = new WordOverlapReconciler(0.6);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper", "text"), 200L, "whisper", null);

        var result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void handlesNullWhisper() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk", "text"), 100L, "vosk", null);

        var result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesBothNull() {
        var reconciler = new WordOverlapReconciler(0.6);

        var result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void edgeCaseZeroThreshold() {
        var reconciler = new WordOverlapReconciler(0.0);
        var vosk = new EngineResult("cat", 0.9, List.of("cat"), 100L, "vosk", null);
        var whisper = new EngineResult("dog", 0.95, List.of("dog"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // With threshold 0, even low overlap should work
        assertThat(result).isNotNull();
    }

    @Test
    void edgeCasePerfectThreshold() {
        var reconciler = new WordOverlapReconciler(1.0);
        var vosk = new EngineResult("hello world", 0.9, List.of("hello", "world"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.95, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Perfect overlap = 1.0, meets threshold of 1.0
        assertThat(result.text()).isEqualTo("hello world");
    }

    @Test
    void partialOverlapScenario() {
        var reconciler = new WordOverlapReconciler(0.5);
        // Vosk: "the quick brown fox"
        // Whisper: "the brown dog"
        // Union: {the, quick, brown, fox, dog} = 5 words
        // Vosk similarity: 4/5 = 0.8
        // Whisper similarity: 3/5 = 0.6
        var vosk = new EngineResult("the quick brown fox", 0.9,
                List.of("the", "quick", "brown", "fox"), 100L, "vosk", null);
        var whisper = new EngineResult("the brown dog", 0.95,
                List.of("the", "brown", "dog"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("the quick brown fox");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void prefersLongerWhenEqualLowSimilarity() {
        var reconciler = new WordOverlapReconciler(0.9);
        // Both have low similarity, pick longer
        var vosk = new EngineResult("short", 0.9, List.of("short"), 100L, "vosk", null);
        var whisper = new EngineResult("much longer text here", 0.95,
                List.of("much", "longer", "text", "here"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("much longer text here");
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void jaccardDivisionReturnsCorrectValue() {
        // Vosk ["a","b"], Whisper ["b","c"] → union = {a,b,c}, size=3
        // vosk jaccard = {a,b} ∩ {a,b,c} / 3 = 2/3 ≈ 0.6667
        // whisper jaccard = {b,c} ∩ {a,b,c} / 3 = 2/3 ≈ 0.6667
        // Kills division→multiplication mutant on L119
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("a b", 0.9, List.of("a", "b"), 100L, "vosk", null);
        var whisper = new EngineResult("b c", 0.9, List.of("b", "c"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        // Both have equal similarity (0.6667), >= picks vosk
        assertThat(result.text()).isEqualTo("a b");
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void thresholdBoundaryExactlyAtMax() {
        // Both similarities = 0.5, threshold = 0.5
        // Math.max(0.5, 0.5) < 0.5 is false → goes to similarity path
        // Kills < to <= on L60
        // vosk: ["a"] in union {a,b} → 1/2=0.5, whisper: ["b"] in union {a,b} → 1/2=0.5
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("a", 0.9, List.of("a"), 100L, "vosk", null);
        var whisper = new EngineResult("b", 0.9, List.of("b"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        // similarity path: voskSimilarity (0.5) >= whisperSimilarity (0.5) → picks vosk
        assertThat(result.text()).isEqualTo("a");
    }

    @Test
    void thresholdBoundaryJustBelowThreshold() {
        // Both similarities below threshold → falls to length fallback
        // vosk: ["a"] in union {a,b} → 0.5 < 0.6 → below threshold
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("a", 0.9, List.of("a"), 100L, "vosk", null);
        var whisper = new EngineResult("bb", 0.9, List.of("b"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        // Length fallback: "bb".length()=2 >= "a".length()=1 is false → picks whisper? No...
        // len(vosk.text())=1, len(whisper.text())=2, 1 >= 2 is false → picks whisper
        assertThat(result.text()).isEqualTo("bb");
    }

    @Test
    void emptyUnionReturnsZeroJaccard() {
        // Both empty tokens → union empty → jaccard returns 0.0
        // Falls to fallback path, both texts empty, picks vosk
        // Kills removal of union.isEmpty() on L109 and tokens.isEmpty() on L112
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.0, List.of(), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        // Both jaccard=0.0, max(0,0)=0 < 0.5 → fallback
        // len("")=0 >= len("")=0 → picks vosk
        assertThat(result.text()).isEmpty();
    }

    @Test
    void equalSimilarityPicksVosk() {
        // Identical tokens → both have jaccard = 1.0
        // voskSimilarity (1.0) >= whisperSimilarity (1.0) → picks vosk
        // Kills >= to > on L64
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("hello", 0.9, List.of("hello"), 100L, "vosk", null);
        var whisper = new EngineResult("hello", 0.95, List.of("hello"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.confidence()).isEqualTo(0.9); // vosk's confidence
    }

    @Test
    void fallbackPicksEqualLengthVosk() {
        // Below threshold, equal-length texts → >= picks vosk
        // Kills >= to > on L62
        var reconciler = new WordOverlapReconciler(0.9);
        var vosk = new EngineResult("abc", 0.9, List.of("abc"), 100L, "vosk", null);
        var whisper = new EngineResult("xyz", 0.9, List.of("xyz"), 100L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);
        // Both below threshold (0/2=0 similarity), length "abc"=3 >= "xyz"=3 → picks vosk
        assertThat(result.text()).isEqualTo("abc");
    }
}
