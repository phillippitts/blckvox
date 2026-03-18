package com.boombapcompile.blckvox.service.audio;

import java.util.List;

/**
 * Abstraction for silence detection in PCM16LE audio buffers.
 *
 * <p>Enables injectable silence detection for testability without Mockito.
 * Test doubles can control silence/non-silence outcomes directly.
 *
 * @since 1.1
 */
public interface SilenceDetector {

    /**
     * Checks if the entire PCM audio buffer is effectively silent using a custom threshold.
     *
     * @param pcmData PCM16LE mono audio buffer (16-bit signed little-endian)
     * @param silenceThreshold RMS amplitude threshold (0-32767 for 16-bit PCM)
     * @return {@code true} if the audio is below the silence threshold
     */
    boolean isSilent(byte[] pcmData, int silenceThreshold);

    /**
     * Checks if the audio buffer is effectively silent using max-window RMS analysis.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceThreshold RMS amplitude threshold (0-32767 for 16-bit PCM)
     * @return {@code true} if the audio is below the silence threshold in all windows
     */
    boolean isSilentMaxWindow(byte[] pcmData, int silenceThreshold);

    /**
     * Returns the overall RMS amplitude of the audio buffer.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @return RMS amplitude, or 0 if input is null/empty
     */
    double calculateOverallRMS(byte[] pcmData);

    /**
     * Returns the highest RMS amplitude found in any non-overlapping 20ms window of the buffer.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @return maximum window RMS amplitude, or 0 if input is null/empty
     */
    double calculateMaxWindowRMS(byte[] pcmData);

    /**
     * Detects silence regions in PCM audio and returns their byte positions.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @return list of byte positions marking the END of each detected silence region
     */
    List<Integer> detectSilenceBoundaries(byte[] pcmData, int silenceGapMs, int sampleRate);

    /**
     * Detects silence regions in PCM audio with custom threshold.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @param silenceThreshold RMS amplitude threshold for silence
     * @return list of byte positions marking the END of each detected silence region
     */
    List<Integer> detectSilenceBoundaries(byte[] pcmData, int silenceGapMs, int sampleRate, int silenceThreshold);
}
