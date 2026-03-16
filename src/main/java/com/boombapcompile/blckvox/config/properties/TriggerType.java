package com.boombapcompile.blckvox.config.properties;

/**
 * Enumerates supported global hotkey trigger types.
 * <p>
 * Spring Boot relaxed binding maps property values like "single-key" and
 * "modifier-combination" to SINGLE_KEY and MODIFIER_COMBO respectively.
 */
public enum TriggerType {
    SINGLE_KEY,
    DOUBLE_TAP,
    MODIFIER_COMBO
}
