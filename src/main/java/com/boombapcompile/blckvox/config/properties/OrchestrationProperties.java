package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for STT orchestration.
 */
@Validated
@ConfigurationProperties(prefix = "stt.orchestration")
public record OrchestrationProperties(

        @DefaultValue("VOSK")
        @NotNull
        PrimaryEngine primaryEngine,

        // Silence gap threshold in milliseconds. If time between transcriptions exceeds this value,
        // a newline is prepended to the next transcription for better paragraph formatting.
        // Set to 0 to disable automatic paragraph breaks.
        @DefaultValue("1000")
        @Min(0)
        int silenceGapMs,

        // RMS amplitude threshold for silence detection (0-32767 for 16-bit PCM).
        // Audio with max 20ms window RMS below this value is considered silent and skipped.
        // Lower = more sensitive (captures quieter speech). Default: 200.
        @DefaultValue("200")
        @Min(0)
        int silenceThreshold,

        // Maximum recording duration in seconds. If a recording session exceeds this limit,
        // it is automatically cancelled. This is a safety net against lost stop events.
        // Set to 0 to disable the timeout. Default: 120 seconds.
        @DefaultValue("120")
        @Min(0)
        int maxRecordingDurationSeconds
) {

    public enum PrimaryEngine { VOSK, WHISPER }

    public PrimaryEngine getPrimaryEngine() {
        return primaryEngine;
    }

    public int getSilenceGapMs() {
        return silenceGapMs;
    }

    public int getSilenceThreshold() {
        return silenceThreshold;
    }

    public int getMaxRecordingDurationSeconds() {
        return maxRecordingDurationSeconds;
    }
}
