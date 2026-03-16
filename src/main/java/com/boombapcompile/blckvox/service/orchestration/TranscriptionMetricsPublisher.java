package com.boombapcompile.blckvox.service.orchestration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes transcription metrics recording for orchestration workflows.
 *
 * <p>When constructed with a {@link MeterRegistry}, records transcription duration,
 * success/failure counts, and processing ratio via Micrometer JMX metrics.
 * The {@link #NOOP} instance retains no-op behavior for tests and builders.
 *
 * @since 1.1
 * @see HotkeyRecordingAdapter
 */
@Component
public final class TranscriptionMetricsPublisher {

    /**
     * Singleton no-op instance for builder defaults and tests.
     */
    public static final TranscriptionMetricsPublisher NOOP = new TranscriptionMetricsPublisher();

    private final MeterRegistry registry;

    /**
     * No-op constructor for {@link #NOOP} singleton and backward-compatible tests.
     */
    TranscriptionMetricsPublisher() {
        this.registry = null;
    }

    /**
     * Constructs a publisher backed by the given Micrometer registry.
     *
     * @param registry the meter registry for recording metrics
     */
    public TranscriptionMetricsPublisher(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a successful transcription with duration and engine metadata.
     */
    public void recordSuccess(String engineName, long durationNanos, String strategy) {
        if (registry == null) {
            return;
        }
        Timer.builder("blckvox.transcription.duration")
                .tag("engine", engineName)
                .tag("strategy", strategy != null ? strategy : "single")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        Counter.builder("blckvox.transcription.count")
                .tag("engine", engineName)
                .tag("result", "success")
                .register(registry)
                .increment();
    }

    /**
     * Records a transcription failure with engine and error category.
     */
    public void recordFailure(String engineName, String errorCategory) {
        if (registry == null) {
            return;
        }
        Counter.builder("blckvox.transcription.count")
                .tag("engine", engineName)
                .tag("result", "failure")
                .tag("error", errorCategory)
                .register(registry)
                .increment();
    }

    /**
     * Records the processing-time-to-audio-duration ratio.
     */
    public void recordProcessingRatio(String engineName, double ratio) {
        if (registry == null) {
            return;
        }
        DistributionSummary.builder("blckvox.processing.ratio")
                .tag("engine", engineName)
                .register(registry)
                .record(ratio);
    }

    /**
     * Checks if metrics tracking is enabled.
     *
     * @return true if a MeterRegistry is available
     */
    public boolean isEnabled() {
        return registry != null;
    }
}
