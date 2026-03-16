package com.boombapcompile.blckvox.service.hotkey;

import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPermissionDeniedEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPressedEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyReleasedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HotkeyManagerTest {

    @Test
    void publishesPressAndReleaseForSingleKey() {
        // Arrange defaults: single-key RIGHT_META
        HotkeyProperties props =
                new HotkeyProperties(
                        TriggerType.SINGLE_KEY,
                        "RIGHT_META", 300, List.of(), List.of(), false);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;

        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, publisher);

        // Act
        mgr.start();
        long now = System.currentTimeMillis();
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "RIGHT_META",
                Set.of(), now));
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "RIGHT_META",
                Set.of(), now + 5));
        mgr.stop();

        // Assert
        assertThat(events.stream().filter(e -> e instanceof HotkeyPressedEvent).count())
                .isEqualTo(1);
        assertThat(events.stream().filter(e -> e instanceof HotkeyReleasedEvent).count())
                .isEqualTo(1);
    }

    @Test
    void startWhenAlreadyRunningIsNoop() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        mgr.start(); // second start — should be noop
        assertThat(mgr.isRunning()).isTrue();
        assertThat(hook.registerCount).isEqualTo(1);
        mgr.stop();
    }

    @Test
    void stopWhenNotRunningIsNoop() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        assertThatCode(mgr::stop).doesNotThrowAnyException();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void startWithSecurityExceptionPublishesPermissionDenied() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        ThrowingHook hook = new ThrowingHook(new SecurityException("no access"));
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        assertThat(mgr.isRunning()).isFalse();
        assertThat(events).hasAtLeastOneElementOfType(HotkeyPermissionDeniedEvent.class);
    }

    @Test
    void startWithGenericExceptionDoesNotRethrow() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        ThrowingHook hook = new ThrowingHook(new RuntimeException("boom"));
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        assertThatCode(mgr::start).doesNotThrowAnyException();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void isRunningReflectsState() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        assertThat(mgr.isRunning()).isFalse();
        mgr.start();
        assertThat(mgr.isRunning()).isTrue();
        mgr.stop();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void stopWhenUnregisterThrowsStillStops() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        UnregisterThrowingHook hook = new UnregisterThrowingHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        assertThat(mgr.isRunning()).isTrue();
        mgr.stop();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void unmatchedKeyEventPublishesNoHotkeyEvent() {
        HotkeyProperties props = defaultProps(); // configured for RIGHT_META
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        long now = System.currentTimeMillis();
        // Emit a key event for a different key — should not match
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "A", Set.of(), now));
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "A", Set.of(), now + 5));
        mgr.stop();

        // No hotkey events should have been published (only start/stop lifecycle)
        assertThat(events.stream().filter(e -> e instanceof HotkeyPressedEvent).count()).isZero();
        assertThat(events.stream().filter(e -> e instanceof HotkeyReleasedEvent).count()).isZero();
    }

    @Test
    void noConflictEventWhenNoReservedMatch() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY, "F20", 300, List.of(), List.of(), false);
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        // no conflict events should be published for an unrecognized key with empty reserved
        assertThat(events).noneMatch(e -> e.getClass().getSimpleName().contains("Conflict"));
        mgr.stop();
    }

    @Test
    void shutdownWhenNotRunningIsNoop() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        // shutdown calls stop() which should be a noop when not running
        mgr.shutdown();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void shutdownAfterStartStopsAndCleansUp() {
        HotkeyProperties props = defaultProps();
        List<Object> events = new ArrayList<>();
        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, events::add);

        mgr.start();
        assertThat(mgr.isRunning()).isTrue();
        mgr.shutdown();
        assertThat(mgr.isRunning()).isFalse();
    }

    private static HotkeyProperties defaultProps() {
        return new HotkeyProperties(
                TriggerType.SINGLE_KEY, "RIGHT_META", 300, List.of(), List.of(), false);
    }

    // Simple fake hook for tests
    static class FakeHook implements GlobalKeyHook {
        private volatile Consumer<NormalizedKeyEvent> listener;
        int registerCount;
        @Override
        public void register() { registerCount++; }
        @Override
        public void unregister() { }
        @Override
        public void addListener(Consumer<NormalizedKeyEvent> listener) {
            this.listener = listener;
        }
        void emit(NormalizedKeyEvent e) {
            Consumer<NormalizedKeyEvent> l = listener;
            if (l != null) {
                l.accept(e);
            }
        }
    }

    static class ThrowingHook implements GlobalKeyHook {
        private final RuntimeException ex;
        ThrowingHook(RuntimeException ex) { this.ex = ex; }
        @Override
        public void register() { throw ex; }
        @Override
        public void unregister() { }
        @Override
        public void addListener(Consumer<NormalizedKeyEvent> listener) { }
    }

    static class UnregisterThrowingHook implements GlobalKeyHook {
        @Override
        public void register() { }
        @Override
        public void unregister() { throw new RuntimeException("unregister failed"); }
        @Override
        public void addListener(Consumer<NormalizedKeyEvent> listener) { }
    }
}
