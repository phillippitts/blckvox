package com.boombapcompile.blckvox.service.hotkey;

import com.boombapcompile.blckvox.config.properties.TriggerType;
import com.boombapcompile.blckvox.config.properties.HotkeyProperties;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyConflictEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyManagerReservedConflictTest {

    @Test
    void publishesConflictEventWhenReservedMatches() {
        // Configure hotkey META+TAB and reserved contains META+TAB
        HotkeyProperties props =
                new HotkeyProperties(
                        TriggerType.MODIFIER_COMBO,
                        "TAB", 300, List.of("META"), List.of("META+TAB"), false);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;
        FakeHook hook = new FakeHook();

        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, publisher);
        mgr.start();
        mgr.stop();

        boolean hasConflict = events.stream().anyMatch(e -> e instanceof HotkeyConflictEvent);
        assertThat(hasConflict).isTrue();
    }

    static class FakeHook implements GlobalKeyHook {
        @Override
        public void register() { }
        @Override
        public void unregister() { }
        @Override
        public void addListener(Consumer<NormalizedKeyEvent> listener) { }
    }
}
