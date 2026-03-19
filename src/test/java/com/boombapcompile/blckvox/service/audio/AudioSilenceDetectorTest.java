package com.boombapcompile.blckvox.service.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioSilenceDetectorTest {

    private static final int SAMPLE_RATE = 16000;
    private static final int SILENCE_GAP_MS = 500;

    private final AudioSilenceDetector detector = new AudioSilenceDetector();

    // --- Guard clauses ---

    @Test
    void shouldReturnEmptyForNullPcm() {
        assertThat(detector.detectSilenceBoundaries(null, SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyPcm() {
        assertThat(detector.detectSilenceBoundaries(new byte[0], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForSingleBytePcm() {
        assertThat(detector.detectSilenceBoundaries(new byte[1], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(detector.detectSilenceBoundaries(pcm, 0, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(detector.detectSilenceBoundaries(pcm, -100, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, 0))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, -16000))
                .isEmpty();
    }

    // --- Core detection ---

    @Test
    void shouldDetectTrailingSilenceBoundary() {
        // Pure silence longer than threshold → boundary at end
        byte[] pcm = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));
        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        assertThat(boundaries.getFirst()).isEqualTo(pcm.length);
    }

    @Test
    void shouldReturnEmptyForContinuousLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(1000));
        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectSilenceExceedingThreshold() {
        // loud + silence (> threshold) + loud
        byte[] loud1 = generateLoud(durationToSamples(300));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 200));
        byte[] loud2 = generateLoud(durationToSamples(300));
        byte[] pcm = concat(loud1, silence, loud2);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
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

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectMultipleSilenceGaps() {
        byte[] loud = generateLoud(durationToSamples(200));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));

        // loud + silence + loud + silence + loud
        byte[] pcm = concat(loud, silence, loud, silence, loud);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(2);
    }

    @Test
    void shouldRespectCustomSilenceThreshold() {
        // Generate moderate amplitude that is below default threshold (800) but above a low threshold
        byte[] moderate = generateModerate(durationToSamples(1000));

        // With default threshold (800), moderate audio is "silence" → boundary detected
        List<Integer> withDefault = detector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 800);
        assertThat(withDefault).hasSize(1);

        // With low threshold (100), moderate audio is "loud" → no boundary
        List<Integer> withLow = detector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 100);
        assertThat(withLow).isEmpty();
    }

    // --- Max-window RMS: Guard clauses ---

    @Test
    void maxWindowRmsShouldReturnZeroForNull() {
        assertThat(detector.calculateMaxWindowRMS(null)).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldReturnZeroForEmpty() {
        assertThat(detector.calculateMaxWindowRMS(new byte[0])).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldReturnZeroForSingleByte() {
        assertThat(detector.calculateMaxWindowRMS(new byte[1])).isEqualTo(0);
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForNull() {
        assertThat(detector.isSilentMaxWindow(null, 200)).isTrue();
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForEmpty() {
        assertThat(detector.isSilentMaxWindow(new byte[0], 200)).isTrue();
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForSingleByte() {
        assertThat(detector.isSilentMaxWindow(new byte[1], 200)).isTrue();
    }

    // --- Max-window RMS: Core detection ---

    @Test
    void maxWindowRmsShouldBeZeroForPureSilence() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(detector.calculateMaxWindowRMS(pcm)).isEqualTo(0);
    }

    @Test
    void maxWindowRmsShouldBeHighForPureLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(detector.calculateMaxWindowRMS(pcm)).isGreaterThan(9000);
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

        assertThat(detector.isSilentMaxWindow(pcm, 200)).isFalse();
        // Overall RMS is diluted — verify the old method would miss it
        assertThat(detector.isSilent(pcm, 200)).isTrue();
    }

    @Test
    void maxWindowRmsShouldDetectSpeechInLongRecordingWithDilution() {
        // 2s silence + 0.5s moderate speech + 2s silence
        // Max-window detects speech, overall RMS does not
        byte[] silence1 = generateSilence(durationToSamples(2000));
        byte[] speech = generateModerate(durationToSamples(500));
        byte[] silence2 = generateSilence(durationToSamples(2000));
        byte[] pcm = concat(silence1, speech, silence2);

        assertThat(detector.isSilentMaxWindow(pcm, 200)).isFalse();
        assertThat(detector.calculateMaxWindowRMS(pcm)).isGreaterThan(200);
        // Overall RMS is heavily diluted
        assertThat(detector.calculateOverallRMS(pcm)).isLessThan(200);
    }

    @Test
    void isSilentMaxWindowShouldRespectCustomThreshold() {
        // Moderate audio (RMS ~500): not silent at threshold 200, silent at threshold 800
        byte[] pcm = generateModerate(durationToSamples(500));

        assertThat(detector.isSilentMaxWindow(pcm, 200)).isFalse();
        assertThat(detector.isSilentMaxWindow(pcm, 800)).isTrue();
    }

    @Test
    void maxWindowRmsShouldHandleBufferSmallerThanOneWindow() {
        // Buffer smaller than 20ms (320 samples = 640 bytes) → graceful fallback to full-buffer RMS
        int samplesFor10ms = durationToSamples(10); // 160 samples = 320 bytes
        byte[] pcm = generateLoud(samplesFor10ms);

        // Should not throw and should return a reasonable value
        double maxRms = detector.calculateMaxWindowRMS(pcm);
        assertThat(maxRms).isGreaterThan(0);
        // Fallback should equal overall RMS
        assertThat(maxRms).isEqualTo(detector.calculateOverallRMS(pcm));
    }

    @Test
    void isSilentMaxWindowShouldReturnTrueForPureSilence() {
        byte[] pcm = generateSilence(durationToSamples(1000));
        assertThat(detector.isSilentMaxWindow(pcm, 200)).isTrue();
    }

    // --- isSilent() basic tests ---

    @Test
    void isSilentReturnsTrueForNull() {
        assertThat(detector.isSilent(null, 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForEmpty() {
        assertThat(detector.isSilent(new byte[0], 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForSingleByte() {
        assertThat(detector.isSilent(new byte[1], 800)).isTrue();
    }

    @Test
    void isSilentReturnsTrueForSilentAudio() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(detector.isSilent(pcm, 800)).isTrue();
    }

    @Test
    void isSilentReturnsFalseForLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(detector.isSilent(pcm, 800)).isFalse();
    }

    // --- calculateOverallRMS() basic tests ---

    @Test
    void overallRmsReturnsZeroForNull() {
        assertThat(detector.calculateOverallRMS(null)).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForEmpty() {
        assertThat(detector.calculateOverallRMS(new byte[0])).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForSingleByte() {
        assertThat(detector.calculateOverallRMS(new byte[1])).isEqualTo(0);
    }

    @Test
    void overallRmsReturnsZeroForSilence() {
        byte[] pcm = generateSilence(durationToSamples(500));
        assertThat(detector.calculateOverallRMS(pcm)).isEqualTo(0);
    }

    @Test
    void overallRmsIsHighForLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(500));
        assertThat(detector.calculateOverallRMS(pcm)).isGreaterThan(9000);
    }

    @Test
    void calculateRMSWithAllZeroAudio() {
        byte[] zeros = new byte[640]; // 20ms at 16kHz
        assertThat(detector.calculateOverallRMS(zeros)).isEqualTo(0.0);
    }

    @Test
    void calculateRMSWithSingleSample() {
        // 2 bytes = 1 sample
        byte[] single = new byte[]{(byte) 0x00, (byte) 0x40}; // sample value = 16384
        double rms = detector.calculateOverallRMS(single);
        assertThat(rms).isGreaterThan(0);
    }

    @Test
    void detectSilenceBoundariesWithAudioShorterThanWindow() {
        // 4 bytes = 2 samples, much shorter than one 20ms window (640 bytes at 16kHz)
        byte[] tiny = new byte[]{0, 0, 0, 0};
        List<Integer> boundaries = detector.detectSilenceBoundaries(tiny, 500, SAMPLE_RATE);
        // Audio is shorter than window — no windows to analyze
        assertThat(boundaries).isEmpty();
    }

    @Test
    void calculateMaxWindowRMSWithBufferSmallerThanWindow() {
        // 4 bytes — smaller than one 20ms window
        byte[] tiny = new byte[]{0, 0, 0, 0};
        double rms = detector.calculateMaxWindowRMS(tiny);
        assertThat(rms).isEqualTo(0.0);
    }

    @Test
    void detectSilenceBoundariesTrailingSilenceShorterThanGap() {
        // Build audio: loud (500ms) + short silence (200ms, less than 500ms gap)
        // Trailing silence should NOT be reported as a boundary
        int loudSamples = durationToSamples(500);
        int shortSilenceSamples = durationToSamples(200);
        byte[] audio = concat(generateLoud(loudSamples), generateSilence(shortSilenceSamples));
        List<Integer> boundaries = detector.detectSilenceBoundaries(audio, 500, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void calculateRMSWithOddByteCount() {
        // 3 bytes = 1 complete sample (2 bytes) + 1 leftover byte
        // The loop processes only complete 2-byte samples
        byte[] odd = new byte[]{0, 0, 42};
        double rms = detector.calculateOverallRMS(odd);
        assertThat(rms).isEqualTo(0.0); // single zero sample
    }

    @Test
    void detectSilenceBoundariesWithZeroGapMs() {
        byte[] audio = generateSilence(durationToSamples(100));
        List<Integer> boundaries = detector.detectSilenceBoundaries(audio, 0, SAMPLE_RATE);
        assertThat(boundaries).isEmpty(); // silenceGapMs <= 0 returns empty
    }

    @Test
    void detectSilenceBoundariesWithZeroSampleRate() {
        byte[] audio = generateSilence(durationToSamples(100));
        List<Integer> boundaries = detector.detectSilenceBoundaries(audio, 500, 0);
        assertThat(boundaries).isEmpty(); // sampleRate <= 0 returns empty
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void isSilentReturnsFalseAtExactThreshold() {
        // RMS = threshold exactly → rms < threshold is false → NOT silent
        // Kills < to <= mutant on L54
        byte[] pcm = generateUniform(320, (short) 800);
        assertThat(detector.isSilent(pcm, 800)).isFalse();
    }

    @Test
    void isSilentReturnsTrueJustBelowThreshold() {
        // RMS = 799 < 800 → IS silent
        byte[] pcm = generateUniform(320, (short) 799);
        assertThat(detector.isSilent(pcm, 800)).isTrue();
    }

    @Test
    void isSilentMaxWindowReturnsFalseAtExactThreshold() {
        // Max window RMS = threshold → < threshold is false → NOT silent
        // Kills < to <= on L95
        // Need >= 1 full 20ms window = 640 bytes = 320 samples
        byte[] pcm = generateUniform(320, (short) 800);
        assertThat(detector.isSilentMaxWindow(pcm, 800)).isFalse();
    }

    @Test
    void calculateRmsKnownValueVerifiesLittleEndianDecoding() {
        // {0x01, 0x00} = sample 1, RMS=1.0
        // {0x00, 0x01} = sample 256, RMS=256.0
        // Kills shift mutants on L166
        byte[] low = new byte[]{0x01, 0x00};
        assertThat(detector.calculateOverallRMS(low)).isEqualTo(1.0);

        byte[] high = new byte[]{0x00, 0x01};
        assertThat(detector.calculateOverallRMS(high)).isEqualTo(256.0);
    }

    @Test
    void calculateRmsStepVerification() {
        // Two samples: {0x00, 0x40} = 16384 and {0xFF, 0x7F} = 32767
        // RMS = sqrt((16384^2 + 32767^2) / 2) = sqrt((268435456 + 1073676289) / 2) = sqrt(671055872.5)
        byte[] buf = new byte[]{0x00, 0x40, (byte) 0xFF, 0x7F};
        double rms = detector.calculateOverallRMS(buf);
        double expected = Math.sqrt((16384.0 * 16384.0 + 32767.0 * 32767.0) / 2.0);
        assertThat(rms).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void detectSilenceBoundariesAtExactMinSilenceBytes() {
        // Silence region exactly minSilenceBytes long → boundary SHOULD be detected (>= on L136)
        // minSilenceBytes for 500ms at 16kHz = (16000*500/1000)*2 = 16000 bytes
        // windowBytes for 20ms = 640 bytes → need silence = 16000/640 = 25 windows
        int windowBytes = 640;
        int minSilenceWindows = 25; // 16000/640 = 25
        byte[] loud = generateLoud(durationToSamples(300));
        byte[] silence = new byte[windowBytes * minSilenceWindows]; // exactly minSilenceBytes
        byte[] loud2 = generateLoud(durationToSamples(300));
        byte[] pcm = concat(loud, silence, loud2);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
    }

    @Test
    void detectSilenceBoundariesSilenceOneByteBelowMinimum() {
        // Silence one window short of minSilenceBytes → no boundary
        int windowBytes = 640;
        int minSilenceWindows = 25;
        byte[] loud = generateLoud(durationToSamples(300));
        byte[] silence = new byte[windowBytes * (minSilenceWindows - 1)]; // one window less
        byte[] loud2 = generateLoud(durationToSamples(300));
        byte[] pcm = concat(loud, silence, loud2);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void detectSilenceBoundariesTrailingSilenceExactlyMinimum() {
        // loud + silence exactly at min → trailing boundary detected (>= on L147)
        int windowBytes = 640;
        int minSilenceWindows = 25;
        byte[] loud = generateLoud(durationToSamples(300));
        byte[] silence = new byte[windowBytes * minSilenceWindows];
        byte[] pcm = concat(loud, silence);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        assertThat(boundaries.getFirst()).isEqualTo(pcm.length);
    }

    @Test
    void detectSilenceBoundariesSilenceAtExactRmsThreshold() {
        // Per-window RMS exactly at threshold → NOT silence (rms < threshold is false)
        // Kills < to <= on L126
        int windowBytes = 640; // 320 samples per window
        int totalWindows = 30;
        byte[] pcm = generateUniform(320 * totalWindows, (short) 800);

        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE, 800);
        // Since no window is silent, no boundaries should be detected
        assertThat(boundaries).isEmpty();
    }

    @Test
    void calculateMaxWindowRmsTracksPeakNotAverage() {
        // 2 silent windows + 1 loud window → max = loud window RMS
        // Kills > to >= on L82
        int windowBytes = 640; // 320 samples
        byte[] silent1 = new byte[windowBytes];
        byte[] silent2 = new byte[windowBytes];
        byte[] loudWindow = generateLoud(320);
        byte[] pcm = concat(silent1, silent2, loudWindow);

        double maxRms = detector.calculateMaxWindowRMS(pcm);
        double loudRms = detector.calculateOverallRMS(loudWindow);
        assertThat(maxRms).isEqualTo(loudRms);
        assertThat(maxRms).isGreaterThan(0);
    }

    @Test
    void detectSilenceBoundariesReturnsEmptyForAllSilentBufferBelowGap() {
        // Pure silence shorter than silenceGapMs → trailing silence < minSilenceBytes → empty
        int windowBytes = 640;
        int minSilenceWindows = 25;
        byte[] pcm = new byte[windowBytes * (minSilenceWindows - 1)]; // one window less than minimum
        List<Integer> boundaries = detector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
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

    /** Generates PCM16LE with all samples set to the given amplitude (constant signal). */
    private byte[] generateUniform(int sampleCount, short amplitude) {
        byte[] pcm = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            pcm[i * 2] = (byte) (amplitude & 0xFF);
            pcm[i * 2 + 1] = (byte) (amplitude >> 8);
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
