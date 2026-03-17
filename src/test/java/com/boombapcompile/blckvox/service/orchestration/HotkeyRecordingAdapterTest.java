package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.testutil.FakeAudioCaptureService;
import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.audio.capture.CaptureErrorEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPressedEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyReleasedEvent;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyRecordingAdapterTest {

    @Test
    void usesPrimaryWhenHealthy() {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200); // 100ms of PCM
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Act
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        boolean published = events.stream().anyMatch(e -> e instanceof TranscriptionCompletedEvent);
        assertThat(published).isTrue();
        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent).findFirst().orElseThrow();
        assertThat(evt.result().engineName()).isEqualTo("vosk");
        assertThat(evt.engineUsed()).isEqualTo("vosk");
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    void fallsBackWhenPrimaryDisabled() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(false, true); // vosk disabled, whisper enabled
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent).findFirst().orElseThrow();
        assertThat(evt.result().engineName()).isEqualTo("whisper");
        assertThat(evt.engineUsed()).isEqualTo("whisper");
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    void publishesEmptyResultWhenBothDisabled() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(false, false);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .map(e -> (TranscriptionCompletedEvent) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected TranscriptionCompletedEvent"));
        assertThat(evt.result().text()).isEmpty();
    }

    @Test
    void handlesCaptureError() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        ApplicationEventPublisher pub = e -> { };
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Start session, then receive capture error
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onCaptureError(new CaptureErrorEvent("Microphone permission denied", Instant.now()));

        // Verify session was canceled
        assertThat(cap.canceledSession).isNotNull();
        assertThat(cap.canceledSession).isEqualTo(cap.id);
    }

    @Test
    void shouldHandleRapidTogglePresses() {
        // Test that rapid hotkey presses don't create race conditions
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        CaptureStateMachine csm = new CaptureStateMachine();
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(csm)
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Simulate: press (start), press again (ignored - already active), release (complete)
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID firstSession = cap.id;
        assertThat(firstSession).isNotNull();
        assertThat(csm.isActive()).isTrue();

        // Second press should be ignored since session is already active
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThat(cap.id).isEqualTo(firstSession); // Same session, not a new one
        assertThat(csm.isActive()).isTrue();

        // Release completes the transcription
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
        assertThat(csm.isActive()).isFalse();

        // Verify only one transcription completed
        long completedCount = events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .count();
        assertThat(completedCount).isEqualTo(1);
    }

    @org.junit.jupiter.api.Disabled("TimingCoordinator removed - pause detection now done by STT engines")
    @Test
    void shouldPrependParagraphBreakAfterSilenceGap() throws InterruptedException {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK,
                100, // 100ms silence gap threshold
                200
        );
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;

        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Act: First transcription - no paragraph break
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt1 = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt1.result().text()).isEqualTo("text"); // No newline prepended
        events.clear();

        // Wait longer than silence gap threshold
        Thread.sleep(150);

        // Second transcription - should have paragraph break prepended
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt2 = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt2.result().text()).startsWith("\n"); // Newline prepended
        assertThat(evt2.result().text()).isEqualTo("\ntext");
    }

    @org.junit.jupiter.api.Disabled("TimingCoordinator removed - pause detection now done by STT engines")
    @Test
    void shouldNotAddDoubleNewlineWhenTextAlreadyStartsWithNewline() throws InterruptedException {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = createVoskEngineWithNewlinePrefix();
        List<Object> events = new ArrayList<>();

        HotkeyRecordingAdapter adapter = buildAdapterForParagraphBreakTest(cap, vosk, events);

        // First transcription
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
        events.clear();

        // Wait for silence gap
        Thread.sleep(150);

        // Second transcription - engine returns text starting with newline
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();

        // Should NOT have double newline
        assertThat(evt.result().text()).isEqualTo("\nalready-has-newline");
        assertThat(evt.result().text()).doesNotStartWith("\n\n");
    }

    private SttEngine createVoskEngineWithNewlinePrefix() {
        return new SttEngine() {
            @Override
            public void initialize() { }
            @Override
            public TranscriptionResult transcribe(byte[] audioData) {
                return TranscriptionResult.of("\nalready-has-newline", 1.0, "vosk");
            }
            @Override
            public String getEngineName() {
                return "vosk";
            }
            @Override
            public boolean isHealthy() {
                return true;
            }
            @Override
            public void close() { }
        };
    }

    private HotkeyRecordingAdapter buildAdapterForParagraphBreakTest(
            FakeCapture cap, SttEngine vosk, List<Object> events) {
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK,
                100,
                200
        );
        ApplicationEventPublisher pub = events::add;

        return HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();
    }

    // ---- Test fakes ----

    static class FakeCapture implements AudioCaptureService {
        byte[] pcm;
        UUID id;
        UUID canceledSession;
        @Override
        public UUID startSession() {
            id = UUID.randomUUID();
            return id;
        }
        @Override
        public void stopSession(UUID sessionId) {
            /* no-op */
        }
        @Override
        public void cancelSession(UUID sessionId) {
            canceledSession = sessionId;
        }
        @Override
        public byte[] readAll(UUID sessionId) {
            return pcm;
        }
    }

    static class StubEngine implements SttEngine {
        final String name;
        StubEngine(String n) {
            this.name = n;
        }
        @Override
        public void initialize() {
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("text", 1.0, name);
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
        }
    }

    static class FakeWatchdog extends SttEngineWatchdog {
        final boolean voskEnabled;
        final boolean whisperEnabled;
        FakeWatchdog(boolean voskEnabled, boolean whisperEnabled) {
            super(java.util.List.of(),
                    new SttWatchdogProperties(
                            true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L),
                    e -> { });
            this.voskEnabled = voskEnabled;
            this.whisperEnabled = whisperEnabled;
        }
        @Override
        public boolean isEngineEnabled(String engine) {
            return switch (engine) {
                case "vosk" -> voskEnabled;
                case "whisper" -> whisperEnabled;
                default -> false;
            };
        }
    }

    @Test
    void toggleModePressStartsThenStops() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        CaptureStateMachine csm = new CaptureStateMachine();
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(toggleHotkeyProps())
                .publisher(pub)
                .captureStateMachine(csm)
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // First press starts recording
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThat(csm.isActive()).isTrue();

        // Second press stops and transcribes
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThat(csm.isActive()).isFalse();

        boolean published = events.stream().anyMatch(e -> e instanceof TranscriptionCompletedEvent);
        assertThat(published).isTrue();
    }

    @Test
    void toggleModeIgnoresReleaseEvent() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = FakeAudioCaptureService.generateNonSilentPcm(3200);
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        CaptureStateMachine csm = new CaptureStateMachine();
        HotkeyRecordingAdapter adapter = HotkeyRecordingAdapterBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(toggleHotkeyProps())
                .publisher(pub)
                .captureStateMachine(csm)
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Start via toggle press
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThat(csm.isActive()).isTrue();

        // Release should be ignored in toggle mode — session still active
        adapter.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
        assertThat(csm.isActive()).isTrue();

        // No transcription completed yet
        long completedCount = events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent).count();
        assertThat(completedCount).isZero();
    }

    @Test
    void toggleModeReturnsFalseWhenRecordingServiceFails() {
        // Directly construct adapter with a RecordingService whose toggleRecording returns false.
        // This covers HotkeyRecordingAdapter line 54: LOG.warn("Toggle recording failed")
        RecordingService failingRecording = new RecordingService() {
            @Override
            public boolean startRecording() {
                return false;
            }
            @Override
            public boolean stopRecording() {
                return false;
            }
            @Override
            public void cancelRecording() { }
            @Override
            public ApplicationState getState() {
                return ApplicationState.IDLE;
            }
            @Override
            public boolean isRecording() {
                return false;
            }
            @Override
            public boolean toggleRecording() {
                return false;
            }
        };

        HotkeyRecordingAdapter adapter = new HotkeyRecordingAdapter(failingRecording, toggleHotkeyProps());
        // Should not throw — just logs a warning
        adapter.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
    }

    private static HotkeyProperties fakeHotkeyProps() {
        return new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, List.of("META"), List.of(), false);
    }

    private static HotkeyProperties toggleHotkeyProps() {
        return new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, List.of("META"), List.of(), true);
    }
}
