package com.boombapcompile.blckvox.service.stt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenizerUtilTest {

    @Test
    void tokenizeNull() {
        assertThat(TokenizerUtil.tokenize(null)).isEmpty();
    }

    @Test
    void tokenizeBlank() {
        assertThat(TokenizerUtil.tokenize("   ")).isEmpty();
    }

    @Test
    void tokenizeSingleWord() {
        assertThat(TokenizerUtil.tokenize("hello")).containsExactly("hello");
    }

    @Test
    void tokenizeMultipleWords() {
        assertThat(TokenizerUtil.tokenize("hello world foo"))
                .containsExactly("hello", "world", "foo");
    }

    @Test
    void tokenizeWithNumbers() {
        assertThat(TokenizerUtil.tokenize("hello123world"))
                .containsExactly("hello", "world");
    }

    @Test
    void tokenizeWithPunctuation() {
        assertThat(TokenizerUtil.tokenize("hi, there!"))
                .containsExactly("hi", "there");
    }

    @Test
    void tokenizeMixedCase() {
        assertThat(TokenizerUtil.tokenize("Hello WORLD FoO"))
                .containsExactly("hello", "world", "foo");
    }

    @Test
    void tokenizeOnlyNonAlpha() {
        assertThat(TokenizerUtil.tokenize("123!@#")).isEmpty();
    }

    @Test
    void tokenizeResultIsImmutable() {
        List<String> result = TokenizerUtil.tokenize("hello world");
        assertThatThrownBy(() -> result.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tokenizeLeadingDelimiterProducesBlankPartThatIsFiltered() {
        // Split on non-alpha at start produces empty first element, exercising isBlank() true branch on line 39
        assertThat(TokenizerUtil.tokenize("123hello")).containsExactly("hello");
    }

    @Test
    void tokenizeEmptyString() {
        assertThat(TokenizerUtil.tokenize("")).isEmpty();
    }

    @Test
    void tokenizeWhitespaceOnly() {
        assertThat(TokenizerUtil.tokenize("\t\n ")).isEmpty();
    }
}
