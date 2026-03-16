package com.boombapcompile.blckvox.service.hotkey;

import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.service.hotkey.trigger.DoubleTapTrigger;
import com.boombapcompile.blckvox.service.hotkey.trigger.ModifierCombinationTrigger;
import com.boombapcompile.blckvox.service.hotkey.trigger.SingleKeyTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyTriggerFactoryTest {

    @Test
    void buildsSingleKeyTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(
                TriggerType.SINGLE_KEY,
                "RIGHT_META", 300, List.of(), List.of(), false);
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(SingleKeyTrigger.class);
    }

    @Test
    void buildsDoubleTapTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(
                TriggerType.DOUBLE_TAP,
                "F13", 250, List.of(), List.of(), false);
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(DoubleTapTrigger.class);
    }

    @Test
    void buildsModifierCombinationTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(
                TriggerType.MODIFIER_COMBO,
                "D", 300, List.of("META", "SHIFT"), List.of(), false);
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(ModifierCombinationTrigger.class);
    }

}
