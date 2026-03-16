package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultCaptureOrchestrator}.
 */
class DefaultCaptureOrchestratorTest {

    @Test
    void shouldStartCaptureWhenNotActive() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        UUID sessionId = orchestrator.startCapture();

        // Assert
        assertThat(sessionId).isNotNull();
        assertThat(orchestrator.isCapturing()).isTrue();
        assertThat(captureService.startSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullWhenStartCaptureCalledWhileActive() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID firstSession = orchestrator.startCapture();
        assertThat(firstSession).isNotNull();

        // Act
        UUID secondSession = orchestrator.startCapture();

        // Assert
        assertThat(secondSession).isNull();
        assertThat(orchestrator.isCapturing()).isTrue();
        assertThat(captureService.startSessionCallCount).isEqualTo(1); // Pre-check prevents opening audio hardware
        assertThat(captureService.cancelSessionCallCount).isEqualTo(0); // No session created to cancel
    }

    @Test
    void shouldStopCaptureAndReturnAudioData() {
        // Arrange
        byte[] expectedAudio = new byte[]{1, 2, 3, 4};
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        captureService.setAudioData(expectedAudio);
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        byte[] audio = orchestrator.stopCapture(sessionId);

        // Assert
        assertThat(audio).isEqualTo(expectedAudio);
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.stopSessionCallCount).isEqualTo(1);
        assertThat(captureService.readAllCallCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullWhenStopCaptureCalledWithNullSessionId() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        byte[] audio = orchestrator.stopCapture(null);

        // Assert
        assertThat(audio).isNull();
        assertThat(captureService.stopSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldReturnNullWhenStopCaptureCalledWithMismatchedSessionId() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID activeSession = orchestrator.startCapture();
        assertThat(activeSession).isNotNull();

        UUID differentSessionId = UUID.randomUUID();

        // Act
        byte[] audio = orchestrator.stopCapture(differentSessionId);

        // Assert
        assertThat(audio).isNull();
        assertThat(orchestrator.isCapturing()).isTrue(); // Still capturing with original session
        assertThat(captureService.stopSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldCancelSessionWhenAudioReadFails() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        captureService.setReadAllException(new RuntimeException("Audio device error"));
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        byte[] audio = orchestrator.stopCapture(sessionId);

        // Assert
        assertThat(audio).isNull();
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldCancelSpecifiedSession() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        orchestrator.cancelCapture(sessionId);

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldCancelActiveSessionWhenCancelCalledWithNull() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        orchestrator.cancelCapture(null);

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldHandleCancelWhenNoActiveSession() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        orchestrator.cancelCapture(UUID.randomUUID());

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldHandleCancelWithNullSessionIdWhenNoActiveSession() {
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // No active session, cancel with null → cancelCapture returns null → no captureService.cancel
        orchestrator.cancelCapture(null);

        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldReturnNullAudioWhenReadAllReturnsNull() {
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        captureService.setAudioData(null); // readAll will return null
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        byte[] audio = orchestrator.stopCapture(sessionId);

        // pcm == null branch in readCapturedAudio LOG
        assertThat(audio).isNull();
        assertThat(orchestrator.isCapturing()).isFalse();
    }

    @Test
    void shouldHandleRaceConditionWhenAnotherSessionStartsBetweenChecks() {
        // This tests the race condition path at lines 60-63:
        // isActive() returns false, captureService.startSession() succeeds,
        // but stateMachine.startCapture() returns false because another session registered.
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        final UUID[] returnedSessionId = new UUID[1];

        // AudioCaptureService that registers a competing session as a side effect
        AudioCaptureService racingCapture = new AudioCaptureService() {
            int cancelCount = 0;
            @Override
            public UUID startSession() {
                UUID id = UUID.randomUUID();
                returnedSessionId[0] = id;
                // Simulate a race: another thread registered a session on the state machine
                stateMachine.startCapture(UUID.randomUUID());
                return id;
            }
            @Override public void stopSession(UUID sessionId) {}
            @Override public void cancelSession(UUID sessionId) { cancelCount++; }
            @Override public byte[] readAll(UUID sessionId) { return new byte[0]; }
        };

        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(racingCapture, stateMachine);

        // startCapture() calls: isActive()=false, startSession(), startCapture()=false (race), cancelSession()
        UUID result = orchestrator.startCapture();
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnCapturingStateFromStateMachine() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Assert - initially not capturing
        assertThat(orchestrator.isCapturing()).isFalse();

        // Start capture
        UUID sessionId = orchestrator.startCapture();
        assertThat(orchestrator.isCapturing()).isTrue();

        // Stop capture
        orchestrator.stopCapture(sessionId);
        assertThat(orchestrator.isCapturing()).isFalse();
    }

    /**
     * Fake implementation of AudioCaptureService for testing.
     */
    static class FakeAudioCaptureService implements AudioCaptureService {
        int startSessionCallCount = 0;
        int stopSessionCallCount = 0;
        int cancelSessionCallCount = 0;
        int readAllCallCount = 0;

        private byte[] audioData = new byte[0];
        private RuntimeException readAllException;
        private final Map<UUID, byte[]> sessions = new HashMap<>();

        void setAudioData(byte[] data) {
            this.audioData = data;
        }

        void setReadAllException(RuntimeException exception) {
            this.readAllException = exception;
        }

        @Override
        public UUID startSession() {
            startSessionCallCount++;
            UUID sessionId = UUID.randomUUID();
            sessions.put(sessionId, audioData);
            return sessionId;
        }

        @Override
        public void stopSession(UUID sessionId) {
            stopSessionCallCount++;
        }

        @Override
        public void cancelSession(UUID sessionId) {
            cancelSessionCallCount++;
            sessions.remove(sessionId);
        }

        @Override
        public byte[] readAll(UUID sessionId) {
            readAllCallCount++;
            if (readAllException != null) {
                throw readAllException;
            }
            return sessions.get(sessionId);
        }
    }
}
