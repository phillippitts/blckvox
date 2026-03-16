package com.boombapcompile.blckvox.config.hotkey;

import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.config.properties.TriggerType;

import com.boombapcompile.blckvox.service.hotkey.KeyNameMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Validates HotkeyProperties against allow-lists at startup to fail fast with
 * actionable messages.
 */
@Component
class HotkeyConfigurationValidator {


    private final HotkeyProperties props;

    HotkeyConfigurationValidator(HotkeyProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validate() {
        if (props.getType() == null) {
            throw new IllegalArgumentException("Invalid hotkey.type: null");
        }
        // Key validation
        if (!KeyNameMapper.isValidKey(props.getKey())) {
            throw new IllegalArgumentException("Invalid hotkey.key: '" + props.getKey()
                    + "'. Must be A-Z, 0-9, F1..F24, or a known special "
                    + "(ESCAPE, ENTER, TAB, SPACE, BACKSPACE, LEFT/RIGHT_*). ");
        }
        // Modifiers validation
        for (String m : props.getModifiers()) {
            if (!KeyNameMapper.isValidModifier(m)) {
                throw new IllegalArgumentException("Invalid hotkey.modifiers entry: '" + m
                        + "'. Allowed: META, SHIFT, CONTROL, ALT and LEFT/RIGHT variants.");
            }
        }
        if (props.getType() == TriggerType.MODIFIER_COMBO && props.getModifiers().isEmpty()) {
            throw new IllegalArgumentException(
                    "hotkey.type=modifier-combination requires at least one modifier in hotkey.modifiers");
        }
        // Double-tap threshold validation
        if (props.getType() == TriggerType.DOUBLE_TAP) {
            int threshold = props.getThresholdMs();
            if (threshold < 100 || threshold > 1000) {
                throw new IllegalArgumentException(
                        "Double-tap threshold must be between 100 and 1000 milliseconds, got: " + threshold);
            }
        }
        // Reserved list can be empty; no strict validation beyond basic format
    }
}
