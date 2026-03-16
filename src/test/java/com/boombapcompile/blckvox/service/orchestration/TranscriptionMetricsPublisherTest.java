package com.boombapcompile.blckvox.service.orchestration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TranscriptionMetricsPublisherTest {

    @Test
    void noopRecordSuccessDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordSuccess("vosk", 1000L, "single"))
                .doesNotThrowAnyException();
    }

    @Test
    void noopRecordFailureDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordFailure("vosk", "timeout"))
                .doesNotThrowAnyException();
    }

    @Test
    void noopRecordProcessingRatioDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordProcessingRatio("vosk", 0.5))
                .doesNotThrowAnyException();
    }

    @Test
    void noopIsEnabledReturnsFalse() {
        assertThat(new TranscriptionMetricsPublisher().isEnabled()).isFalse();
    }

    @Test
    void noopInstanceIsNotNull() {
        assertThat(TranscriptionMetricsPublisher.NOOP).isNotNull();
        assertThat(TranscriptionMetricsPublisher.NOOP.isEnabled()).isFalse();
    }

    @Test
    void registryBackedPublisherIsEnabled() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var publisher = new TranscriptionMetricsPublisher(registry);
        assertThat(publisher.isEnabled()).isTrue();
    }

    @Test
    void recordSuccessCreatesTimerAndCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var publisher = new TranscriptionMetricsPublisher(registry);

        publisher.recordSuccess("vosk", 500_000_000L, "overlap");

        var timer = registry.get("blckvox.transcription.duration")
                .tag("engine", "vosk").tag("strategy", "overlap").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(500.0);

        var counter = registry.get("blckvox.transcription.count")
                .tag("engine", "vosk").tag("result", "success").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordSuccessWithNullStrategyDefaultsToSingle() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var publisher = new TranscriptionMetricsPublisher(registry);

        publisher.recordSuccess("whisper", 100_000_000L, null);

        var timer = registry.get("blckvox.transcription.duration")
                .tag("strategy", "single").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordFailureCreatesCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var publisher = new TranscriptionMetricsPublisher(registry);

        publisher.recordFailure("whisper", "timeout");

        var counter = registry.get("blckvox.transcription.count")
                .tag("engine", "whisper").tag("result", "failure").tag("error", "timeout").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordProcessingRatioCreatesDistributionSummary() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var publisher = new TranscriptionMetricsPublisher(registry);

        publisher.recordProcessingRatio("vosk", 0.42);

        var summary = registry.get("blckvox.processing.ratio")
                .tag("engine", "vosk").summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.mean()).isCloseTo(0.42, org.assertj.core.data.Offset.offset(0.001));
    }
}
