package com.boombapcompile.blckvox.service.hotkey.trigger;

import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent;
import com.boombapcompile.blckvox.service.hotkey.NormalizedKeyEvent.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModifierCombinationTriggerTest {

    private final ModifierCombinationTrigger trigger =
            new ModifierCombinationTrigger(List.of("META", "SHIFT"), "D");

    @Test
    void pressWithAllModifiersReturnsTrue() {
        assertThat(trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")))).isTrue();
    }

    @Test
    void repeatPressWhileHeldReturnsFalse() {
        trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")));
        assertThat(trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")))).isFalse();
    }

    @Test
    void releaseAfterPressReturnsTrue() {
        trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")));
        assertThat(trigger.onKeyReleased(release("D"))).isTrue();
    }

    @Test
    void pressWithWrongKeyReturnsFalse() {
        assertThat(trigger.onKeyPressed(press("E", Set.of("META", "SHIFT")))).isFalse();
    }

    @Test
    void pressMissingOneModifierReturnsFalse() {
        assertThat(trigger.onKeyPressed(press("D", Set.of("META")))).isFalse();
    }

    @Test
    void releaseWhenNotHeldReturnsFalse() {
        assertThat(trigger.onKeyReleased(release("D"))).isFalse();
    }

    @Test
    void releaseWithWrongKeyReturnsFalse() {
        trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")));
        assertThat(trigger.onKeyReleased(release("E"))).isFalse();
    }

    @Test
    void nameFormat() {
        String name = trigger.name();
        assertThat(name).startsWith("combo:");
        assertThat(name).contains("META");
        assertThat(name).contains("SHIFT");
        assertThat(name).contains("D");
    }

    @Test
    void multiplePressReleaseCycles() {
        assertThat(trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")))).isTrue();
        assertThat(trigger.onKeyReleased(release("D"))).isTrue();

        // second cycle
        assertThat(trigger.onKeyPressed(press("D", Set.of("META", "SHIFT")))).isTrue();
        assertThat(trigger.onKeyReleased(release("D"))).isTrue();
    }

    private static NormalizedKeyEvent press(String key, Set<String> modifiers) {
        return new NormalizedKeyEvent(Type.PRESSED, key, modifiers, System.currentTimeMillis());
    }

    private static NormalizedKeyEvent release(String key) {
        return new NormalizedKeyEvent(Type.RELEASED, key, Set.of(), System.currentTimeMillis());
    }
}
