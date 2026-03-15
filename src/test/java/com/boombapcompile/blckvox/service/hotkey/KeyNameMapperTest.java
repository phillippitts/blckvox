package com.boombapcompile.blckvox.service.hotkey;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KeyNameMapperTest {

    @Test
    void normalizesAliases() {
        assertThat(KeyNameMapper.normalizeKey("Right Meta")).isEqualTo("RIGHT_META");
        assertThat(KeyNameMapper.normalizeKey("Command")).isEqualTo("META");
        assertThat(KeyNameMapper.normalizeModifier("cmd")).isEqualTo("META");
        assertThat(KeyNameMapper.normalizeModifier("Command")).isEqualTo("META");
    }

    @Test
    void validatesKeysAndModifiers() {
        assertThat(KeyNameMapper.isValidKey("F13")).isTrue();
        assertThat(KeyNameMapper.isValidKey("ENTER")).isTrue();
        assertThat(KeyNameMapper.isValidKey("foo")).isFalse();
        assertThat(KeyNameMapper.isValidModifier("SHIFT")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("LEFT_META")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("WEIRD")).isFalse();
    }

    @Test
    void matchesReservedCombos() {
        Set<String> mods = Set.of("META", "SHIFT");
        assertThat(KeyNameMapper.matchesReserved(mods, "D", "META+SHIFT+D")).isTrue();
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "META+TAB")).isTrue();
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "META+L")).isFalse();
    }

    @Test
    void normalizeKeyNull() {
        assertThat(KeyNameMapper.normalizeKey(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void normalizeKeyLeftMeta() {
        assertThat(KeyNameMapper.normalizeKey("Left Meta")).isEqualTo("LEFT_META");
    }

    @Test
    void normalizeKeyPlus() {
        assertThat(KeyNameMapper.normalizeKey("plus")).isEqualTo("+");
    }

    @Test
    void normalizeKeyCmdAlias() {
        assertThat(KeyNameMapper.normalizeKey("cmd")).isEqualTo("META");
    }

    @Test
    void normalizeModifierNull() {
        assertThat(KeyNameMapper.normalizeModifier(null)).isEmpty();
    }

    @Test
    void validatesLetterKeys() {
        assertThat(KeyNameMapper.isValidKey("A")).isTrue();
        assertThat(KeyNameMapper.isValidKey("z")).isTrue();
    }

    @Test
    void validatesDigitKeys() {
        assertThat(KeyNameMapper.isValidKey("0")).isTrue();
        assertThat(KeyNameMapper.isValidKey("9")).isTrue();
    }

    @Test
    void validatesSpecialKeys() {
        assertThat(KeyNameMapper.isValidKey("ESCAPE")).isTrue();
        assertThat(KeyNameMapper.isValidKey("SPACE")).isTrue();
        assertThat(KeyNameMapper.isValidKey("BACKSPACE")).isTrue();
        assertThat(KeyNameMapper.isValidKey("TAB")).isTrue();
    }

    @Test
    void validatesModifiersControl() {
        assertThat(KeyNameMapper.isValidModifier("CONTROL")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("ALT")).isTrue();
    }

    @Test
    void matchesReservedNullSpec() {
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", null)).isFalse();
    }

    @Test
    void matchesReservedBlankSpec() {
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "  ")).isFalse();
    }

    @Test
    void matchesReservedExtraModsInConfigured() {
        // Configured has extra modifier not in reserved → false
        assertThat(KeyNameMapper.matchesReserved(Set.of("META", "SHIFT"), "TAB", "META+TAB")).isFalse();
    }

    @Test
    void matchesReservedWithEmptyPartInSpec() {
        // "META++TAB" has an empty part between the plus signs
        assertThat(KeyNameMapper.matchesReserved(Set.of("META"), "TAB", "META++TAB")).isTrue();
    }

    @Test
    void validatesMetaAsKey() {
        assertThat(KeyNameMapper.isValidKey("LEFT_META")).isTrue();
        assertThat(KeyNameMapper.isValidKey("RIGHT_META")).isTrue();
        assertThat(KeyNameMapper.isValidKey("LEFT_ALT")).isTrue();
    }

    @Test
    void validatesFunctionKeys() {
        assertThat(KeyNameMapper.isValidKey("F1")).isTrue();
        assertThat(KeyNameMapper.isValidKey("F24")).isTrue();
    }

    @Test
    void matchesReservedWithAllowedModifier() {
        // Uses LEFT_META which is in ALLOWED_MODIFIERS set, exercising the contains() true branch
        assertThat(KeyNameMapper.matchesReserved(Set.of("LEFT_META"), "D", "LEFT_META+D")).isTrue();
    }

    @Test
    void matchesReservedModifierOnlySpec() {
        // Spec "META" has no key part → rkey is null → ckey.equals(null) is false
        assertThat(KeyNameMapper.matchesReserved(Set.of(), "D", "META")).isFalse();
    }

    @Test
    void isValidModifierForRightSideModifiers() {
        assertThat(KeyNameMapper.isValidModifier("RIGHT_META")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("LEFT_CONTROL")).isTrue();
        assertThat(KeyNameMapper.isValidModifier("RIGHT_ALT")).isTrue();
    }

    @Test
    void normalizeModifierSpacesAndUnderscores() {
        assertThat(KeyNameMapper.normalizeModifier("left shift")).isEqualTo("LEFT_SHIFT");
    }
}
