package com.boombapcompile.blckvox.service.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects silence regions in PCM16LE audio using Voice Activity Detection (VAD).
 *
 * <p>This utility analyzes raw PCM audio buffers to identify continuous silence regions
 * that exceed a configurable threshold. It uses RMS (Root Mean Square) amplitude analysis
 * in sliding time windows to distinguish speech from silence.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Divide audio into small time windows (e.g., 20ms chunks)</li>
 *   <li>Calculate RMS amplitude for each window</li>
 *   <li>Compare RMS to silence threshold (configurable, typically 500-1000 for 16-bit PCM)</li>
 *   <li>Track continuous silence regions</li>
 *   <li>Return boundaries of silence gaps exceeding the minimum duration</li>
 * </ol>
 *
 * <p><b>Use Case:</b> Enables pause-based paragraph detection for Vosk transcription,
 * which doesn't provide timestamp data like Whisper does.
 *
 * <p><b>Audio Format:</b> Expects PCM16LE mono audio (16-bit signed little-endian, mono channel).
 *
 * @since 1.0
 */
public final class AudioSilenceDetector {

    private AudioSilenceDetector() {
        // Utility class
    }

    /**
     * Default RMS amplitude threshold for silence boundary detection within audio.
     * Values below this are considered silence in 16-bit PCM audio.
     * This is distinct from the configurable {@code stt.orchestration.silence-threshold}
     * property (default 200), which controls full-buffer silence skip in the orchestrator.
     */
    private static final int DEFAULT_BOUNDARY_THRESHOLD = 800;

    /**
     * Default window size for RMS analysis (milliseconds).
     * Smaller = more granular detection but more CPU; larger = smoother but less precise.
     */
    private static final int DEFAULT_WINDOW_MS = 20;

    /**
     * Checks if the entire PCM audio buffer is effectively silent using a custom threshold.
     *
     * @param pcmData PCM16LE mono audio buffer (16-bit signed little-endian)
     * @param silenceThreshold RMS amplitude threshold (0-32767 for 16-bit PCM)
     * @return {@code true} if the audio is below the silence threshold
     */
    public static boolean isSilent(byte[] pcmData, int silenceThreshold) {
        if (pcmData == null || pcmData.length < 2) {
            return true;
        }
        double rms = calculateRMS(pcmData, 0, pcmData.length);
        return rms < silenceThreshold;
    }

