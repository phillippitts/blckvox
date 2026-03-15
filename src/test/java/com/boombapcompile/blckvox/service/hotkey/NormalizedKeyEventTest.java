package com.boombapcompile.blckvox.service.hotkey;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizedKeyEventTest {

    @Test
    void nullKeyThrowsIae() {
        assertThatThrownBy(() -> new NormalizedKeyEvent(
                NormalizedKeyEvent.Type.PRESSED, null, Set.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankKeyThrowsIae() {
        assertThatThrownBy(() -> new NormalizedKeyEvent(
                NormalizedKeyEvent.Type.PRESSED, "  ", Set.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyNormalizedToUpperCase() {
        var evt = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "tab", Set.of(), 123);
        assertThat(evt.key()).isEqualTo("TAB");
    }

    @Test
    void modifiersNormalizedToUpperCase() {
        var evt = new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "J",
                Set.of("meta", "shift"), 0);
        assertThat(evt.modifiers()).containsExactlyInAnyOrder("META", "SHIFT");
    }

    @Test
    void nullModifiersDefaultsToEmptySet() {
        var evt = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "A", null, 0);
        assertThat(evt.modifiers()).isEmpty();
    }

    @Test
    void emptyModifiersStaysEmpty() {
        var evt = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "B", Set.of(), 0);
        assertThat(evt.modifiers()).isEmpty();
    }

    @Test
    void preservesTypeAndWhenMillis() {
        var evt = new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "ESC", Set.of("ALT"), 999L);
        assertThat(evt.type()).isEqualTo(NormalizedKeyEvent.Type.RELEASED);
        assertThat(evt.whenMillis()).isEqualTo(999L);
    }
}
