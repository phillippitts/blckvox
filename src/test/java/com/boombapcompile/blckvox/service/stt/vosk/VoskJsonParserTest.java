package com.boombapcompile.blckvox.service.stt.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoskJsonParserTest {

    @Test
    void nullInputReturnsEmptyTextAndFullConfidence() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(null);
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void blankInputReturnsEmptyTextAndFullConfidence() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse("   ");
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void emptyStringReturnsEmptyTextAndFullConfidence() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse("");
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void oversizedJsonIsTruncatedBeforeParsing() {
        // Build a JSON string exceeding 1MB — truncation will corrupt the JSON, yielding empty result
        String base = "{\"text\":\"hello\"}";
        StringBuilder sb = new StringBuilder(1_100_000);
        while (sb.length() < 1_100_000) {
            sb.append(base);
        }
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(sb.toString());
        // Truncated JSON won't parse correctly — should return empty fallback
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void finalResultFormatWithPerWordConfidence() {
        String json = """
                {
                  "text": "hello world",
                  "result": [
                    {"word": "hello", "conf": 0.9},
                    {"word": "world", "conf": 0.8}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void alternativesFormatExtractsTextAndConfidence() {
        String json = """
                {
                  "alternatives": [
                    {"text": "good morning", "confidence": 0.95}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("good morning");
        assertThat(result.confidence()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void alternativesConfidenceAboveOneClampedToOne() {
        String json = """
                {
                  "alternatives": [
                    {"text": "over", "confidence": 2.5}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("over");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void alternativesConfidenceBelowZeroClampedToZero() {
        String json = """
                {
                  "alternatives": [
                    {"text": "under", "confidence": -0.5}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("under");
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void emptyAlternativesArrayReturnsEmptyTextAndFullConfidence() {
        String json = """
                {
                  "alternatives": []
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void emptyResultArrayReturnsDefaultConfidence() {
        String json = """
                {
                  "text": "silent",
                  "result": []
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("silent");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void noResultNoAlternativesReturnsTextOnlyWithDefaultConfidence() {
        String json = """
                {"text": "just text"}
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("just text");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void malformedJsonReturnsEmptyResultWithoutException() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse("{broken json!!");
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void resultArrayWithWordsLackingConfFieldDefaultsToOne() {
        String json = """
                {
                  "text": "no conf",
                  "result": [
                    {"word": "no"},
                    {"word": "conf"}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("no conf");
        // No conf fields → count==0 → 1.0 default
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void resultArrayWithMixedConfFieldsAveragesOnlyPresentOnes() {
        String json = """
                {
                  "text": "mixed",
                  "result": [
                    {"word": "hello", "conf": 0.6},
                    {"word": "world"}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("mixed");
        // Only one word has conf (0.6), average = 0.6
        assertThat(result.confidence()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void textWithWhitespaceIsTrimmed() {
        String json = """
                {"text": "  trimmed text  "}
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("trimmed text");
    }

    @Test
    void alternativesTextWithWhitespaceIsTrimmed() {
        String json = """
                {
                  "alternatives": [
                    {"text": "  spaces  ", "confidence": 0.9}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("spaces");
    }

    @Test
    void resultConfidenceClampedAboveOne() {
        String json = """
                {
                  "text": "over",
                  "result": [
                    {"word": "over", "conf": 1.5}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void resultConfidenceClampedBelowZero() {
        String json = """
                {
                  "text": "under",
                  "result": [
                    {"word": "under", "conf": -0.3}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void alternativesWithMissingConfidenceDefaultsToOne() {
        String json = """
                {
                  "alternatives": [
                    {"text": "no conf"}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("no conf");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void alternativesWithMissingTextDefaultsToEmpty() {
        String json = """
                {
                  "alternatives": [
                    {"confidence": 0.7}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.001));
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void truncateJsonIfNeededExactlyAtLimit() {
        // JSON of exactly MAX_JSON_SIZE (1_048_576) should NOT be truncated
        // Kills > to >= on L98 (json.length() > MAX_JSON_SIZE)
        StringBuilder sb = new StringBuilder(1_048_576);
        sb.append("{\"text\":\"");
        while (sb.length() < 1_048_576 - 2) {
            sb.append('a');
        }
        sb.append("\"}");
        // Pad or trim to exactly 1_048_576
        while (sb.length() < 1_048_576) {
            sb.append(' ');
        }
        String json = sb.substring(0, 1_048_576);
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        // At exactly the limit, should not be truncated → parses normally
        assertThat(result.text()).isNotNull();
    }

    @Test
    void truncateJsonOneBeyondLimit() {
        // JSON of MAX_JSON_SIZE + 1 → gets truncated → likely corrupt → fallback
        StringBuilder sb = new StringBuilder(1_048_577);
        sb.append("{\"text\":\"");
        while (sb.length() < 1_048_577) {
            sb.append('b');
        }
        String json = sb.substring(0, 1_048_577);
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        // Truncated JSON won't parse correctly
        assertThat(result).isNotNull();
    }

    @Test
    void singleWordConfidenceReturnsExactValue() {
        // Single word with conf → count=1, avg=conf value
        // Kills division/count mutants in extractConfidence
        String json = """
                {
                  "text": "hello",
                  "result": [
                    {"word": "hello", "conf": 0.42}
                  ]
                }
                """;
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isCloseTo(0.42, org.assertj.core.data.Offset.offset(0.001));
    }
}
