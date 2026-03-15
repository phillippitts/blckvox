package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionOutputTest {

    @Test
    void nullResultThrowsNpe() {
        assertThatThrownBy(() -> new TranscriptionOutput(null, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("result");
    }

    @Test
    void nullTokensNormalizedToEmptyList() {
        TranscriptionResult r = TranscriptionResult.of("hello", 0.9, "vosk");
        var output = new TranscriptionOutput(r, null, null);
        assertThat(output.tokens()).isEmpty();
    }

    @Test
    void tokensAreDefensivelyCopied() {
        TranscriptionResult r = TranscriptionResult.of("hello", 0.9, "vosk");
        List<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        var output = new TranscriptionOutput(r, mutable, null);
        mutable.add("c");
        assertThat(output.tokens()).containsExactly("a", "b");
    }

    @Test
    void ofWithResultOnlyCreatesEmptyTokens() {
        TranscriptionResult r = TranscriptionResult.of("hello", 0.9, "vosk");
        var output = TranscriptionOutput.of(r);
        assertThat(output.result()).isSameAs(r);
        assertThat(output.tokens()).isEmpty();
        assertThat(output.rawJson()).isNull();
    }

    @Test
    void ofWithAllFieldsPreservesValues() {
        TranscriptionResult r = TranscriptionResult.of("hello", 0.9, "vosk");
        var output = TranscriptionOutput.of(r, List.of("hello"), "{\"text\":\"hello\"}");
        assertThat(output.result()).isSameAs(r);
        assertThat(output.tokens()).containsExactly("hello");
        assertThat(output.rawJson()).isEqualTo("{\"text\":\"hello\"}");
    }
}