    /**
     * Returns the overall RMS amplitude of the audio buffer for diagnostic logging.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @return RMS amplitude, or 0 if input is null/empty
     */
    public static double calculateOverallRMS(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0;
        }
        return calculateRMS(pcmData, 0, pcmData.length);
    }

    /**
     * Returns the highest RMS amplitude found in any non-overlapping 20ms window of the buffer.
     *
     * <p>Unlike {@link #calculateOverallRMS(byte[])}, which averages energy across the entire
     * buffer (diluting speech energy with leading/trailing silence), this method detects
     * speech even when it occupies a small fraction of the recording.
     *
     * <p>Falls back to full-buffer RMS for buffers smaller than one 20ms window.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @return maximum window RMS amplitude, or 0 if input is null/empty
     */
    public static double calculateMaxWindowRMS(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0;
        }

        int windowBytes = (AudioFormat.REQUIRED_SAMPLE_RATE * DEFAULT_WINDOW_MS / 1000) * 2;

        // Fallback for buffers smaller than one window
        if (pcmData.length < windowBytes) {
            return calculateRMS(pcmData, 0, pcmData.length);
        }

        double maxRms = 0;
        int pos = 0;
        while (pos + windowBytes <= pcmData.length) {
            double rms = calculateRMS(pcmData, pos, windowBytes);
            if (rms > maxRms) {
                maxRms = rms;
            }
            pos += windowBytes;
        }
        return maxRms;
    }

    /**
     * Checks if the audio buffer is effectively silent using max-window RMS analysis.
     *
     * <p>Returns {@code true} only if <em>every</em> 20ms window in the buffer has RMS below
     * the threshold. This avoids false-positive silence detection when speech is surrounded
     * by silence (e.g., short utterance in a long recording).
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceThreshold RMS amplitude threshold (0-32767 for 16-bit PCM)
     * @return {@code true} if the audio is below the silence threshold in all windows
     */
    public static boolean isSilentMaxWindow(byte[] pcmData, int silenceThreshold) {
        if (pcmData == null || pcmData.length < 2) {
            return true;
        }
        return calculateMaxWindowRMS(pcmData) < silenceThreshold;
    }

    /**
     * Detects silence regions in PCM audio and returns their byte positions.
     *
     * <p>Analyzes the audio buffer using RMS amplitude in sliding windows to identify
     * continuous silence regions exceeding the minimum gap duration.
     *
     * @param pcmData PCM16LE mono audio buffer (16-bit signed little-endian)
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @return list of byte positions marking the END of each detected silence region
     *         (empty list if no silence detected or invalid input)
     */
    public static List<Integer> detectSilenceBoundaries(byte[] pcmData, int silenceGapMs, int sampleRate) {
        return detectSilenceBoundaries(pcmData, silenceGapMs, sampleRate, DEFAULT_BOUNDARY_THRESHOLD);
    }

    /**
     * Detects silence regions in PCM audio with custom threshold.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @param silenceThreshold RMS amplitude threshold for silence (0-32767 for 16-bit PCM)
     * @return list of byte positions marking the END of each detected silence region
     */
    public static List<Integer> detectSilenceBoundaries(
            byte[] pcmData,
            int silenceGapMs,
            int sampleRate,
            int silenceThreshold) {

        List<Integer> boundaries = new ArrayList<>();

        if (pcmData == null || pcmData.length < 2 || silenceGapMs <= 0 || sampleRate <= 0) {
            return boundaries;
        }

        // Calculate window size in bytes (16-bit = 2 bytes per sample)
        int windowBytes = calculateWindowBytes(DEFAULT_WINDOW_MS, sampleRate);
        int minSilenceBytes = calculateWindowBytes(silenceGapMs, sampleRate);

        int silenceStart = -1;
        int pos = 0;

        while (pos + windowBytes <= pcmData.length) {
            double rms = calculateRMS(pcmData, pos, windowBytes);

            if (rms < silenceThreshold) {
                // Silence detected
                if (silenceStart == -1) {
                    silenceStart = pos; // Start tracking silence
                }
            } else {
                // Speech detected
                if (silenceStart != -1) {
                    // Check if silence duration exceeds threshold
                    int silenceBytes = pos - silenceStart;
                    if (silenceBytes >= minSilenceBytes) {
                        boundaries.add(pos); // Mark end of silence region
                    }
                    silenceStart = -1; // Reset
                }
            }

            pos += windowBytes;
        }

        // Handle trailing silence
        if (silenceStart != -1 && (pcmData.length - silenceStart) >= minSilenceBytes) {
            boundaries.add(pcmData.length);
        }

        return boundaries;
    }

    /**
     * Calculates the number of bytes corresponding to a time duration.
     *
     * @param durationMs time duration in milliseconds
     * @param sampleRate audio sample rate in Hz
     * @return number of bytes (aligned to 2-byte samples)
     */
    private static int calculateWindowBytes(int durationMs, int sampleRate) {
        int samples = (sampleRate * durationMs) / 1000;
        return samples * 2; // 2 bytes per sample (16-bit PCM)
    }

    /**
     * Calculates RMS (Root Mean Square) amplitude for a PCM audio window.
     *
     * <p>RMS provides a measure of the audio signal's energy. Higher RMS indicates
     * louder audio (speech), lower RMS indicates quiet audio (silence).
     *
     * @param pcmData PCM16LE audio buffer
     * @param offset starting byte position
     * @param length number of bytes to analyze (must be even)
     * @return RMS amplitude (0-32767 range for 16-bit PCM)
     */
    private static double calculateRMS(byte[] pcmData, int offset, int length) {
        long sumSquares = 0;
        int sampleCount = 0;

        // Process 16-bit samples (2 bytes each, little-endian)
        for (int i = offset; i + 1 < offset + length && i + 1 < pcmData.length; i += 2) {
            // Convert 2 bytes to 16-bit signed sample (little-endian)
            int sample = (pcmData[i] & 0xFF) | (pcmData[i + 1] << 8);
            sumSquares += (long) sample * sample;
            sampleCount++;
        }

        if (sampleCount == 0) {
            return 0;
        }

        return Math.sqrt((double) sumSquares / sampleCount);
    }
}
