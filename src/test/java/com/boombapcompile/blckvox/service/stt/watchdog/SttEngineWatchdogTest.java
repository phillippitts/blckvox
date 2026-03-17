package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SttEngineWatchdogTest {

    @Test
    void shouldRestartEngineOnFailureWithinBudget() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "test fail",
                null, java.util.Map.of()));

        Optional<EngineRecoveredEvent> recovery = publishedEvents.stream()
                .filter(e -> e instanceof EngineRecoveredEvent)
                .map(e -> (EngineRecoveredEvent) e)
                .findFirst();

        recovery.ifPresent(watchdog::onRecovered);

        assertThat(engine.closedCount).isEqualTo(1);
        assertThat(engine.initCount).isEqualTo(1);
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldDisableEngineAfterExceedingBudget() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 1, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("whisper");
        ApplicationEventPublisher publisher = (event) -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First failure -> restart allowed
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail1",
                null, java.util.Map.of()));
        // Second failure within window -> should disable
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail2",
                null, java.util.Map.of()));

        assertThat(watchdog.getState("whisper")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
        int initAfterDisable = engine.initCount;
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail3",
                null, java.util.Map.of()));
        assertThat(engine.initCount).isEqualTo(initAfterDisable);
    }

    @Test
    void shouldNotBlacklistBeforeMinSamplesReached() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 4 low-confidence events (below min samples of 5)
        for (int i = 0; i < 4; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).isEmpty();
    }

    @Test
    void shouldBlacklistEngineWhenConfidenceBelowThreshold() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 5 low-confidence events (meets min samples, avg 0.1 < threshold 0.3)
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).hasSize(1);
    }

    @Test
    void shouldNotBlacklistWhenConfidenceAboveThreshold() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 10; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.8, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).isEmpty();
    }

    @Test
    void shouldPruneConfidenceWindowToConfiguredSize() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 5, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 8; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.9, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).hasSize(5);
    }

    @Test
    void shouldClearConfidenceWindowOnRecovery() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        watchdog.onRecovered(new EngineRecoveredEvent("vosk", Instant.now()));

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).isEmpty();
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldIgnoreConfidenceForUnknownEngines() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // "reconciled" is not a tracked engine — should be silently ignored
        TranscriptionResult result = TranscriptionResult.of("text", 0.1, "reconciled");
        watchdog.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(result, Instant.now(), "reconciled"));

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void initializeEnginesCallsInitializeOnAllEngines() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine vosk = new RecordingEngine("vosk");
        RecordingEngine whisper = new RecordingEngine("whisper");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(vosk, whisper), props, publisher);
        watchdog.initializeEngines();

        assertThat(vosk.initCount).isEqualTo(1);
        assertThat(whisper.initCount).isEqualTo(1);
    }

    @Test
    void initializeEnginesDisablesEngineOnFailure() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        FailingEngine failing = new FailingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(failing), props, publisher);
        watchdog.initializeEngines();

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
    }

    @Test
    void initializeEnginesDefersSecondaryWhenPrimaryConfigured() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine vosk = new RecordingEngine("vosk");
        RecordingEngine whisper = new RecordingEngine("whisper");
        ApplicationEventPublisher publisher = event -> { };
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(vosk, whisper), props, publisher, orchProps);
        watchdog.initializeEngines();

        // Primary (vosk) should be eagerly initialized
        assertThat(vosk.initCount).isEqualTo(1);
        // Secondary (whisper) should be deferred
        assertThat(whisper.initCount).isEqualTo(0);
    }

    @Test
    void initializeOnDemandInitializesLazyEngine() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine vosk = new RecordingEngine("vosk");
        // Use an engine that starts unhealthy until initialize() is called
        LazyRecordingEngine whisper = new LazyRecordingEngine("whisper");
        ApplicationEventPublisher publisher = event -> { };
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(vosk, whisper), props, publisher, orchProps);
        watchdog.initializeEngines();

        // Whisper not yet initialized
        assertThat(whisper.initCount).isEqualTo(0);
        assertThat(whisper.isHealthy()).isFalse();

        // Lazy init on demand
        boolean result = watchdog.initializeOnDemand("whisper");
        assertThat(result).isTrue();
        assertThat(whisper.initCount).isEqualTo(1);
        assertThat(whisper.isHealthy()).isTrue();
    }

    @Test
    void initializeOnDemandReturnsFalseForUnknownEngine() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        assertThat(watchdog.initializeOnDemand("nonexistent")).isFalse();
    }

    @Test
    void logHealthSummaryShouldNotThrow() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        assertThatCode(watchdog::logHealthSummary).doesNotThrowAnyException();
    }

    @Test
    void isEngineEnabledReturnsTrueForHealthyEngine() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        assertThat(watchdog.isEngineEnabled("vosk")).isTrue();
    }

    @Test
    void isEngineEnabledReturnsFalseWhenDisabled() {
        // budget = 1 restart allowed; two failures will exhaust and disable
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 10, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First failure -> restart (uses the single budget slot)
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail1",
                null, java.util.Map.of()));
        // Second failure -> budget exceeded -> DISABLED
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail2",
                null, java.util.Map.of()));

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
        assertThat(watchdog.isEngineEnabled("vosk")).isFalse();
    }

    @Test
    void onFailureIgnoresUnknownEngine() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Fire failure for an engine that is not tracked
        watchdog.onFailure(new EngineFailureEvent("unknown-engine", Instant.now(), "fail",
                null, java.util.Map.of()));

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(engine.initCount).isZero();
        assertThat(engine.closedCount).isZero();
    }

    @Test
    void safetyModeForceEnablesBestEngine() {
        // Both engines get budget = 1, so two failures disable each
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 10, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine vosk = new RecordingEngine("vosk");
        RecordingEngine whisper = new RecordingEngine("whisper");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(vosk, whisper), props, publisher);

        // Record some confidence so the monitor has data to pick the best engine.
        // vosk gets higher confidence than whisper.
        for (int i = 0; i < 5; i++) {
            TranscriptionResult voskResult = TranscriptionResult.of("text", 0.8, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(voskResult, Instant.now(), "vosk"));
            TranscriptionResult whisperResult = TranscriptionResult.of("text", 0.5, "whisper");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(whisperResult, Instant.now(), "whisper"));
        }

        // Disable vosk: first failure -> restart (uses budget), second -> DISABLED
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail1",
                null, java.util.Map.of()));
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail2",
                null, java.util.Map.of()));

        // Disable whisper: first failure -> restart (uses budget), second -> DISABLED
        // This should trigger safety mode since both are now disabled
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail1",
                null, java.util.Map.of()));
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail2",
                null, java.util.Map.of()));

        // Safety mode should force-enable the best engine (vosk, with higher confidence)
        // After safety mode, at least one engine should be enabled again
        boolean anyEnabled = watchdog.isEngineEnabled("vosk") || watchdog.isEngineEnabled("whisper");
        assertThat(anyEnabled).isTrue();

        // The best engine (vosk, avg 0.8 > whisper avg 0.5) should have been force-enabled
        assertThat(watchdog.isEngineEnabled("vosk")).isTrue();
    }

    @Test
    void onRecoveredIgnoresUnknownEngine() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Should not throw for unknown engine
        assertThatCode(() ->
                watchdog.onRecovered(new EngineRecoveredEvent("unknown-engine", Instant.now()))
        ).doesNotThrowAnyException();

        // Existing engine state unchanged
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void restartFailureKeepsEngineDegraded() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        // Engine that closes fine but fails to re-initialize
        InitFailingEngine engine = new InitFailingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail",
                null, java.util.Map.of()));

        // Restart attempted but initialize() fails → stays DEGRADED
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
        // No recovery event should have been published
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineRecoveredEvent).isEmpty();
    }

    @Test
    void singleEngineSkipsSafetyMode() {
        // Safety mode requires size >= 2; with a single engine it should not trigger
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 10, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Disable the engine
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail1",
                null, java.util.Map.of()));
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail2",
                null, java.util.Map.of()));

        // Engine should be DISABLED — safety mode not triggered for single engine
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
        assertThat(watchdog.isEngineEnabled("vosk")).isFalse();
    }

    @Test
    void closeThrowsDuringRestartContinuesToInitialize() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        CloseFailingEngine engine = new CloseFailingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail",
                null, java.util.Map.of()));

        // close() throws but initialize() succeeds → recovery event published
        assertThat(engine.initCount).isEqualTo(1);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineRecoveredEvent).hasSize(1);
    }

    // --- Event record tests ---

    @Test
    void engineFailureEventNullAtDefaultsToNow() {
        var event = new EngineFailureEvent("vosk", null, "fail", null, Map.of());
        assertThat(event.at()).isNotNull();
        assertThat(event.engine()).isEqualTo("vosk");
    }

    @Test
    void engineFailureEventPreservesNonNullAt() {
        Instant custom = Instant.parse("2025-01-01T00:00:00Z");
        var event = new EngineFailureEvent("vosk", custom, "fail", null, Map.of());
        assertThat(event.at()).isEqualTo(custom);
    }

    @Test
    void engineRecoveredEventNullAtDefaultsToNow() {
        var event = new EngineRecoveredEvent("vosk", null);
        assertThat(event.at()).isNotNull();
        assertThat(event.engine()).isEqualTo("vosk");
    }

    @Test
    void engineRecoveredEventPreservesNonNullAt() {
        Instant custom = Instant.parse("2025-01-01T00:00:00Z");
        var event = new EngineRecoveredEvent("vosk", custom);
        assertThat(event.at()).isEqualTo(custom);
    }

    // --- ConfidenceMonitor edge cases ---

    @Test
    void confidenceMonitorFormattedSummaryWithData() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Record some confidence data
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.8, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        String summary = watchdog.getConfidenceMonitor().formattedSummary("vosk");
        assertThat(summary).contains("conf=");
        assertThat(summary).contains("/5");
    }

    @Test
    void confidenceMonitorRecordForUntrackedEngineReturnsNull() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        // "unknown" was never registered
        assertThat(monitor.record("unknown", 0.5)).isNull();
    }

    @Test
    void confidenceMonitorAverageConfidenceForEmptyReturnsZero() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        assertThat(monitor.averageConfidence("vosk")).isEqualTo(0.0);
    }

    @Test
    void confidenceMonitorAverageConfidenceForUnknownReturnsZero() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.averageConfidence("unknown")).isEqualTo(0.0);
    }

    @Test
    void confidenceMonitorFormattedSummaryForUnknownReturnsEmpty() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.formattedSummary("unknown")).isEmpty();
    }

    @Test
    void confidenceMonitorFormattedSummaryForEmptyWindowReturnsEmpty() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        assertThat(monitor.formattedSummary("vosk")).isEmpty();
    }

    @Test
    void confidenceMonitorClearOnRecoveryForUnknownDoesNotThrow() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThatCode(() -> monitor.clearOnRecovery("unknown")).doesNotThrowAnyException();
    }

    @Test
    void packagePrivateConstructorRegistersEngines() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RestartBudgetTracker budget = new RestartBudgetTracker(props);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);

        RecordingEngine engine = new RecordingEngine("vosk");
        budget.register("vosk");
        monitor.register("vosk");

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), event -> { }, budget, monitor);

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(watchdog.isEngineEnabled("vosk")).isTrue();
    }

    @Test
    void safetyModeWithFailingRestartKeepsEngineDegraded() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 10, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        // Use InitFailingEngine so tryRestart() fails in safety mode
        InitFailingEngine vosk = new InitFailingEngine("vosk");
        RecordingEngine whisper = new RecordingEngine("whisper");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(vosk, whisper), props, publisher);

        // Record confidence data so safety mode can pick a best engine
        for (int i = 0; i < 5; i++) {
            watchdog.onTranscriptionCompleted(new TranscriptionCompletedEvent(
                    TranscriptionResult.of("text", 0.8, "vosk"), Instant.now(), "vosk"));
            watchdog.onTranscriptionCompleted(new TranscriptionCompletedEvent(
                    TranscriptionResult.of("text", 0.5, "whisper"), Instant.now(), "whisper"));
        }

        // Disable vosk (two failures with budget=1)
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "f1", null, Map.of()));
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "f2", null, Map.of()));

        // Disable whisper → triggers safety mode
        // Safety mode picks vosk (higher confidence) but tryRestart fails (InitFailingEngine)
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "f1", null, Map.of()));
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "f2", null, Map.of()));

        // vosk should be DEGRADED (safety mode set it to DEGRADED, tryRestart failed)
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
        // No recovery event for vosk since restart failed
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof EngineRecoveredEvent re && re.engine().equals("vosk"))
                .count()).isZero();
    }

    @Test
    void isEngineEnabledReturnsFalseWhenInCooldownButNotDisabledState() {
        // Tests the `!budgetTracker.isInCooldown(engine)` returning false
        // when state is not DISABLED (i.e., line 113 returns false)
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RestartBudgetTracker budget = new RestartBudgetTracker(props);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);

        RecordingEngine engine = new RecordingEngine("vosk");
        budget.register("vosk");
        monitor.register("vosk");

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), event -> { }, budget, monitor);

        // State is HEALTHY, but manually put in cooldown via budget tracker
        budget.disable("vosk");

        // isEngineEnabled: state=HEALTHY (not DISABLED) → reaches line 113
        // budgetTracker.isInCooldown("vosk") → true → returns false
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(watchdog.isEngineEnabled("vosk")).isFalse();
    }

    @Test
    void attemptRestartSkipsWhenLockAlreadyHeld() throws InterruptedException {
        // Tests the `tryLockRestart` returning false branch (line 180)
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RestartBudgetTracker budget = new RestartBudgetTracker(props);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);

        RecordingEngine engine = new RecordingEngine("vosk");
        budget.register("vosk");
        monitor.register("vosk");

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), event -> { }, budget, monitor);

        // Hold the lock from a different thread (ReentrantLock is thread-exclusive for tryLock)
        java.util.concurrent.CountDownLatch lockAcquired = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread lockHolder = new Thread(() -> {
            budget.tryLockRestart("vosk");
            lockAcquired.countDown();
            try {
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) { }
            budget.unlockRestart("vosk");
        });
        lockHolder.start();
        lockAcquired.await(2, java.util.concurrent.TimeUnit.SECONDS);

        try {
            // Fire failure → attemptRestart → tryLockRestart returns false → early return
            watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail",
                    null, Map.of()));

            // No restart should have been attempted
            assertThat(engine.initCount).isZero();
            assertThat(engine.closedCount).isZero();
        } finally {
            release.countDown();
            lockHolder.join(3000);
        }
    }

    @Test
    void onFailureEarlyReturnWhenAlreadyDisabled() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 10, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Disable the engine
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "f1", null, Map.of()));
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "f2", null, Map.of()));
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);

        int initsBefore = engine.initCount;
        // Third failure hits the early return at isEngineEnabled check
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "f3", null, Map.of()));
        // No new restart attempts
        assertThat(engine.initCount).isEqualTo(initsBefore);
    }

    @Test
    void attemptRestartSkippedWhenInCooldown() {
        // maxRestarts=1, budget period=60s — first restart uses budget, second triggers cooldown
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 1, false, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First failure — uses the budget, restarts successfully
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail 1",
                null, Map.of()));
        // Engine should have been restarted
        assertThat(engine.initCount).isEqualTo(1);

        // Second failure — budget exhausted, engine gets disabled
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail 2",
                null, Map.of()));
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);

        // Third failure — engine is disabled, attemptRestart skipped
        int initsBefore = engine.initCount;
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail 3",
                null, Map.of()));
        assertThat(engine.initCount).isEqualTo(initsBefore); // no new init
    }

    // --- Test doubles ---

    static class InitFailingEngine implements SttEngine {
        final String name;
        int initCount = 0;
        int closedCount = 0;

        InitFailingEngine(String name) {
            this.name = name;
        }

        @Override public void initialize() {
            initCount++;
            throw new RuntimeException("init failed");
        }
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }
        @Override public String getEngineName() {
            return name;
        }
        @Override public boolean isHealthy() {
            return false;
        }
        @Override public void close() {
            closedCount++;
        }
    }

    static class CloseFailingEngine implements SttEngine {
        final String name;
        int initCount = 0;

        CloseFailingEngine(String name) {
            this.name = name;
        }

        @Override public void initialize() {
            initCount++;
        }
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }
        @Override public String getEngineName() {
            return name;
        }
        @Override public boolean isHealthy() {
            return true;
        }
        @Override public void close() {
            throw new RuntimeException("close failed");
        }
    }

    // --- Test double ---
    static class RecordingEngine implements SttEngine {
        final String name;
        int initCount = 0;
        int closedCount = 0;

        RecordingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            initCount++;
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }
        @Override
        public String getEngineName() {
            return name;
        }
        @Override
        public boolean isHealthy() {
            return true;
        }
        @Override
        public void close() {
            closedCount++;
        }
    }

    static class FailingEngine implements SttEngine {
        final String name;

        FailingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            throw new RuntimeException("Simulated initialization failure for " + name);
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            throw new RuntimeException("Engine " + name + " is not available");
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return false;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /**
     * Engine that starts unhealthy and becomes healthy only after initialize() is called.
     * Used to test lazy initialization behavior.
     */
    static class LazyRecordingEngine implements SttEngine {
        final String name;
        int initCount = 0;
        private boolean healthy = false;

        LazyRecordingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            initCount++;
            healthy = true;
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }
        @Override
        public String getEngineName() {
            return name;
        }
        @Override
        public boolean isHealthy() {
            return healthy;
        }
        @Override
        public void close() {
            healthy = false;
        }
    }

    // --- EngineHealthChangedEvent tests ---

    @Test
    void shouldPublishHealthChangedEventOnStateTransition() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Trigger failure → should transition from HEALTHY to DEGRADED
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "test fail",
                null, Map.of()));

        List<EngineHealthChangedEvent> healthEvents = publishedEvents.stream()
                .filter(e -> e instanceof EngineHealthChangedEvent)
                .map(e -> (EngineHealthChangedEvent) e)
                .toList();

        assertThat(healthEvents).isNotEmpty();
        EngineHealthChangedEvent event = healthEvents.getFirst();
        assertThat(event.engine()).isEqualTo("vosk");
        assertThat(event.previousState()).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(event.currentState()).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void shouldPublishHealthChangedEventOnRecovery() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First: cause a failure (HEALTHY → DEGRADED)
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail",
                null, Map.of()));

        // The failure causes a restart which publishes EngineRecoveredEvent.
        // Process that recovery event.
        publishedEvents.stream()
                .filter(e -> e instanceof EngineRecoveredEvent)
                .map(e -> (EngineRecoveredEvent) e)
                .findFirst()
                .ifPresent(watchdog::onRecovered);

        // Should have DEGRADED→HEALTHY transition
        List<EngineHealthChangedEvent> healthEvents = publishedEvents.stream()
                .filter(e -> e instanceof EngineHealthChangedEvent)
                .map(e -> (EngineHealthChangedEvent) e)
                .toList();

        assertThat(healthEvents).hasSizeGreaterThanOrEqualTo(2);
        EngineHealthChangedEvent recoveryEvent = healthEvents.getLast();
        assertThat(recoveryEvent.currentState()).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void attemptRestartSkippedDuringBackoff() {
        // Use high base delay so backoff is definitely active after first restart
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 600_000L, 2.0, 600_000L);
        RestartBudgetTracker budget = new RestartBudgetTracker(props);
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);

        RecordingEngine engine = new RecordingEngine("vosk");
        budget.register("vosk");
        monitor.register("vosk");

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), publisher, budget, monitor);

        // First failure — restarts successfully, sets backoff
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail1",
                null, Map.of()));
        assertThat(engine.initCount).isEqualTo(1);

        // Do NOT process the EngineRecoveredEvent (don't call onRecovered) —
        // this way clearOnRecovery is not called and backoff stays active.

        // Second failure — should be skipped due to backoff (600s base delay)
        int initsBefore = engine.initCount;
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail2",
                null, Map.of()));
        assertThat(engine.initCount).isEqualTo(initsBefore);
    }

    @Test
    void shouldSkipFailedResultsInConfidenceTracking() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 5 failed results — should NOT trigger degradation
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.failure("vosk", "engine error");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        // Confidence window should remain empty (failures skipped)
        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).isEmpty();
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldSkipSilentResultsInConfidenceTracking() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 5 silent results (empty text, confidence 1.0) — should NOT enter confidence window
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("", 1.0, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).isEmpty();
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldTrackNonEmptyLowConfidenceResults() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Non-empty text with low confidence should still be tracked
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("some text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).hasSize(5);
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
    }

    @Test
    void engineHealthChangedEventNullTimestampDefaultsToNow() {
        EngineHealthChangedEvent event = new EngineHealthChangedEvent(
                "vosk", SttEngineWatchdog.EngineState.HEALTHY,
                SttEngineWatchdog.EngineState.DEGRADED, null);
        assertThat(event.timestamp()).isNotNull();
    }
}
