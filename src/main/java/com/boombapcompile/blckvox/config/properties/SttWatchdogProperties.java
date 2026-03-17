package com.boombapcompile.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the STT engine watchdog (Task 2.7).
 */
@ConfigurationProperties(prefix = "stt.watchdog")
@Validated
public record SttWatchdogProperties(

        // Enable/disable watchdog globally.
        @DefaultValue("true")
        boolean enabled,

        // Sliding window size for restart budget, in minutes.
        @DefaultValue("60")
        @Positive(message = "Window minutes must be positive")
        int windowMinutes,

        // Maximum restarts permitted per engine within the window.
        @DefaultValue("3")
        @Positive(message = "Max restarts per window must be positive")
        int maxRestartsPerWindow,

        // Cooldown minutes after disabling an engine before attempting re-enable.
        @DefaultValue("10")
        @Positive(message = "Cooldown minutes must be positive")
        int cooldownMinutes,

        // Optional lightweight probe enabled (not used by default).
        @DefaultValue("false")
        boolean probeEnabled,

        // Health summary log interval in milliseconds.
        @DefaultValue("60000")
        @Positive(message = "Health summary interval must be positive")
        long healthSummaryIntervalMillis,

        // Average confidence below this threshold triggers blacklisting (0.0-1.0).
        @DefaultValue("0.3")
        @Min(value = 0, message = "Confidence blacklist threshold must be >= 0")
        @Max(value = 1, message = "Confidence blacklist threshold must be <= 1")
        double confidenceBlacklistThreshold,

        // Number of recent confidence scores to average for blacklisting.
        @DefaultValue("10")
        @Positive(message = "Confidence window size must be positive")
        int confidenceWindowSize,

        // Minimum samples required before evaluating confidence trend.
        @DefaultValue("5")
        @Positive(message = "Confidence min samples must be positive")
        int confidenceMinSamples,

        // Base delay in ms for exponential backoff between restart attempts (0 = no backoff).
        @DefaultValue("1000")
        @Min(value = 0, message = "Backoff base delay must be >= 0")
        long backoffBaseDelayMs,

        // Multiplier for exponential backoff (delay = base * multiplier^(attempts-1)).
        @DefaultValue("2.0")
        @Min(value = 1, message = "Backoff multiplier must be >= 1")
        double backoffMultiplier,

        // Maximum backoff delay in ms (cap).
        @DefaultValue("60000")
        @Positive(message = "Backoff max delay must be positive")
        long backoffMaxDelayMs
) {

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isProbeEnabled() {
        return probeEnabled;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public int getMaxRestartsPerWindow() {
        return maxRestartsPerWindow;
    }

    public int getCooldownMinutes() {
        return cooldownMinutes;
    }

    public long getHealthSummaryIntervalMillis() {
        return healthSummaryIntervalMillis;
    }

    public double getConfidenceBlacklistThreshold() {
        return confidenceBlacklistThreshold;
    }

    public int getConfidenceWindowSize() {
        return confidenceWindowSize;
    }

    public int getConfidenceMinSamples() {
        return confidenceMinSamples;
    }

    public long getBackoffBaseDelayMs() {
        return backoffBaseDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getBackoffMaxDelayMs() {
        return backoffMaxDelayMs;
    }
}
