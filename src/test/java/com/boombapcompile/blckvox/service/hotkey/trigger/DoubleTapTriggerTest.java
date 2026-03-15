package com.boombapcompile.blckvox.service.hotkey.trigger;

import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent;
import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent.Type;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleTapTriggerTest {

    private final DoubleTapTrigger trigger = new DoubleTapTrigger("F13", 300);

    @Test
    void firstTapReturnsFalse() {
        assertThat(trigger.onKeyPressed(press("F13", 1000))).isFalse();
    }

    @Test
    void secondTapWithinThresholdReturnsTrue() {
        trigger.onKeyPressed(press("F13", 1000));
        assertThat(trigger.onKeyPressed(press("F13", 1200))).isTrue();
    }

    @Test
    void secondTapAfterThresholdReturnsFalse() {
        trigger.onKeyPressed(press("F13", 1000));
        assertThat(trigger.onKeyPressed(press("F13", 1500))).isFalse();
    }

    @Test
    void releaseWhenArmedReturnsTrue() {
        trigger.onKeyPressed(press("F13", 1000));
        trigger.onKeyPressed(press("F13", 1200));
        assertThat(trigger.onKeyReleased(release("F13", 1250))).isTrue();
    }

    @Test
    void releaseWhenNotArmedReturnsFalse() {
        trigger.onKeyPressed(press("F13", 1000));
        assertThat(trigger.onKeyReleased(release("F13", 1050))).isFalse();
    }

    @Test
    void wrongKeyPressReturnsFalse() {
        assertThat(trigger.onKeyPressed(press("F14", 1000))).isFalse();
    }

    @Test
    void wrongKeyReleaseReturnsFalse() {
        trigger.onKeyPressed(press("F13", 1000));
        trigger.onKeyPressed(press("F13", 1200));
        assertThat(trigger.onKeyReleased(release("F14", 1250))).isFalse();
    }

    @Test
    void slowTapTreatedAsNewFirstTap() {
        trigger.onKeyPressed(press("F13", 1000));
        // slow second tap — treated as new first tap
        assertThat(trigger.onKeyPressed(press("F13", 2000))).isFalse();
        // fast third tap — should match
        assertThat(trigger.onKeyPressed(press("F13", 2100))).isTrue();
    }

    @Test
    void nameFormat() {
        assertThat(trigger.name()).isEqualTo("double-tap:F13@300ms");
    }

    @Test
    void keyMatchIsCaseInsensitive() {
        DoubleTapTrigger lower = new DoubleTapTrigger("f13", 300);
        lower.onKeyPressed(press("F13", 1000));
        assertThat(lower.onKeyPressed(press("F13", 1100))).isTrue();
    }

    private static NormalizedKeyEvent press(String key, long when) {
        return new NormalizedKeyEvent(Type.PRESSED, key, Set.of(), when);
    }

    private static NormalizedKeyEvent release(String key, long when) {
        return new NormalizedKeyEvent(Type.RELEASED, key, Set.of(), when);
    }
}
