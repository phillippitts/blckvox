package com.boombapcompile.blckvox.service.orchestration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CaptureStateMachine}.
 */
class CaptureStateMachineTest {

    @Test
    void startCaptureSucceedsWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();

        boolean result = sm.startCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.isActive()).isTrue();
    }

    @Test
    void startCaptureFailsWhenAlreadyActive() {
        CaptureStateMachine sm = new CaptureStateMachine();
        sm.startCapture(UUID.randomUUID());

        boolean result = sm.startCapture(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    void stopCaptureSucceedsWithCorrectId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        boolean result = sm.stopCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.isActive()).isFalse();
    }

    @Test
    void stopCaptureFailsWithWrongId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        sm.startCapture(id1);

        boolean result = sm.stopCapture(id2);

        assertThat(result).isFalse();
        assertThat(sm.isActive()).isTrue();
    }

    @Test
    void stopCaptureFailsWhenNoActiveSession() {
        CaptureStateMachine sm = new CaptureStateMachine();

        boolean result = sm.stopCapture(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    void cancelCaptureReturnsSessionId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        UUID cancelled = sm.cancelCapture();

        assertThat(cancelled).isEqualTo(id);
        assertThat(sm.isActive()).isFalse();
    }

    @Test
    void cancelCaptureReturnsNullWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();

        UUID cancelled = sm.cancelCapture();

        assertThat(cancelled).isNull();
    }

    @Test
    void getActiveSessionReturnsNullWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();

        assertThat(sm.getActiveSession()).isNull();
    }

    @Test
    void getActiveSessionReturnsIdWhenActive() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        assertThat(sm.getActiveSession()).isEqualTo(id);
    }

    @Test
    void startCaptureRejectsNullSessionId() {
        CaptureStateMachine sm = new CaptureStateMachine();

        assertThatThrownBy(() -> sm.startCapture(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void startCaptureReturnsFalseAndDoesNotChangeActiveSession() {
        // Double start: second returns false AND session unchanged
        // Kills return value mutation + activeSession assignment mutation
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        sm.startCapture(first);

        boolean result = sm.startCapture(second);

        assertThat(result).isFalse();
        assertThat(sm.getActiveSession()).isEqualTo(first); // session not changed to second
    }

    @Test
    void stopCaptureReturnsFalseAndDoesNotClearSession() {
        // Stop with wrong ID: returns false AND session still active
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        boolean result = sm.stopCapture(UUID.randomUUID());

        assertThat(result).isFalse();
        assertThat(sm.getActiveSession()).isEqualTo(id); // session not cleared
        assertThat(sm.isActive()).isTrue();
    }

    @Test
    void stopCaptureReturnsTrueAndClearsAllState() {
        // Successful stop: returns true AND session null AND not active
        // Kills return value or null-assignment mutation on L67-68
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        boolean result = sm.stopCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.getActiveSession()).isNull();
        assertThat(sm.isActive()).isFalse();
    }

    @Test
    void startCaptureReturnsTrueAndSetsSession() {
        // Successful start: returns true AND session matches
        // Kills return value mutation on L48 or session assignment on L47
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();

        boolean result = sm.startCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.getActiveSession()).isEqualTo(id);
        assertThat(sm.isActive()).isTrue();
    }
}
