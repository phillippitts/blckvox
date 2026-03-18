package com.boombapcompile.blckvox.service.audio;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects silence regions in PCM16LE audio using Voice Activity Detection (VAD).
 *
 * <p>This component analyzes raw PCM audio buffers to identify continuous silence regions
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
@Component
public class AudioSilenceDetector implements SilenceDetector {

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

    @Override
    public boolean isSilent(byte[] pcmData, int silenceThreshold) {
        if (pcmData == null || pcmData.length < 2) {
            return true;
        }
        double rms = calculateRMS(pcmData, 0, pcmData.length);
        return rms < silenceThreshold;
    }

    @Override
    public double calculateOverallRMS(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0;
        }
        return calculateRMS(pcmData, 0, pcmData.length);
    }

    @Override
    public double calculateMaxWindowRMS(byte[] pcmData) {
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

    @Override
    public boolean isSilentMaxWindow(byte[] pcmData, int silenceThreshold) {
        if (pcmData == null || pcmData.length < 2) {
            return true;
        }
        return calculateMaxWindowRMS(pcmData) < silenceThreshold;
    }

    @Override
    public List<Integer> detectSilenceBoundaries(byte[] pcmData, int silenceGapMs, int sampleRate) {
        return detectSilenceBoundaries(pcmData, silenceGapMs, sampleRate, DEFAULT_BOUNDARY_THRESHOLD);
    }

    @Override
    public List<Integer> detectSilenceBoundaries(
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

    private int calculateWindowBytes(int durationMs, int sampleRate) {
        int samples = (sampleRate * durationMs) / 1000;
        return samples * 2; // 2 bytes per sample (16-bit PCM)
    }

    private double calculateRMS(byte[] pcmData, int offset, int length) {
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
