package com.boombapcompile.blckvox.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void shouldReturnEmptyStringForNull() {
        assertThat(LogSanitizer.truncate(null, 10)).isEmpty();
        assertThat(LogSanitizer.truncate(null, 100)).isEmpty();
        assertThat(LogSanitizer.truncate(null, 0)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForZeroMax() {
        assertThat(LogSanitizer.truncate("hello world", 0)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForNegativeMax() {
        assertThat(LogSanitizer.truncate("hello world", -1)).isEmpty();
        assertThat(LogSanitizer.truncate("hello world", -100)).isEmpty();
    }

    @Test
    void shouldReturnFullStringWhenShorterThanMax() {
        assertThat(LogSanitizer.truncate("hello", 10)).isEqualTo("hello");
        assertThat(LogSanitizer.truncate("test", 100)).isEqualTo("test");
    }

    @Test
    void shouldReturnFullStringWhenEqualToMax() {
        assertThat(LogSanitizer.truncate("12345", 5)).isEqualTo("12345");
        assertThat(LogSanitizer.truncate("hello", 5)).isEqualTo("hello");
    }

    @Test
    void shouldTruncateWhenLongerThanMax() {
        assertThat(LogSanitizer.truncate("hello world", 5)).isEqualTo("hello");
        assertThat(LogSanitizer.truncate("This is a long string", 10)).isEqualTo("This is a ");
    }

    @Test
    void shouldTruncateToOneCharacter() {
        assertThat(LogSanitizer.truncate("hello", 1)).isEqualTo("h");
        assertThat(LogSanitizer.truncate("world", 1)).isEqualTo("w");
    }

    @Test
    void shouldHandleEmptyString() {
        assertThat(LogSanitizer.truncate("", 10)).isEmpty();
        assertThat(LogSanitizer.truncate("", 0)).isEmpty();
    }

    @Test
    void shouldTruncateVeryLongStrings() {
        String longString = "a".repeat(10000);
        assertThat(LogSanitizer.truncate(longString, 100)).hasSize(100);
        assertThat(LogSanitizer.truncate(longString, 100)).isEqualTo("a".repeat(100));
    }

    @Test
    void shouldPreserveUnicodeCharacters() {
        String unicode = "Hello 世界 🌍";
        assertThat(LogSanitizer.truncate(unicode, 5)).isEqualTo("Hello");
        assertThat(LogSanitizer.truncate(unicode, 8)).isEqualTo("Hello 世界");
    }

    @Test
    void shouldHandleWhitespaceOnlyStrings() {
        assertThat(LogSanitizer.truncate("     ", 3)).isEqualTo("   ");
        // Control chars are now sanitized: \t→\\t, \n→\\n, \r→\\r
        assertThat(LogSanitizer.truncate("\t\n\r", 4)).isEqualTo("\\t\\n");
    }

    @Test
    void sanitizeNull() {
        assertThat(LogSanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void sanitizeReplacesNewline() {
        assertThat(LogSanitizer.sanitize("a\nb")).isEqualTo("a\\nb");
    }

    @Test
    void sanitizeReplacesCarriageReturn() {
        assertThat(LogSanitizer.sanitize("a\rb")).isEqualTo("a\\rb");
    }

    @Test
    void sanitizeReplacesTab() {
        assertThat(LogSanitizer.sanitize("a\tb")).isEqualTo("a\\tb");
    }

    @Test
    void sanitizeReplacesOtherControlChars() {
        // \u0001 = SOH control character
        assertThat(LogSanitizer.sanitize("a\u0001b")).isEqualTo("a\\x01b");
    }

    @Test
    void sanitizeStripsAnsiEscapes() {
        assertThat(LogSanitizer.sanitize("hello\u001B[31mred\u001B[0mnormal"))
                .isEqualTo("hellorednormal");
    }

    @Test
    void sanitizePreservesRegularText() {
        assertThat(LogSanitizer.sanitize("Hello World 123!")).isEqualTo("Hello World 123!");
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void truncateExactlyAtMaxReturnsFullSanitized() {
        // sanitized.length() <= max (5 <= 5) → returns full sanitized string
        // Kills <= to < on L25
        assertThat(LogSanitizer.truncate("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void truncateOnePastMaxReturnsTruncated() {
        // sanitized.length() > max (6 > 5) → truncates
        assertThat(LogSanitizer.truncate("abcdef", 5)).isEqualTo("abcde");
    }
}
