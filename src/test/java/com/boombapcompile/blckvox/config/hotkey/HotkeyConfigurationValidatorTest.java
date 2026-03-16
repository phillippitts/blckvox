package com.boombapcompile.blckvox.config.hotkey;

import org.junit.jupiter.api.Test;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.TriggerType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotkeyConfigurationValidatorTest {

    @Test
    void acceptsValidSingleKeyConfiguration() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "F13",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsValidDoubleTapConfiguration() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "D",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsValidModifierComboConfiguration() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("META", "SHIFT"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsLeftRightModifierVariants() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("LEFT_META", "RIGHT_SHIFT"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsAllValidModifiers() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "A",
                300,
                List.of("META", "SHIFT", "CONTROL", "ALT"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsAllValidSpecialKeys() {
        // Test a few special keys
        for (String key : List.of("ESCAPE", "ENTER", "TAB", "SPACE", "BACKSPACE")) {
            HotkeyProperties props = new HotkeyProperties(
                    TriggerType.SINGLE_KEY,
                    key,
                    300,
                    List.of(),
                    List.of(),
                    false
            );
            HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

            // Should not throw
            validator.validate();
        }
    }

    @Test
    void acceptsNumericKeys() {
        for (String key : List.of("0", "1", "5", "9")) {
            HotkeyProperties props = new HotkeyProperties(
                    TriggerType.SINGLE_KEY,
                    key,
                    300,
                    List.of(),
                    List.of(),
                    false
            );
            HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

            // Should not throw
            validator.validate();
        }
    }

    @Test
    void acceptsFunctionKeys() {
        for (String key : List.of("F1", "F12", "F13", "F24")) {
            HotkeyProperties props = new HotkeyProperties(
                    TriggerType.SINGLE_KEY,
                    key,
                    300,
                    List.of(),
                    List.of(),
                    false
            );
            HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

            // Should not throw
            validator.validate();
        }
    }

    @Test
    void acceptsAlphabeticKeys() {
        for (String key : List.of("A", "D", "M", "Z")) {
            HotkeyProperties props = new HotkeyProperties(
                    TriggerType.SINGLE_KEY,
                    key,
                    300,
                    List.of(),
                    List.of(),
                    false
            );
            HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

            // Should not throw
            validator.validate();
        }
    }

    @Test
    void rejectsNullType() {
        HotkeyProperties props = new HotkeyProperties(
                null,
                "F13",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.type: null");
    }

    @Test
    void rejectsInvalidKey() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "INVALID_KEY",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.key: 'INVALID_KEY'")
                .hasMessageContaining("Must be A-Z, 0-9, F1..F24");
    }

    @Test
    void acceptsLowercaseKeyByNormalizing() {
        // KeyNameMapper normalizes lowercase to uppercase
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "d",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw - lowercase is accepted and normalized
        validator.validate();
    }

    @Test
    void rejectsInvalidModifier() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("META", "INVALID_MOD"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.modifiers entry: 'INVALID_MOD'")
                .hasMessageContaining("Allowed: META, SHIFT, CONTROL, ALT");
    }

    @Test
    void acceptsLowercaseModifierByNormalizing() {
        // KeyNameMapper normalizes lowercase to uppercase
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("meta"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw - lowercase is accepted and normalized
        validator.validate();
    }

    @Test
    void rejectsModifierComboWithoutModifiers() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hotkey.type=modifier-combination requires at least one modifier");
    }

    @Test
    void allowsSingleKeyWithModifiers() {
        // Single-key type can have optional modifiers
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "F13",
                300,
                List.of("META"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void allowsDoubleTapWithoutModifiers() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "D",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void allowsEmptyReservedList() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "F13",
                300,
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw (validator doesn't strictly check reserved list)
        validator.validate();
    }

    @Test
    void allowsNonEmptyReservedList() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "F13",
                300,
                List.of(),
                List.of("META+TAB", "META+L"),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw (validator doesn't strictly check reserved list)
        validator.validate();
    }

    @Test
    void handlesMultipleInvalidModifiersInSingleError() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("META", "INVALID_1", "SHIFT"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // First invalid modifier should cause failure
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.modifiers entry: 'INVALID_1'");
    }

    @Test
    void acceptsSingleModifierForCombo() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D",
                300,
                List.of("META"),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw (one modifier is enough)
        validator.validate();
    }

    @Test
    void rejectsDoubleTapThresholdTooLow() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "F13",
                50, // Too low
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Double-tap threshold must be between 100 and 1000");
    }

    @Test
    void rejectsDoubleTapThresholdTooHigh() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "F13",
                1500, // Too high
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Double-tap threshold must be between 100 and 1000");
    }

    @Test
    void acceptsDoubleTapThresholdAtMinimum() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "D",
                100, // Minimum valid
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsDoubleTapThresholdAtMaximum() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "D",
                1000, // Maximum valid
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }

    @Test
    void acceptsDoubleTapThresholdInRange() {
        HotkeyProperties props = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "D",
                300, // Valid range
                List.of(),
                List.of(),
                false
        );
        HotkeyConfigurationValidator validator = new HotkeyConfigurationValidator(props);

        // Should not throw
        validator.validate();
    }
}
