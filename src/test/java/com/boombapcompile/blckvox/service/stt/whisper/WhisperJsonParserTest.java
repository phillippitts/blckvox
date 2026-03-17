package com.boombapcompile.blckvox.service.stt.whisper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperJsonParserTest {

    @Test
    void extractTextPrefersTopLevelText() {
        String json = "{\n  \"text\": \"hello world\",\n  \"segments\": [{\"text\": \"ignored\"}]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractTextConcatenatesSegmentsWhenNoTopLevel() {
        String json = "{\n  \"segments\": [\n    {\"text\": \"hello\"},\n    {\"text\": \"world\"}\n  ]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractTokensPrefersWordsWhenAvailable() {
        String json = "{\n  \"segments\": [\n    {\"words\": [\n      {\"word\": \"Hello\"},"
                + "\n      {\"word\": \"WORLD!\"}\n    ]}\n  ]\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTokensFallsBackToTextTokenization() {
        String json = "{\n  \"text\": \"Alpha, beta. GAMMA!\"\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void extractTextHandlesMalformedGracefully() {
        String text = WhisperJsonParser.extractText("{ not-json");
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensHandlesMalformedGracefully() {
        List<String> tokens = WhisperJsonParser.extractTokens("{ not-json");
        assertThat(tokens).isEmpty();
    }

    @Test
    void extractTextEmptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractText(null)).isEmpty();
        assertThat(WhisperJsonParser.extractText("")).isEmpty();
    }

    @Test
    void extractTextHandlesWhitespaceOnly() {
        String json = "{\n  \"text\": \"   \"\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTextMultipleSegmentsWithMixedContent() {
        String json = """
            {
              "segments": [
                {"text": "First segment"},
                {"text": ""},
                {"text": "   "},
                {"text": "Last segment"}
              ]
            }
            """;
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("First segment Last segment");
    }

    @Test
    void extractTokensMultipleSegmentsWithWords() {
        String json = """
            {
              "segments": [
                {"words": [{"word": "Hello"}, {"word": "there"}]},
                {"words": [{"word": "General"}, {"word": "Kenobi!"}]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "there", "general", "kenobi");
    }

    @Test
    void extractTokensSegmentsWithoutWordsFallsBackToSegmentText() {
        String json = """
            {
              "segments": [
                {"text": "Hello world"},
                {"text": "How are you"}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world", "how", "are", "you");
    }

    @Test
    void extractTokensHandlesSpecialCharacters() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "Hello,"},
                  {"word": "world!"},
                  {"word": "123"},
                  {"word": "test@example.com"}
                ]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world", "test", "example", "com");
    }

    @Test
    void extractTokensEmptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractTokens(null)).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("")).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("{}")).isEmpty();
    }

    @Test
    void extractTokensFiltersBlankWords() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "valid"},
                  {"word": "   "},
                  {"word": ""},
                  {"word": "also-valid"}
                ]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("valid", "also", "valid");
    }

    @Test
    void extractTextEmptySegmentsArray() {
        String json = "{\"segments\": []}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensEmptySegmentsArray() {
        String json = "{\"segments\": []}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).isEmpty();
    }

    // ---- Pause detection and segment-timestamp tests ----

    @Test
    void extractTextWithPauseDetectionInsertsNewlineOnLargeGap() {
        String json = """
            {
              "segments": [
                {"text": "Hello", "start": 0.0, "end": 1.0},
                {"text": "World", "start": 3.0, "end": 4.0}
              ]
            }
            """;
        // 1000ms gap threshold; gap is 2.0s (3.0 - 1.0 = 2000ms > 1000ms)
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("Hello\nWorld");
    }

    @Test
    void extractTextWithPauseDetectionInsertsSpaceOnSmallGap() {
        String json = """
            {
              "segments": [
                {"text": "Hello", "start": 0.0, "end": 1.0},
                {"text": "World", "start": 1.2, "end": 2.0}
              ]
            }
            """;
        // 1000ms gap threshold; gap is 200ms < 1000ms
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("Hello World");
    }

    @Test
    void extractTextWithPauseDetectionHandlesEmptySegments() {
        String json = """
            {
              "segments": []
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTextWithPauseDetectionSkipsBlankSegmentText() {
        String json = """
            {
              "segments": [
                {"text": "Hello", "start": 0.0, "end": 1.0},
                {"text": "   ", "start": 1.5, "end": 2.0},
                {"text": "World", "start": 4.0, "end": 5.0}
              ]
            }
            """;
        // Blank segment should be skipped; gap from end of "Hello" (1.0) to "World" (4.0) = 3.0s
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("Hello\nWorld");
    }

    @Test
    void extractTextWithPauseDetectionHandlesSingleSegment() {
        String json = """
            {
              "segments": [
                {"text": "Only segment", "start": 0.0, "end": 1.0}
              ]
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("Only segment");
    }

    @Test
    void extractTextWithPauseDetectionHandlesMissingTimestamps() {
        String json = """
            {
              "segments": [
                {"text": "First"},
                {"text": "Second"}
              ]
            }
            """;
        // No start/end timestamps - should just concatenate with spaces
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("First Second");
    }

    @Test
    void extractTextWithPauseDetectionMixedTimestampPresence() {
        String json = """
            {
              "segments": [
                {"text": "First", "start": 0.0, "end": 1.0},
                {"text": "Second"},
                {"text": "Third", "start": 5.0, "end": 6.0}
              ]
            }
            """;
        // Second segment missing timestamps, third has start > prevEnd but prevEnd is from first (1.0)
        // Gap: 5.0 - 1.0 = 4.0s > 1.0s threshold
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).contains("First");
        assertThat(text).contains("Second");
        assertThat(text).contains("Third");
    }

    @Test
    void extractTextWithPauseDetectionZeroGapDisablesPauseDetection() {
        String json = """
            {
              "text": "top level text",
              "segments": [
                {"text": "segment text", "start": 0.0, "end": 1.0},
                {"text": "more text", "start": 5.0, "end": 6.0}
              ]
            }
            """;
        // silenceGapMs=0 disables pause detection, falls back to top-level text
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 0);
        assertThat(text).isEqualTo("top level text");
    }

    @Test
    void extractTextWithPauseDetectionNoSegmentsFallsToText() {
        // silenceGapMs > 0 but JSON has no "segments" key → falls through to obj.has("text")
        String json = """
            {
              "text": "top level only"
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("top level only");
    }

    @Test
    void extractTextWithPauseDetectionNoSegmentsNoText() {
        // silenceGapMs > 0, no "segments", no "text" → empty
        String json = "{}";
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensHandlesWordsFromMultipleSegments() {
        String json = """
            {
              "segments": [
                {"words": [{"word": "Hello"}, {"word": "World"}]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTextSkipsNullSegmentObjectsInSimpleMode() {
        // JSON array with null-like entries (JSONObject.optJSONObject returns null for non-objects)
        String json = """
            {
              "segments": [
                {"text": "Hello"},
                "not-an-object",
                {"text": "World"}
              ]
            }
            """;
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("Hello World");
    }

    @Test
    void extractTextWithPauseDetectionSkipsNullSegments() {
        String json = """
            {
              "segments": [
                {"text": "Hello", "start": 0.0, "end": 1.0},
                "not-an-object",
                {"text": "World", "start": 1.5, "end": 2.5}
              ]
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEqualTo("Hello World");
    }

    @Test
    void extractTokensSkipsNullSegments() {
        String json = """
            {
              "segments": [
                {"words": [{"word": "Hello"}]},
                "not-a-segment",
                {"words": [{"word": "World"}]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTokensSkipsNullWordObjects() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "Hello"},
                  "not-a-word-object",
                  {"word": "World"}
                ]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTextPauseDetectionSegmentMissingEndTimestamp() {
        // First segment has no "end" → prevEnd stays -1, second segment treated as no-gap
        String json = """
            {
              "segments": [
                {"text": "Hello", "start": 0.0},
                {"text": "World", "start": 5.0, "end": 6.0}
              ]
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        // No pause detection because prevEnd is -1 after first segment (no end timestamp)
        assertThat(text).isEqualTo("Hello World");
    }

    @Test
    void extractTextSegmentsAsNonArrayValue() {
        // "segments" key exists but value is not an array
        String json = """
            {
              "segments": "not-an-array"
            }
            """;
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTextPauseDetectionSegmentsAsNonArrayValue() {
        String json = """
            {
              "segments": "not-an-array"
            }
            """;
        String text = WhisperJsonParser.extractTextWithPauseDetection(json, 1000);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensSegmentsWithNullWordsArray() {
        // Segment has "words" key but it's not an array
        String json = """
            {
              "segments": [
                {"words": "not-an-array", "text": "fallback text"}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        // words is not an array → no tokens from words → falls back to text tokenization
        assertThat(tokens).containsExactly("fallback", "text");
    }

    @Test
    void extractTokensFromSegmentWithNoWords() {
        String json = "{\"segments\": [{\"text\": \"hello world\"}]}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTextWithPauseDetectionNullSegment() {
        // JSONArray with a null entry — optJSONObject returns null
        String json = "{\"segments\": [null, {\"text\": \"hello\", \"start\": 0.0, \"end\": 1.0}]}";
        assertThat(WhisperJsonParser.extractTextWithPauseDetection(json, 500)).isEqualTo("hello");
    }

    @Test
    void extractTokensWithNullWordEntry() {
        // words array contains a null entry
        String json = "{\"segments\": [{\"words\": [null, {\"word\": \"hi\"}]}]}";
        assertThat(WhisperJsonParser.extractTokens(json)).containsExactly("hi");
    }

    @Test
    void extractTokensWithBlankWord() {
        String json = "{\"segments\": [{\"words\": [{\"word\": \"  \"}, {\"word\": \"hi\"}]}]}";
        assertThat(WhisperJsonParser.extractTokens(json)).containsExactly("hi");
    }

    @Test
    void extractTextWithNullTopLevelText() {
        // top-level "text" key exists but value is JSON null —
        // optString returns "" for null, which is blank, so code returns ""
        // rather than falling through to segments.
        String json = "{\"text\": null, \"segments\": [{\"text\": \"fallback\"}]}";
        assertThat(WhisperJsonParser.extractText(json)).isEmpty();
    }

    @Test
    void extractTextNullSegmentInConcatenation() {
        // When no top-level text, segments with null entry
        String json = "{\"segments\": [null, {\"text\": \"world\"}]}";
        assertThat(WhisperJsonParser.extractText(json)).isEqualTo("world");
    }

    @Test
    void extractTextBlankSegmentTextInConcatenation() {
        String json = "{\"segments\": [{\"text\": \"  \"}, {\"text\": \"world\"}]}";
        assertThat(WhisperJsonParser.extractText(json)).isEqualTo("world");
    }

    @Test
    void extractTokensNullSegmentInArray() {
        String json = "{\"segments\": [null]}";
        assertThat(WhisperJsonParser.extractTokens(json)).isEmpty();
    }

    @Test
    void extractTextWithPauseDetectionSingleSegmentNoTimestamps() {
        // First and only segment with no start/end — exercises the else if (sb.length() > 0) false branch
        // sb.length() is 0 for the first segment, so no space is added
        String json = "{\"segments\": [{\"text\": \"hello\"}]}";
        assertThat(WhisperJsonParser.extractTextWithPauseDetection(json, 500)).isEqualTo("hello");
    }

    @Test
    void extractTextWithPauseDetectionSegmentWithNullWordsInTokenExtraction() {
        // Segment has words key as null (not an array) — optJSONArray returns null
        String json = "{\"segments\": [{\"words\": null, \"text\": \"fallback\"}]}";
        assertThat(WhisperJsonParser.extractTokens(json)).containsExactly("fallback");
    }

    // ---- extractConfidence tests ----

    @Test
    void extractConfidenceAveragesProbFields() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "prob": 0.9},
                  {"word": "world", "prob": 0.7}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.8);
    }

    @Test
    void extractConfidenceAcceptsProbabilityField() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "probability": 0.6},
                  {"word": "world", "probability": 0.8}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.7);
    }

    @Test
    void extractConfidencePrefProbOverProbability() {
        // If both "prob" and "probability" present, "prob" wins
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "prob": 0.5, "probability": 0.9}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.5);
    }

    @Test
    void extractConfidenceReturnsFallbackForNoWords() {
        String json = """
            {
              "segments": [
                {"text": "hello world"}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceReturnsFallbackForEmptySegments() {
        String json = "{\"segments\": []}";
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceReturnsFallbackForNull() {
        assertThat(WhisperJsonParser.extractConfidence(null)).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceReturnsFallbackForBlank() {
        assertThat(WhisperJsonParser.extractConfidence("")).isEqualTo(0.85);
        assertThat(WhisperJsonParser.extractConfidence("   ")).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceReturnsFallbackForMalformedJson() {
        assertThat(WhisperJsonParser.extractConfidence("{ not-json")).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceReturnsFallbackForNoSegments() {
        String json = "{\"text\": \"hello\"}";
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceClampsAboveOne() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "prob": 1.5}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(1.0);
    }

    @Test
    void extractConfidenceClampsBeforeZero() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "prob": -0.5}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.0);
    }

    @Test
    void extractConfidenceSkipsWordsWithoutProb() {
        // Mixed: some words have prob, some don't — average only those that have it
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "hello", "prob": 0.6},
                  {"word": "world"}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.6);
    }

    @Test
    void extractConfidenceAcrossMultipleSegments() {
        String json = """
            {
              "segments": [
                {"words": [{"word": "a", "prob": 0.4}]},
                {"words": [{"word": "b", "prob": 0.8}]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void extractConfidenceSkipsNullSegmentsAndWords() {
        String json = "{\"segments\": [null, {\"words\": [null, {\"word\": \"hi\", \"prob\": 0.5}]}]}";
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.5);
    }

    @Test
    void extractConfidenceSkipsSegmentsWithNullWordsArray() {
        String json = "{\"segments\": [{\"words\": null}]}";
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.85);
    }

    // --- Parameterized confidence boundary tests ---

    @ParameterizedTest(name = "extractConfidence: prob={0} should return {1}")
    @CsvSource({
        "0.0, 0.0",     // exact zero
        "0.001, 0.001",  // near zero
        "0.5, 0.5",     // midpoint
        "0.999, 0.999",  // near one
        "1.0, 1.0",     // exact one
        "-0.1, 0.0",    // negative clamped to zero
        "1.5, 1.0",     // above one clamped to one
        "-100.0, 0.0",  // extreme negative clamped
        "100.0, 1.0",   // extreme positive clamped
    })
    void extractConfidenceBoundaryValues(double prob, double expected) {
        String json = String.format("""
            {
              "segments": [
                {"words": [{"word": "test", "prob": %s}]}
              ]
            }
            """, prob);
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
    }

    @ParameterizedTest(name = "extractConfidence: two words with probs {0} and {1} should average to {2}")
    @CsvSource({
        "0.0, 1.0, 0.5",     // zero + one averages to 0.5
        "0.3, 0.7, 0.5",     // symmetric around 0.5
        "0.9, 0.9, 0.9",     // identical values
        "0.0, 0.0, 0.0",     // both zero
        "1.0, 1.0, 1.0",     // both one
    })
    void extractConfidenceAveragingBoundaries(double prob1, double prob2, double expected) {
        String json = String.format("""
            {
              "segments": [
                {"words": [
                  {"word": "a", "prob": %s},
                  {"word": "b", "prob": %s}
                ]}
              ]
            }
            """, prob1, prob2);
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
    }

    // --- avg_logprob fallback tests ---

    @Test
    void extractConfidenceFallsBackToAvgLogprob() {
        // No word-level prob fields, but segments have avg_logprob
        // Math.exp(-0.5) ≈ 0.6065
        String json = """
            {
              "segments": [
                {"text": "hello world", "avg_logprob": -0.5}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(Math.exp(-0.5), org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void extractConfidencePrefersWordProbOverAvgLogprob() {
        // Word-level prob fields present — avg_logprob should be ignored
        String json = """
            {
              "segments": [
                {"avg_logprob": -2.0, "words": [
                  {"word": "hello", "prob": 0.9}
                ]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.9);
    }

    @Test
    void extractConfidenceAvgLogprobClampsAboveOne() {
        // avg_logprob of 0.0 → Math.exp(0.0) = 1.0, should clamp to 1.0
        String json = """
            {
              "segments": [
                {"text": "hello", "avg_logprob": 0.0}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(1.0);
    }

    @Test
    void extractConfidenceAvgLogprobClampsNearZero() {
        // Very negative avg_logprob → Math.exp(-10) ≈ 0.0000454
        String json = """
            {
              "segments": [
                {"text": "hello", "avg_logprob": -10.0}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(Math.exp(-10.0), org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void extractConfidenceAvgLogprobMultipleSegments() {
        // Two segments: Math.exp(-0.3) ≈ 0.7408, Math.exp(-0.7) ≈ 0.4966
        // Average ≈ 0.6187
        String json = """
            {
              "segments": [
                {"text": "first", "avg_logprob": -0.3},
                {"text": "second", "avg_logprob": -0.7}
              ]
            }
            """;
        double expected = (Math.exp(-0.3) + Math.exp(-0.7)) / 2;
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void extractConfidenceNoWordProbsNoAvgLogprobReturnsFallback() {
        // Segments have words but no prob fields and no avg_logprob
        String json = """
            {
              "segments": [
                {"words": [{"word": "hello"}]}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json)).isEqualTo(0.85);
    }

    @Test
    void extractConfidenceAvgLogprobSkipsSegmentsWithoutField() {
        // Mix: one segment has avg_logprob, one doesn't
        String json = """
            {
              "segments": [
                {"text": "first", "avg_logprob": -0.5},
                {"text": "second"}
              ]
            }
            """;
        assertThat(WhisperJsonParser.extractConfidence(json))
                .isCloseTo(Math.exp(-0.5), org.assertj.core.data.Offset.offset(0.001));
    }
}
