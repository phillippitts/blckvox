package com.boombapcompile.blckvox.service.events;

import com.boombapcompile.blckvox.service.audio.capture.CaptureErrorEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyConflictEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPermissionDeniedEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorEventsListenerTest {

    @Test
    void shouldLogReturnsTrueOnFirstCall() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThat(listener.shouldLog("test-key")).isTrue();
    }

    @Test
    void shouldLogReturnsFalseWithinThrottleWindow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("test-key"); // First call
        assertThat(listener.shouldLog("test-key")).isFalse(); // Within 1 min
    }

    @Test
    void shouldLogReturnsTrueForDifferentKeys() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("key-1");
        assertThat(listener.shouldLog("key-2")).isTrue();
    }

    @Test
    void onHotkeyPermissionDeniedRecordsThrottleKey() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        assertThat(listener.shouldLog("hotkey-permission")).isFalse();
    }

    @Test
    void onHotkeyConflictRecordsThrottleKey() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.onHotkeyConflict(
                new HotkeyConflictEvent("TAB", List.of("META"), Instant.now()));
        assertThat(listener.shouldLog("hotkey-conflict-TAB-[META]")).isFalse();
    }

    @Test
    void onCaptureErrorRecordsThrottleKey() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.onCaptureError(new CaptureErrorEvent("test-reason", Instant.now()));
        assertThat(listener.shouldLog("capture-test-reason")).isFalse();
    }

    @Test
    void onHotkeyConflictIsThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        HotkeyConflictEvent event = new HotkeyConflictEvent("TAB", List.of("META"), Instant.now());
        listener.onHotkeyConflict(event);
        // second call within throttle window — shouldLog returns false
        assertThat(listener.shouldLog("hotkey-conflict-TAB-[META]")).isFalse();
    }

    @Test
    void onCaptureErrorIsThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        CaptureErrorEvent event = new CaptureErrorEvent("mic-fail", Instant.now());
        listener.onCaptureError(event);
        // second call with same reason key — shouldLog returns false
        assertThat(listener.shouldLog("capture-mic-fail")).isFalse();
    }

    @Test
    void differentEventKeysAreIndependent() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThat(listener.shouldLog("key-a")).isTrue();
        assertThat(listener.shouldLog("key-b")).isTrue();
        // key-a is still throttled
        assertThat(listener.shouldLog("key-a")).isFalse();
        // key-b is also still throttled
        assertThat(listener.shouldLog("key-b")).isFalse();
        // new key is not throttled
        assertThat(listener.shouldLog("key-c")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLogReturnsTrueAfterThrottleWindowExpires() throws Exception {
        ErrorEventsListener listener = new ErrorEventsListener();
        // First call — records current timestamp
        assertThat(listener.shouldLog("expired-key")).isTrue();
        // Within throttle window — false
        assertThat(listener.shouldLog("expired-key")).isFalse();

        // Use reflection to set the lastLog entry to 2 minutes ago (past the 1-minute throttle)
        Field lastLogField = ErrorEventsListener.class.getDeclaredField("lastLog");
        lastLogField.setAccessible(true);
        Map<String, Instant> lastLog = (Map<String, Instant>) lastLogField.get(listener);
        lastLog.put("expired-key", Instant.now().minusSeconds(120));

        // After throttle window — should return true again
        assertThat(listener.shouldLog("expired-key")).isTrue();
    }

    @Test
    void onHotkeyPermissionDeniedThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        // Second call within throttle window — shouldLog returns false
        assertThat(listener.shouldLog("hotkey-permission")).isFalse();
    }

    @Test
    void onHotkeyPermissionDeniedCalledTwiceStaysThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        // Second call exercises the throttled (false) branch
        listener.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        assertThat(listener.shouldLog("hotkey-permission")).isFalse();
    }

    @Test
    void onHotkeyConflictCalledTwiceStaysThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        HotkeyConflictEvent event = new HotkeyConflictEvent("TAB", List.of("META"), Instant.now());
        listener.onHotkeyConflict(event);
        // Second call exercises the throttled (false) branch
        listener.onHotkeyConflict(event);
        assertThat(listener.shouldLog("hotkey-conflict-TAB-[META]")).isFalse();
    }

    @Test
    void onCaptureErrorCalledTwiceStaysThrottled() {
        ErrorEventsListener listener = new ErrorEventsListener();
        CaptureErrorEvent event = new CaptureErrorEvent("mic-fail", Instant.now());
        listener.onCaptureError(event);
        // Second call exercises the throttled (false) branch
        listener.onCaptureError(event);
        assertThat(listener.shouldLog("capture-mic-fail")).isFalse();
    }

    // --- Mutation-killing boundary tests ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldLogBoundaryExactlyAtThrottleExpiry() throws Exception {
        // Set lastLog to 59 seconds ago (just under THROTTLE = 60s)
        // Duration.between(prev, now) ≈ 59s, compareTo(60s) < 0 → should NOT log
        // Kills > to >= on L55 (a >= mutant would let 59s through)
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("boundary-key"); // First call records timestamp

        Field lastLogField = ErrorEventsListener.class.getDeclaredField("lastLog");
        lastLogField.setAccessible(true);
        Map<String, Instant> lastLog = (Map<String, Instant>) lastLogField.get(listener);
        lastLog.put("boundary-key", Instant.now().minusSeconds(59));

        // At ~59s: compareTo returns < 0, so condition is false → should return false
        assertThat(listener.shouldLog("boundary-key")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLogReturnsTrueJustAfterThrottleExpiry() throws Exception {
        // Set lastLog to 61 seconds ago → past the 60-second throttle
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("past-key");

        Field lastLogField = ErrorEventsListener.class.getDeclaredField("lastLog");
        lastLogField.setAccessible(true);
        Map<String, Instant> lastLog = (Map<String, Instant>) lastLogField.get(listener);
        lastLog.put("past-key", Instant.now().minusSeconds(61));

        assertThat(listener.shouldLog("past-key")).isTrue();
    }
}
