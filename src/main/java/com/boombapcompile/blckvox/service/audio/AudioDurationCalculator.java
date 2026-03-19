package com.boombapcompile.blckvox.service.audio;

/**
 * Utility to compute audio duration from PCM byte length.
 *
 * <p>For the default project format (16kHz, 16-bit, mono):
 * {@code durationMs = pcmBytes / (16000 * 2 * 1) * 1000}
 */
public final class AudioDurationCalculator {

    private AudioDurationCalculator() {}

    /**
     * Computes audio duration in milliseconds from PCM byte length.
     *
     * @param pcmByteLength total PCM data length in bytes
     * @param sampleRate    samples per second (e.g., 16000)
     * @param bytesPerSample bytes per sample (e.g., 2 for 16-bit)
     * @param channels      number of audio channels (e.g., 1 for mono)
     * @return duration in milliseconds
     * @throws IllegalArgumentException if any parameter is non-positive
     */
    public static double durationMs(int pcmByteLength, int sampleRate, int bytesPerSample, int channels) {
        if (sampleRate <= 0 || bytesPerSample <= 0 || channels <= 0) {
            throw new IllegalArgumentException("sampleRate, bytesPerSample, and channels must be positive");
        }
        if (pcmByteLength <= 0) {
            return 0.0;
        }
        return (double) pcmByteLength / ((long) sampleRate * bytesPerSample * channels) * 1000.0;
    }

    /**
     * Computes audio duration using the project's default format (16kHz, 16-bit, mono).
     *
     * @param pcmByteLength total PCM data length in bytes
     * @return duration in milliseconds
     */
    public static double durationMs(int pcmByteLength) {
        return durationMs(pcmByteLength,
                AudioFormat.REQUIRED_SAMPLE_RATE,
                AudioFormat.REQUIRED_BITS_PER_SAMPLE / 8,
                AudioFormat.REQUIRED_CHANNELS);
    }
}
