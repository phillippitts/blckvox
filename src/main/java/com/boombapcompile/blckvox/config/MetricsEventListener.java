package com.boombapcompile.blckvox.config;

import com.boombapcompile.blckvox.service.fallback.event.TypingFallbackEvent;
import com.boombapcompile.blckvox.service.orchestration.CaptureStateMachine;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.EngineRecoveredEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Listens for Spring application events and records Micrometer metrics.
 *
 * <p>This listener provides observability for engine health, typing outcomes,
 * and capture state without modifying existing service classes.
 */
@Component
public class MetricsEventListener {

    private final MeterRegistry registry;
    private final CaptureStateMachine captureStateMachine;

    public MetricsEventListener(MeterRegistry registry, CaptureStateMachine captureStateMachine) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.captureStateMachine = Objects.requireNonNull(captureStateMachine, "captureStateMachine");
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("blckvox.capture.active", captureStateMachine, sm -> sm.isActive() ? 1.0 : 0.0)
                .description("Whether audio capture is currently active (1=active, 0=idle)")
                .register(registry);
    }

    @EventListener
    public void onEngineFailure(EngineFailureEvent event) {
        Counter.builder("blckvox.engine.failure")
                .tag("engine", event.engine())
                .description("STT engine failure count")
                .register(registry)
                .increment();
    }

    @EventListener
    public void onEngineRecovered(EngineRecoveredEvent event) {
        Counter.builder("blckvox.engine.restart")
                .tag("engine", event.engine())
                .description("STT engine successful restart count")
                .register(registry)
                .increment();
    }

    @EventListener
    public void onTypingFallback(TypingFallbackEvent event) {
        Counter.builder("blckvox.typing.fallback")
                .tag("tier", event.tier())
                .description("Typing adapter fallback count")
                .register(registry)
                .increment();
    }

    @EventListener
    public void onTranscriptionCompleted(TranscriptionCompletedEvent event) {
        if (!event.result().isFailure()) {
            Counter.builder("blckvox.typing.count")
                    .tag("engine", event.engineUsed())
                    .description("Successful transcriptions delivered for typing")
                    .register(registry)
                    .increment();
        }
        double confidence = event.result().confidence();
        if (confidence >= 0) {
            io.micrometer.core.instrument.DistributionSummary
                    .builder("blckvox.reconciliation.confidence")
                    .tag("engine", event.engineUsed())
                    .description("Transcription confidence scores")
                    .register(registry)
                    .record(confidence);
        }
    }
}
