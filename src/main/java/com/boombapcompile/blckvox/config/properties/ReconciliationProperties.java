package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stt.reconciliation")
public record ReconciliationProperties(

        // Enable reconciled path in orchestrator.
        @DefaultValue("true")
        boolean enabled,

        // Strategy to use when enabled.
        @DefaultValue("OVERLAP")
        @NotNull
        Strategy strategy,

        // Threshold for word-overlap (Jaccard) strategy (0..1).
        @DefaultValue("0.6")
        @Min(0)
        @Max(1)
        double overlapThreshold,

        // Confidence threshold for smart reconciliation (0..1).
        // If Vosk confidence < this threshold, run Whisper too and reconcile.
        // Set to 0.0 to always run dual-engine, 1.0 to never upgrade to dual-engine.
        @DefaultValue("0.7")
        @Min(0)
        @Max(1)
        double confidenceThreshold
) {

    public enum Strategy { SIMPLE, CONFIDENCE, OVERLAP }

    public ReconciliationProperties {
        if (overlapThreshold < 0.0 || overlapThreshold > 1.0) {
            throw new IllegalArgumentException("stt.reconciliation.overlap-threshold must be in [0,1]");
        }
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("stt.reconciliation.confidence-threshold must be in [0,1]");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public double getOverlapThreshold() {
        return overlapThreshold;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
