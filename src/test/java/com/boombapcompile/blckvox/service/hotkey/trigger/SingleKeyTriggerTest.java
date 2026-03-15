package com.boombapcompile.blckvox.service.hotkey.trigger;

import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent;
import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SingleKeyTriggerTest {

    @Test
    void pressWithMatchingKeyReturnsTrue() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        assertThat(trigger.onKeyPressed(press("RIGHT_META", Set.of()))).isTrue();
    }

    @Test
    void repeatPressWhileHeldReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        trigger.onKeyPressed(press("RIGHT_META", Set.of()));
        assertThat(trigger.onKeyPressed(press("RIGHT_META", Set.of()))).isFalse();
    }

    @Test
    void releaseAfterPressReturnsTrue() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        trigger.onKeyPressed(press("RIGHT_META", Set.of()));
        assertThat(trigger.onKeyReleased(release("RIGHT_META"))).isTrue();
    }

    @Test
    void pressWithWrongKeyReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        assertThat(trigger.onKeyPressed(press("LEFT_META", Set.of()))).isFalse();
    }

    @Test
    void pressWithoutRequiredModifiersReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("A", List.of("META", "SHIFT"));
        assertThat(trigger.onKeyPressed(press("A", Set.of()))).isFalse();
    }

    @Test
    void pressWithPartialModifiersReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("A", List.of("META", "SHIFT"));
        assertThat(trigger.onKeyPressed(press("A", Set.of("META")))).isFalse();
    }

    @Test
    void releaseWhenNotHeldReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        assertThat(trigger.onKeyReleased(release("RIGHT_META"))).isFalse();
    }

    @Test
    void releaseWithWrongKeyReturnsFalse() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        trigger.onKeyPressed(press("RIGHT_META", Set.of()));
        assertThat(trigger.onKeyReleased(release("LEFT_META"))).isFalse();
    }

    @Test
    void nameFormatWithoutModifiers() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());
        assertThat(trigger.name()).isEqualTo("single-key:RIGHT_META");
    }

    @Test
    void nameFormatWithModifiers() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("A", List.of("META", "SHIFT"));
        String name = trigger.name();
        assertThat(name).startsWith("single-key:A+");
        assertThat(name).contains("META");
        assertThat(name).contains("SHIFT");
    }

    @Test
    void multiplePressReleaseCycles() {
        SingleKeyTrigger trigger = new SingleKeyTrigger("RIGHT_META", List.of());

        assertThat(trigger.onKeyPressed(press("RIGHT_META", Set.of()))).isTrue();
        assertThat(trigger.onKeyReleased(release("RIGHT_META"))).isTrue();

        // second cycle should also work
        assertThat(trigger.onKeyPressed(press("RIGHT_META", Set.of()))).isTrue();
        assertThat(trigger.onKeyReleased(release("RIGHT_META"))).isTrue();
    }

    private static NormalizedKeyEvent press(String key, Set<String> modifiers) {
        return new NormalizedKeyEvent(Type.PRESSED, key, modifiers, System.currentTimeMillis());
    }

    private static NormalizedKeyEvent release(String key) {
        return new NormalizedKeyEvent(Type.RELEASED, key, Set.of(), System.currentTimeMillis());
    }
}
