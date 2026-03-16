package com.boombapcompile.blckvox.config.properties;

import com.boombapcompile.blckvox.config.properties.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyPropertiesTest {

    @Test
    void nullModifiersDefaultsToEmptyList() {
        var props = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, null, List.of("META+TAB"), false);
        assertThat(props.getModifiers()).isEmpty();
    }

    @Test
    void nonNullModifiersAreCopied() {
        var props = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                List.of("META", "SHIFT"), List.of("META+TAB"), false);
        assertThat(props.getModifiers()).containsExactly("META", "SHIFT");
    }

    @Test
    void nullReservedDefaultsToDefaults() {
        var props = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, List.of(), null, false);
        assertThat(props.getReserved()).containsExactly("META+TAB", "META+L");
    }

    @Test
    void emptyReservedDefaultsToDefaults() {
        var props = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, List.of(), List.of(), false);
        assertThat(props.getReserved()).containsExactly("META+TAB", "META+L");
    }

    @Test
    void nonEmptyReservedIsPreserved() {
        var props = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                List.of(), List.of("CTRL+ALT+DEL"), false);
        assertThat(props.getReserved()).containsExactly("CTRL+ALT+DEL");
    }

    @Test
    void toggleModeAccessors() {
        var toggleOn = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                List.of("META"), List.of("X"), true);
        assertThat(toggleOn.isToggleMode()).isTrue();
        assertThat(toggleOn.toggleMode()).isTrue();

        var toggleOff = new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                List.of("META"), List.of("X"), false);
        assertThat(toggleOff.isToggleMode()).isFalse();
    }

    @Test
    void accessorsReturnCorrectValues() {
        var props = new HotkeyProperties(TriggerType.DOUBLE_TAP, "F13", 500,
                List.of("ALT"), List.of("META+TAB"), false);
        assertThat(props.getType()).isEqualTo(TriggerType.DOUBLE_TAP);
        assertThat(props.getKey()).isEqualTo("F13");
        assertThat(props.getThresholdMs()).isEqualTo(500);
    }
}
