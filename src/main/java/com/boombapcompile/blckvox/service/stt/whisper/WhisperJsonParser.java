package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.service.stt.TokenizerUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to parse whisper.cpp JSON stdout into text/tokens for reconciliation.
 * Safe against malformed input: falls back to empty tokens and empty text.
 */
final class WhisperJsonParser {

    private WhisperJsonParser() {}

    /**
     * Extracts text from Whisper JSON output without pause detection.
     *
     * @param json Whisper JSON output
     * @return concatenated text from all segments
     */
    static String extractText(String json) {
        return extractTextWithPauseDetection(json, 0);
    }

    /**
     * Extracts text from Whisper JSON output with automatic pause detection.
     * Inserts newlines when the gap between segments exceeds the threshold.
     *
     * @param json Whisper JSON output with segments
     * @param silenceGapMs threshold in milliseconds for inserting newlines (0 = disabled)
     * @return text with newlines inserted at pause boundaries
     */
    static String extractTextWithPauseDetection(String json, int silenceGapMs) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JSONObject obj = new JSONObject(json);

            // If segments are available and pause detection is enabled, use segment timestamps
            if (silenceGapMs > 0 && obj.has("segments")) {
                return extractTextFromSegmentsWithPauses(obj, silenceGapMs);
            }

            // Otherwise, fall back to simple text extraction
            // Prefer top-level text if present
            if (obj.has("text")) {
                String t = obj.optString("text", "");
                if (t.isBlank()) {
                    return "";
                }
                return t.trim();
            }

            // Otherwise, concatenate segment texts without pause detection
            if (obj.has("segments")) {
                StringBuilder sb = new StringBuilder();
                JSONArray segs = obj.optJSONArray("segments");
                if (segs != null) {
                    for (int i = 0; i < segs.length(); i++) {
                        JSONObject seg = segs.optJSONObject(i);
                        if (seg != null) {
                            String t = seg.optString("text", "");
                            if (!t.isBlank()) {
                                if (sb.length() > 0) {
                                    sb.append(' ');
                                }
                                sb.append(t.trim());
                            }
                        }
                    }
                }
                return sb.toString();
            }
        } catch (org.json.JSONException ignored) {
            // Malformed JSON — fall through to blank
        }
        return "";
    }

    /**
     * Extracts text from segments with pause detection based on timestamps.
     *
     * @param obj parsed JSON object containing segments
     * @param silenceGapMs threshold in milliseconds
     * @return text with newlines at pause boundaries
     */
    private static String extractTextFromSegmentsWithPauses(JSONObject obj, int silenceGapMs) {
        StringBuilder sb = new StringBuilder();
        JSONArray segs = obj.optJSONArray("segments");
        if (segs == null || segs.length() == 0) {
            return "";
        }

        double silenceGapSec = silenceGapMs / 1000.0;
        double prevEnd = -1;

        for (int i = 0; i < segs.length(); i++) {
            JSONObject seg = segs.optJSONObject(i);
            if (seg == null) {
                continue;
            }

            String text = seg.optString("text", "").trim();
            if (text.isBlank()) {
                continue;
            }

            double start = seg.optDouble("start", -1);
            double end = seg.optDouble("end", -1);

            // If this is not the first segment, check for pause
            if (prevEnd >= 0 && start >= 0) {
                double gap = start - prevEnd;
                if (gap > silenceGapSec) {
                    // Insert newline for pause
                    sb.append('\n');
                } else if (sb.length() > 0) {
                    // Insert space for normal continuation
                    sb.append(' ');
                }
            } else if (sb.length() > 0) {
                // First segment or missing timestamps - just add space
                sb.append(' ');
            }

            sb.append(text);

            // Update prevEnd for next iteration
            if (end >= 0) {
                prevEnd = end;
            }
        }

        return sb.toString();
    }

    /**
     * Extracts average word-level confidence from Whisper JSON output.
     *
     * <p>Looks for {@code prob} or {@code probability} fields in
     * {@code segments[].words[]} and averages them. Returns 0.85 fallback
     * when no word-level probabilities are available (Whisper is generally
     * accurate, so default above 0.5 is reasonable).
     *
     * @param json Whisper JSON output
     * @return average confidence clamped to [0.0, 1.0], or 0.85 if unavailable
     */
    static double extractConfidence(String json) {
        if (json == null || json.isBlank()) {
            return 0.85;
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (!obj.has("segments")) {
                return 0.85;
            }
            JSONArray segs = obj.optJSONArray("segments");
            if (segs == null || segs.length() == 0) {
                return 0.85;
            }

            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < segs.length(); i++) {
                JSONObject seg = segs.optJSONObject(i);
                if (seg == null) {
                    continue;
                }
                JSONArray words = seg.optJSONArray("words");
                if (words == null) {
                    continue;
                }
                for (int w = 0; w < words.length(); w++) {
                    JSONObject word = words.optJSONObject(w);
                    if (word == null) {
                        continue;
                    }
                    // whisper.cpp uses "prob"; some builds use "probability"
                    if (word.has("prob")) {
                        sum += word.optDouble("prob", 0.0);
                        count++;
                    } else if (word.has("probability")) {
                        sum += word.optDouble("probability", 0.0);
                        count++;
                    }
                }
            }

            if (count == 0) {
                // Fallback: try segment-level avg_logprob
                return extractConfidenceFromAvgLogprob(segs);
            }
            double avg = sum / count;
            return Math.min(1.0, Math.max(0.0, avg));
        } catch (org.json.JSONException ignored) {
            return 0.85;
        }
    }

    /**
     * Extracts confidence from segment-level {@code avg_logprob} fields.
     * Converts log-probability to linear probability via {@code Math.exp()},
     * averages across segments, and clamps to [0.0, 1.0].
     * Returns 0.85 fallback if no avg_logprob fields are found.
     */
    private static double extractConfidenceFromAvgLogprob(JSONArray segs) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < segs.length(); i++) {
            JSONObject seg = segs.optJSONObject(i);
            if (seg == null || !seg.has("avg_logprob")) {
                continue;
            }
            double logprob = seg.optDouble("avg_logprob", Double.NaN);
            if (Double.isNaN(logprob)) {
                continue;
            }
            sum += Math.exp(logprob);
            count++;
        }
        if (count == 0) {
            return 0.85;
        }
        double avg = sum / count;
        return Math.min(1.0, Math.max(0.0, avg));
    }

    static List<String> extractTokens(String json) {
        List<String> tokens = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JSONObject obj = new JSONObject(json);
            // If words available, prefer them
            if (obj.has("segments")) {
                JSONArray segs = obj.optJSONArray("segments");
                if (segs != null) {
                    for (int i = 0; i < segs.length(); i++) {
                        JSONObject seg = segs.optJSONObject(i);
                        if (seg == null) {
                            continue;
                        }
                        JSONArray words = seg.optJSONArray("words");
                        if (words != null) {
                            for (int w = 0; w < words.length(); w++) {
                                JSONObject word = words.optJSONObject(w);
                                if (word == null) {
                                    continue;
                                }
                                String t = word.optString("word", "");
                                if (!t.isBlank()) {
                                    // Normalize to alpha tokens only for overlap consistency
                                    tokens.addAll(TokenizerUtil.tokenize(t));
                                }
                            }
                        }
                    }
                }
            }
            if (!tokens.isEmpty()) {
                return List.copyOf(tokens);
            }
            // Fallback to tokenizing text if no words available
            String text = extractText(json);
            return TokenizerUtil.tokenize(text);
        } catch (org.json.JSONException e) {
            return List.of();
        }
    }
}
