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
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void onHotkeyPermissionDeniedDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onHotkeyPermissionDenied(
                new HotkeyPermissionDeniedEvent(Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onHotkeyConflictDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onHotkeyConflict(
                new HotkeyConflictEvent("TAB", List.of("META"), Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onCaptureErrorDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onCaptureError(
                new CaptureErrorEvent("test-reason", Instant.now())))
                .doesNotThrowAnyException();
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
    void onHotkeyPermissionDeniedCalledTwiceDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        // First call triggers logging, second call hits the throttled (false) branch
        listener.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        assertThatCode(() -> listener.onHotkeyPermissionDenied(
                new HotkeyPermissionDeniedEvent(Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onHotkeyConflictCalledTwiceDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        HotkeyConflictEvent event = new HotkeyConflictEvent("TAB", List.of("META"), Instant.now());
        listener.onHotkeyConflict(event);
        // Second call exercises the false branch of if(shouldLog(key))
        assertThatCode(() -> listener.onHotkeyConflict(event))
                .doesNotThrowAnyException();
    }

    @Test
    void onCaptureErrorCalledTwiceDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        CaptureErrorEvent event = new CaptureErrorEvent("mic-fail", Instant.now());
        listener.onCaptureError(event);
        // Second call exercises the false branch of if(shouldLog(key))
        assertThatCode(() -> listener.onCaptureError(event))
                .doesNotThrowAnyException();
    }
}
