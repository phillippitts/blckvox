package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RestartBudgetTrackerTest {

    private final SttWatchdogProperties props = new SttWatchdogProperties(
            true, 60, 3, 10, false, 60000L, 0.3, 10, 5);

    @Test
    void allowsRestartWhenBudgetNotExhausted() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        assertThat(tracker.allowsRestart("vosk")).isTrue();
    }

    @Test
    void deniesRestartWhenBudgetExhausted() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        for (int i = 0; i < 3; i++) {
            tracker.recordRestart("vosk");
        }

        assertThat(tracker.allowsRestart("vosk")).isFalse();
    }

    @Test
    void getRestartCountReturnsCorrectCount() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        tracker.recordRestart("vosk");
        tracker.recordRestart("vosk");

        assertThat(tracker.getRestartCount("vosk")).isEqualTo(2);
    }

    @Test
    void disableSetsInCooldown() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        Instant until = tracker.disable("vosk");

        assertThat(tracker.isInCooldown("vosk")).isTrue();
        assertThat(tracker.getCooldownUntil("vosk")).isEqualTo(until);
    }

    @Test
    void clearOnRecoveryClearsCooldownAndWindow() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        tracker.recordRestart("vosk");
        tracker.disable("vosk");

        tracker.clearOnRecovery("vosk");

        assertThat(tracker.isInCooldown("vosk")).isFalse();
        assertThat(tracker.getRestartCount("vosk")).isZero();
    }

    @Test
    void isInCooldownReturnsFalseWhenNotDisabled() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        assertThat(tracker.isInCooldown("vosk")).isFalse();
    }

    @Test
    void tryLockRestartAcquiresAndReleasesLock() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        // ReentrantLock allows same-thread reacquire, so just verify acquire+release cycle
        assertThat(tracker.tryLockRestart("vosk")).isTrue();
        tracker.unlockRestart("vosk");
        // Can acquire again after release
        assertThat(tracker.tryLockRestart("vosk")).isTrue();
        tracker.unlockRestart("vosk");
    }

    @Test
    void isInCooldownReturnsFalseAfterCooldownExpires() {
        // Use 0-minute cooldown so it expires immediately
        SttWatchdogProperties zeroCooldownProps = new SttWatchdogProperties(
                true, 60, 3, 0, false, 60000L, 0.3, 10, 5);
        RestartBudgetTracker tracker = new RestartBudgetTracker(zeroCooldownProps);
        tracker.register("vosk");

        tracker.disable("vosk");
        // With 0-minute cooldown, Instant.now().isBefore(until) is false immediately
        assertThat(tracker.isInCooldown("vosk")).isFalse();
    }

    @Test
    void getCooldownUntilReturnsNullWhenNotDisabled() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");

        assertThat(tracker.getCooldownUntil("vosk")).isNull();
    }

    @Test
    void independentEngineTracking() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");
        tracker.register("whisper");

        tracker.recordRestart("vosk");
        tracker.recordRestart("vosk");

        assertThat(tracker.getRestartCount("vosk")).isEqualTo(2);
        assertThat(tracker.getRestartCount("whisper")).isZero();
    }

    @Test
    void disableAndClearOnRecoveryAreIndependent() {
        RestartBudgetTracker tracker = new RestartBudgetTracker(props);
        tracker.register("vosk");
        tracker.register("whisper");

        tracker.disable("vosk");
        tracker.recordRestart("whisper");

        // Clear vosk shouldn't affect whisper
        tracker.clearOnRecovery("vosk");
        assertThat(tracker.isInCooldown("vosk")).isFalse();
        assertThat(tracker.getRestartCount("whisper")).isEqualTo(1);
    }
}
