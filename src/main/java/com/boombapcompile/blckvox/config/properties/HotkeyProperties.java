package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed properties for global hotkey configuration.
 * Supports three trigger styles:
 * - single-key
 * - double-tap (same key pressed twice within threshold)
 * - modifier-combination (e.g., META+SHIFT+D)
 * Values are validated on startup for fail-fast behavior.
 */
@Validated
@ConfigurationProperties(prefix = "hotkey")
public record HotkeyProperties(

        // Trigger type.
        @jakarta.validation.constraints.NotNull
        TriggerType type,

        // Primary key code name (e.g., RIGHT_META, F13, D).
        @NotBlank
        String key,

        // Double-tap threshold (ms). Only used when type=double-tap.
        @DefaultValue("300")
        @Min(100)
        @Max(1000)
        int thresholdMs,

        // Optional modifiers for single-key or combination types (e.g., META, SHIFT, CONTROL, ALT).
        List<String> modifiers,

        // Reserved OS shortcuts to flag as conflicts (e.g., META+TAB, META+L).
        List<String> reserved,

        // Toggle mode: if true, first hotkey press starts recording, second press stops and transcribes.
        // If false (default), uses push-to-talk: press starts recording, release stops and transcribes.
        @DefaultValue("false")
        boolean toggleMode
) {

    public HotkeyProperties {
        if (modifiers == null) {
            modifiers = List.of();
        } else {
            modifiers = List.copyOf(modifiers);
        }
        if (reserved == null || reserved.isEmpty()) {
            reserved = List.of("META+TAB", "META+L");
        } else {
            reserved = List.copyOf(reserved);
        }
    }

    public boolean isToggleMode() {
        return toggleMode;
    }

    public TriggerType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public int getThresholdMs() {
        return thresholdMs;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getReserved() {
        return reserved;
    }
}
