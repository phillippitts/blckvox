package com.boombapcompile.blckvox.service.stt.whisper;

import org.junit.jupiter.api.Test;

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
}
