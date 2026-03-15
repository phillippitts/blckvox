package com.boombapcompile.blckvox.service.hotkey;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility for canonicalizing key and modifier names and validating against
 * a known allow-list to keep configuration and adapters consistent.
 */
public final class KeyNameMapper {

    private static final Set<String> ALLOWED_MODIFIERS = Set.of("META", "SHIFT", "CONTROL", "ALT",
            "LEFT_META", "RIGHT_META", "LEFT_SHIFT", "RIGHT_SHIFT", "LEFT_CONTROL", "RIGHT_CONTROL",
            "LEFT_ALT", "RIGHT_ALT");

    private static final Set<String> ALLOWED_KEYS;

    static {
        Set<String> keys = new HashSet<>();
        // Letters A..Z
        for (char c = 'A'; c <= 'Z'; c++) {
            keys.add(String.valueOf(c));
        }
        // Digits 0..9
        for (char c = '0'; c <= '9'; c++) {
            keys.add(String.valueOf(c));
        }
        // Function keys F1..F24
        IntStream.rangeClosed(1, 24).forEach(i -> keys.add("F" + i));
        // Specials
        keys.addAll(List.of("ESCAPE", "ENTER", "TAB", "SPACE", "BACKSPACE"));
        // Meta keys as primaries (some platforms expose LEFT/RIGHT_META as key)
        keys.addAll(List.of("LEFT_META", "RIGHT_META", "LEFT_SHIFT", "RIGHT_SHIFT",
                "LEFT_CONTROL", "RIGHT_CONTROL", "LEFT_ALT", "RIGHT_ALT"));
        ALLOWED_KEYS = Set.copyOf(keys);
    }

    private KeyNameMapper() {}

    /** Canonicalize a key name (case-insensitive, spaces to underscores, aliases). */
    public static String normalizeKey(String keyText) {
        if (keyText == null) {
            return "UNKNOWN";
        }
        String k = keyText.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace("PLUS", "+")
                .replace("COMMAND", "META")
                .replace("CMD", "META");
        // Normalize common forms
        if (k.contains("RIGHT_META")) {
            return "RIGHT_META";
        }
        if (k.contains("LEFT_META")) {
            return "LEFT_META";
        }
        return k;
    }

    /** Normalize a single modifier alias to canonical form. */
    public static String normalizeModifier(String mod) {
        if (mod == null) {
            return "";
        }
        String m = mod.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace("COMMAND", "META")
                .replace("CMD", "META");
        return m;
    }

    public static boolean isValidKey(String key) {
        return ALLOWED_KEYS.contains(normalizeKey(key));
    }

    public static boolean isValidModifier(String mod) {
        String m = normalizeModifier(mod);
        return ALLOWED_MODIFIERS.contains(m);
    }

    /**
     * Compare a configured hotkey (mods + key) against a reserved combo string
     * like "META+TAB" or "META+SHIFT+D".
     */
    public static boolean matchesReserved(Set<String> configuredMods, String configuredKey, String reservedSpec) {
        if (reservedSpec == null || reservedSpec.isBlank()) {
            return false;
        }
        String[] parts = reservedSpec.split("\\+");
        Set<String> rmods = new HashSet<>();
        String rkey = null;
        for (String p : parts) {
            String n = p.trim();
            if (n.isEmpty()) {
                continue;
            }
            String nm = normalizeModifier(n);
            if (ALLOWED_MODIFIERS.contains(nm)) {
                rmods.add(nm);
            } else {
                rkey = normalizeKey(n);
            }
        }
        String ckey = normalizeKey(configuredKey);
        Set<String> cmods = configuredMods.stream()
                .map(KeyNameMapper::normalizeModifier)
                .collect(Collectors.toSet());
        return ckey.equals(rkey) && cmods.containsAll(rmods) && rmods.containsAll(cmods);
    }
}
