package com.boombapcompile.blckvox.service.stt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineResultTest {

    @Test
    void validConstructionSucceeds() {
        var result = new EngineResult("hello", 0.9, List.of("hello"), 100L, "vosk", null);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.tokens()).containsExactly("hello");
        assertThat(result.durationMs()).isEqualTo(100L);
        assertThat(result.engineName()).isEqualTo("vosk");
        assertThat(result.rawJson()).isNull();
    }

    @Test
    void nullTextThrowsNpe() {
        assertThatThrownBy(() -> new EngineResult(null, 0.9, List.of(), 100L, "vosk", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("text");
    }

    @Test
    void nullEngineNameThrowsNpe() {
        assertThatThrownBy(() -> new EngineResult("hello", 0.9, List.of(), 100L, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engineName");
    }

    @Test
    void negativeConfidenceThrowsIae() {
        assertThatThrownBy(() -> new EngineResult("hello", -0.1, List.of(), 100L, "vosk", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void confidenceAboveOneThrowsIae() {
        assertThatThrownBy(() -> new EngineResult("hello", 1.1, List.of(), 100L, "vosk", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void nullTokensNormalizedToEmptyList() {
        var result = new EngineResult("hello", 0.9, null, 100L, "vosk", null);
        assertThat(result.tokens()).isEmpty();
    }

    @Test
    void tokensAreDefensivelyCopied() {
        List<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        var result = new EngineResult("hello", 0.9, mutable, 100L, "vosk", null);
        mutable.add("c"); // Mutate original
        assertThat(result.tokens()).containsExactly("a", "b"); // Defensive copy unchanged
    }

    @Test
    void boundaryConfidenceValuesAreValid() {
        var zero = new EngineResult("text", 0.0, List.of(), 100L, "vosk", null);
        assertThat(zero.confidence()).isEqualTo(0.0);

        var one = new EngineResult("text", 1.0, List.of(), 100L, "vosk", null);
        assertThat(one.confidence()).isEqualTo(1.0);
    }

    @Test
    void rawJsonCanBeNonNull() {
        var result = new EngineResult("hello", 0.9, List.of(), 100L, "vosk", "{\"text\":\"hello\"}");
        assertThat(result.rawJson()).isEqualTo("{\"text\":\"hello\"}");
    }
}
