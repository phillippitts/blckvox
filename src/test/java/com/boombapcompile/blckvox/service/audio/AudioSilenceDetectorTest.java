package com.boombapcompile.blckvox.service.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioSilenceDetectorTest {

    private static final int SAMPLE_RATE = 16000;
    private static final int SILENCE_GAP_MS = 500;

    // --- Guard clauses ---

    @Test
    void shouldReturnEmptyForNullPcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(null, SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyPcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(new byte[0], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForSingleBytePcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(new byte[1], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, 0, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, -100, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, 0))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, -16000))
                .isEmpty();
    }

    // --- Core detection ---

    @Test
    void shouldDetectTrailingSilenceBoundary() {
        // Pure silence longer than threshold → boundary at end
        byte[] pcm = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        assertThat(boundaries.getFirst()).isEqualTo(pcm.length);
    }

    @Test
    void shouldReturnEmptyForContinuousLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(1000));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectSilenceExceedingThreshold() {
        // loud + silence (> threshold) + loud
        byte[] loud1 = generateLoud(durationToSamples(300));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 200));
        byte[] loud2 = generateLoud(durationToSamples(300));
        byte[] pcm = concat(loud1, silence, loud2);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        // Boundary should be after silence region, before loud2
        assertThat(boundaries.getFirst()).isGreaterThan(loud1.length)
                .isLessThanOrEqualTo(loud1.length + silence.length);
    }

    @Test
    void shouldNotDetectSilenceBelowThreshold() {
        // loud + silence (< threshold) + loud
        byte[] loud1 = generateLoud(durationToSamples(500));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS - 100));
        byte[] loud2 = generateLoud(durationToSamples(500));
        byte[] pcm = concat(loud1, silence, loud2);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectMultipleSilenceGaps() {
        byte[] loud = generateLoud(durationToSamples(200));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));

        // loud + silence + loud + silence + loud
        byte[] pcm = concat(loud, silence, loud, silence, loud);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(2);
    }

    @Test
    void shouldRespectCustomSilenceThreshold() {
        // Generate moderate amplitude that is below default threshold (800) but above a low threshold
        byte[] moderate = generateModerate(durationToSamples(1000));

        // With default threshold (800), moderate audio is "silence" → boundary detected
        List<Integer> withDefault = AudioSilenceDetector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 800);
        assertThat(withDefault).hasSize(1);

        // With low threshold (100), moderate audio is "loud" → no boundary
        List<Integer> withLow = AudioSilenceDetector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 100);
        assertThat(withLow).isEmpty();
    }

    // --- Max-window RMS: Guard clauses ---

    @Test
    void maxWindowRmsShouldReturnZeroForNull() {
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(null)).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldReturnZeroForEmpty() {
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(new byte[0])).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldReturnZeroForSingleByte() {
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(new byte[1])).isEqualTo(0);
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForNull() {
        assertThat(AudioSilenceDetector.isSilentMaxWindow(null, 200)).isTrue();
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForEmpty() {
        assertThat(AudioSilenceDetector.isSilentMaxWindow(new byte[0], 200)).isTrue();
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForSingleByte() {
        assertThat(AudioSilenceDetector.isSilentMaxWindow(new byte[1], 200)).isTrue();
    }

    // --- Max-window RMS: Core detection ---

    @Test
    void maxWindowRmsShouldBeZeroForPureSilence() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(pcm)).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldBeHighForPureLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(pcm)).isGreaterThan(9000);
    }

    @Test
    void maxWindowRmsShouldDetectSpeechSurroundedBySilence() {
        // Core regression test: 800ms silence + 200ms moderate speech + 800ms silence
        // Moderate speech (amplitude ~500) has per-window RMS ~500, well above threshold 200.
        // But overall-buffer RMS is diluted by surrounding silence to ~167, below threshold 200.
        // isSilentMaxWindow should return false (speech detected)
        // isSilent (overall) would return true (speech diluted by silence)
        byte[] silence1 = generateSilence(durationToSamples(800));
        byte[] speech = generateModerate(durationToSamples(200));
        byte[] silence2 = generateSilence(durationToSamples(800));
        byte[] pcm = concat(silence1, speech, silence2);

        assertThat(AudioSilenceDetector.isSilentMaxWindow(pcm, 200)).isFalse();
        // Overall RMS is diluted — verify the old method would miss it
        assertThat(AudioSilenceDetector.isSilent(pcm, 200)).isTrue();
    }

    @Test
    void maxWindowRmsShouldDetectSpeechInLongRecordingWithDilution() {
        // 2s silence + 0.5s moderate speech + 2s silence
        // Max-window detects speech, overall RMS does not
        byte[] silence1 = generateSilence(durationToSamples(2000));
        byte[] speech = generateModerate(durationToSamples(500));
        byte[] silence2 = generateSilence(durationToSamples(2000));
        byte[] pcm = concat(silence1, speech, silence2);

        assertThat(AudioSilenceDetector.isSilentMaxWindow(pcm, 200)).isFalse();
        assertThat(AudioSilenceDetector.calculateMaxWindowRMS(pcm)).isGreaterThan(200);
        // Overall RMS is heavily diluted
        assertThat(AudioSilenceDetector.calculateOverallRMS(pcm)).isLessThan(200);
    }

    @Test
    void isSilentMaxWindowShouldRespectCustomThreshold() {
        // Moderate audio (RMS ~500): not silent at threshold 200, silent at threshold 800
        byte[] pcm = generateModerate(durationToSamples(500));

        assertThat(AudioSilenceDetector.isSilentMaxWindow(pcm, 200)).isFalse();
        assertThat(AudioSilenceDetector.isSilentMaxWindow(pcm, 800)).isTrue();
    }

    @Test
    void maxWindowRmsShouldHandleBufferSmallerThanOneWindow() {
        // Buffer smaller than 20ms (320 samples = 640 bytes) → graceful fallback to full-buffer RMS
        int samplesFor10ms = durationToSamples(10); // 160 samples = 320 bytes
        byte[] pcm = generateLoud(samplesFor10ms);

        // Should not throw and should return a reasonable value
        double maxRms = AudioSilenceDetector.calculateMaxWindowRMS(pcm);
        assertThat(maxRms).isGreaterThan(0);
        // Fallback should equal overall RMS
        assertThat(maxRms).isEqualTo(AudioSilenceDetector.calculateOverallRMS(pcm));
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForPureSilence() {
        byte[] pcm = generateSilence(durationToSamples(1000));
        assertThat(AudioSilenceDetector.isSilentMaxWindow(pcm, 200)).isTrue();
    }

    // --- isSilent() basic tests ---

    @Test
    void isSilentReturnsTrueForNull() {
        assertThat(AudioSilenceDetector.isSilent(null, 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForEmpty() {
        assertThat(AudioSilenceDetector.isSilent(new byte[0], 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForSingleByte() {
        assertThat(AudioSilenceDetector.isSilent(new byte[1], 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForSilentAudio() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(AudioSilenceDetector.isSilent(pcm, 800)).isTrue();
    }

    @Test
    void isSilentReturnsFalseForLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(AudioSilenceDetector.isSilent(pcm, 800)).isFalse();
    }

    // --- calculateOverallRMS() basic tests ---

    @Test
    void overallRmsReturnsZeroForNull() {
        assertThat(AudioSilenceDetector.calculateOverallRMS(null)).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForEmpty() {
        assertThat(AudioSilenceDetector.calculateOverallRMS(new byte[0])).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForSingleByte() {
        assertThat(AudioSilenceDetector.calculateOverallRMS(new byte[1])).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForSilence() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(AudioSilenceDetector.calculateOverallRMS(pcm)).isEqualTo(0);
    }

    @Test
    void overallRmsIsHighForLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(AudioSilenceDetector.calculateOverallRMS(pcm)).isGreaterThan(9000);
    }

    @Test
    void calculateRMSWithAllZeroAudio() {
        byte[] zeros = new byte[640]; // 20ms at 16kHz
        assertThat(AudioSilenceDetector.calculateOverallRMS(zeros)).isEqualTo(0.0);
    }

    @Test
    void calculateRMSWithSingleSample() {
        // 2 bytes = 1 sample
        byte[] single = new byte[]{(byte) 0x00, (byte) 0x40}; // sample value = 16384
        double rms = AudioSilenceDetector.calculateOverallRMS(single);
        assertThat(rms).isGreaterThan(0);
    }

    @Test
    void detectSilenceBoundariesWithAudioShorterThanWindow() {
        // 4 bytes = 2 samples, much shorter than one 20ms window (640 bytes at 16kHz)
        byte[] tiny = new byte[]{0, 0, 0, 0};
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(tiny, 500, SAMPLE_RATE);
        // Audio is shorter than window — no windows to analyze
        assertThat(boundaries).isEmpty();
    }

    @Test
    void calculateMaxWindowRMSWithBufferSmallerThanWindow() {
        // 4 bytes — smaller than one 20ms window
        byte[] tiny = new byte[]{0, 0, 0, 0};
        double rms = AudioSilenceDetector.calculateMaxWindowRMS(tiny);
        assertThat(rms).isEqualTo(0.0);
    }

    @Test
    void detectSilenceBoundariesTrailingSilenceShorterThanGap() {
        // Build audio: loud (500ms) + short silence (200ms, less than 500ms gap)
        // Trailing silence should NOT be reported as a boundary
        int loudSamples = durationToSamples(500);
        int shortSilenceSamples = durationToSamples(200);
        byte[] audio = concat(generateLoud(loudSamples), generateSilence(shortSilenceSamples));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(audio, 500, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void calculateRMSWithOddByteCount() {
        // 3 bytes = 1 complete sample (2 bytes) + 1 leftover byte
        // The loop processes only complete 2-byte samples
        byte[] odd = new byte[]{0, 0, 42};
        double rms = AudioSilenceDetector.calculateOverallRMS(odd);
        assertThat(rms).isEqualTo(0.0); // single zero sample
    }

    @Test
    void detectSilenceBoundariesWithZeroGapMs() {
        byte[] audio = generateSilence(durationToSamples(100));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(audio, 0, SAMPLE_RATE);
        assertThat(boundaries).isEmpty(); // silenceGapMs <= 0 returns empty
    }

    @Test
    void detectSilenceBoundariesWithZeroSampleRate() {
        byte[] audio = generateSilence(durationToSamples(100));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(audio, 500, 0);
        assertThat(boundaries).isEmpty(); // sampleRate <= 0 returns empty
    }

    // --- Helpers ---

    /** Converts duration in ms to number of 16-bit samples at SAMPLE_RATE. */
    private int durationToSamples(int durationMs) {
        return (SAMPLE_RATE * durationMs) / 1000;
    }

    /** Generates PCM16LE silence (all zeros). */
    private byte[] generateSilence(int sampleCount) {
        return new byte[sampleCount * 2]; // 2 bytes per sample, all zeros
    }

    /** Generates PCM16LE loud audio (amplitude ~10000). */
    private byte[] generateLoud(int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        short amplitude = 10000;
        for (int i = 0; i < sampleCount; i++) {
            // Alternate positive/negative to create audio-like signal
            short val = (i % 2 == 0) ? amplitude : (short) -amplitude;
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) (val >> 8);
        }
        return pcm;
    }

    /** Generates PCM16LE moderate amplitude audio (~500). */
    private byte[] generateModerate(int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        short amplitude = 500;
        for (int i = 0; i < sampleCount; i++) {
            short val = (i % 2 == 0) ? amplitude : (short) -amplitude;
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) (val >> 8);
        }
        return pcm;
    }

    private byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) {
            total += a.length;
        }
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
