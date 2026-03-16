package com.boombapcompile.blckvox.config;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.fallback.event.TypingFallbackEvent;
import com.boombapcompile.blckvox.service.orchestration.CaptureStateMachine;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.EngineRecoveredEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsEventListenerTest {

    private MeterRegistry registry;
    private CaptureStateMachine stateMachine;
    private MetricsEventListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        stateMachine = new CaptureStateMachine();
        listener = new MetricsEventListener(registry, stateMachine);
        listener.registerGauges();
    }

    @Test
    void captureActiveGaugeReflectsStateMachine() {
        assertThat(registry.get("blckvox.capture.active").gauge().value()).isEqualTo(0.0);
        stateMachine.startCapture(UUID.randomUUID());
        assertThat(registry.get("blckvox.capture.active").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void engineFailureIncrementsCounter() {
        var event = new EngineFailureEvent("vosk", Instant.now(), "timeout", null, Map.of());
        listener.onEngineFailure(event);
        assertThat(registry.get("blckvox.engine.failure").tag("engine", "vosk").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void engineRecoveredIncrementsCounter() {
        var event = new EngineRecoveredEvent("whisper", Instant.now());
        listener.onEngineRecovered(event);
        assertThat(registry.get("blckvox.engine.restart").tag("engine", "whisper").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void typingFallbackIncrementsCounter() {
        var event = new TypingFallbackEvent("robot", "type returned false", Instant.now());
        listener.onTypingFallback(event);
        assertThat(registry.get("blckvox.typing.fallback").tag("tier", "robot").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void transcriptionCompletedIncrementsTypingCount() {
        var result = TranscriptionResult.of("hello", 0.9, "vosk");
        var event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");
        listener.onTranscriptionCompleted(event);

        assertThat(registry.get("blckvox.typing.count").tag("engine", "vosk").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void transcriptionCompletedRecordsConfidence() {
        var result = TranscriptionResult.of("hello", 0.85, "vosk");
        var event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");
        listener.onTranscriptionCompleted(event);

        assertThat(registry.get("blckvox.reconciliation.confidence")
                .tag("engine", "vosk").summary().count()).isEqualTo(1);
        assertThat(registry.get("blckvox.reconciliation.confidence")
                .tag("engine", "vosk").summary().mean()).isEqualTo(0.85);
    }

    @Test
    void failedTranscriptionDoesNotIncrementTypingCount() {
        var result = TranscriptionResult.failure("vosk", "timeout");
        var event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");
        listener.onTranscriptionCompleted(event);

        assertThat(registry.find("blckvox.typing.count").counter()).isNull();
    }
}
