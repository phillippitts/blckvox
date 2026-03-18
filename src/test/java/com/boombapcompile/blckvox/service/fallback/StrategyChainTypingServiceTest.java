package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.config.properties.TypingProperties;
import com.boombapcompile.blckvox.service.fallback.event.AllTypingFallbacksFailedEvent;
import com.boombapcompile.blckvox.service.fallback.event.TypingFallbackEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyChainTypingServiceTest {

    private static final TypingProperties PROPS = new TypingProperties(800, 30, 0, true, false,
            TypingProperties.NewlineMode.LF, true, true, "os-default", 200);

    @Test
    void fallsBackFromRobotToClipboard() {
        TypingAdapter failingRobot = adapter("robot", true, false);
        final boolean[] clipboardCalled = {false};
        TypingAdapter okClipboard = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                clipboardCalled[0] = true; return true;
            }
            @Override public String name() {
                return "clipboard";
            }
        };
        TypingAdapter notify = adapter("notify", true, true);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(failingRobot, okClipboard, notify), PROPS, e -> { });
        boolean ok = svc.paste("hello");
        assertThat(ok).isTrue();
        assertThat(clipboardCalled[0]).isTrue();
    }

    @Test
    void allAdaptersFailReturnsFalseAndPublishesEvent() {
        List<Object> events = new ArrayList<>();
        TypingAdapter failRobot = adapter("robot", true, false);
        TypingAdapter failClipboard = adapter("clipboard", true, false);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(failRobot, failClipboard), PROPS, events::add);

        boolean ok = svc.paste("hello");

        assertThat(ok).isFalse();
        assertThat(events).anyMatch(e -> e instanceof AllTypingFallbacksFailedEvent);
    }

    @Test
    void adapterThrowingPublishesFallbackEvent() {
        List<Object> events = new ArrayList<>();
        TypingAdapter throwing = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                throw new RuntimeException("boom");
            }
            @Override public String name() {
                return "robot";
            }
        };
        TypingAdapter ok = adapter("clipboard", true, true);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(throwing, ok), PROPS, events::add);

        boolean result = svc.paste("hello");

        assertThat(result).isTrue();
        assertThat(events).anyMatch(e -> e instanceof TypingFallbackEvent);
    }

    @Test
    void skipsAdapterThatCannotType() {
        final boolean[] robotCalled = {false};
        TypingAdapter unavailableRobot = new TypingAdapter() {
            @Override public boolean canType() {
                return false;
            }
            @Override public boolean type(String text) {
                robotCalled[0] = true; return true;
            }
            @Override public String name() {
                return "robot";
            }
        };
        TypingAdapter clipboard = adapter("clipboard", true, true);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(unavailableRobot, clipboard), PROPS, e -> { });

        boolean ok = svc.paste("hello");

        assertThat(ok).isTrue();
        assertThat(robotCalled[0]).isFalse();
    }

    @Test
    void pasteWithNullTextDoesNotThrow() {
        TypingAdapter clipboard = adapter("clipboard", true, true);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(clipboard), PROPS, e -> { });

        boolean ok = svc.paste(null);
        assertThat(ok).isTrue();
    }

    @Test
    void adapterOrderingNotifyLast() {
        // Provide adapters in wrong order — service should reorder to robot, clipboard, notify
        List<String> callOrder = new ArrayList<>();
        TypingAdapter notify = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                callOrder.add("notify"); return false;
            }
            @Override public String name() {
                return "notify";
            }
        };
        TypingAdapter clipboard = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                callOrder.add("clipboard"); return false;
            }
            @Override public String name() {
                return "clipboard";
            }
        };
        TypingAdapter robot = new TypingAdapter() {
            @Override public boolean canType() {
                return true;
            }
            @Override public boolean type(String text) {
                callOrder.add("robot"); return false;
            }
            @Override public String name() {
                return "robot";
            }
        };
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(notify, clipboard, robot), PROPS, e -> { });

        svc.paste("test");

        assertThat(callOrder).containsExactly("robot", "clipboard", "notify");
    }

    @Test
    void allAdaptersFailWithNullTextReturnsFalse() {
        List<Object> events = new ArrayList<>();
        TypingAdapter failRobot = adapter("robot", true, false);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(failRobot), PROPS, events::add);

        // Exercises text == null ? 0 : text.length() on failure path (line 75)
        boolean ok = svc.paste(null);

        assertThat(ok).isFalse();
        assertThat(events).anyMatch(e -> e instanceof AllTypingFallbacksFailedEvent);
    }

    @Test
    void emptyChainReturnsFalse() {
        List<Object> events = new ArrayList<>();
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(), PROPS, events::add);

        boolean ok = svc.paste("hello");

        assertThat(ok).isFalse();
        assertThat(events).anyMatch(e -> e instanceof AllTypingFallbacksFailedEvent);
    }

    @Test
    void unknownAdapterIsIncludedInChainAfterClipboardBeforeNotify() {
        List<String> callOrder = new ArrayList<>();
        TypingAdapter unknown = trackingAdapter("other", callOrder);
        TypingAdapter clipboard = trackingAdapter("clipboard", callOrder);
        TypingAdapter notify = trackingAdapter("notify", callOrder);
        StrategyChainTypingService svc = new StrategyChainTypingService(
                List.of(unknown, notify, clipboard), PROPS, e -> { });

        svc.paste("test");
        // Unknown adapters go after clipboard but before notify
        assertThat(callOrder).containsExactly("clipboard", "other", "notify");
    }

    @Test
    void allAdaptersFailWithDebugLogsPreview() {
        // Enable DEBUG on StrategyChainTypingService to hit LOG.isDebugEnabled() true branch
        Configurator.setLevel(StrategyChainTypingService.class, Level.DEBUG);
        try {
            List<Object> events = new ArrayList<>();
            TypingAdapter failRobot = adapter("robot", true, false);
            StrategyChainTypingService svc = new StrategyChainTypingService(
                    List.of(failRobot), PROPS, events::add);

            boolean ok = svc.paste("some text to preview");

            assertThat(ok).isFalse();
            assertThat(events).anyMatch(e -> e instanceof AllTypingFallbacksFailedEvent);
        } finally {
            Configurator.setLevel(StrategyChainTypingService.class, Level.INFO);
        }
    }

    private static TypingAdapter trackingAdapter(String adapterName, List<String> callOrder) {
        return new TypingAdapter() {
            @Override
            public boolean canType() {
                return true;
            }
            @Override
            public boolean type(String text) {
                callOrder.add(adapterName);
                return false;
            }
            @Override
            public String name() {
                return adapterName;
            }
        };
    }

    private static TypingAdapter adapter(String name, boolean canType, boolean typeResult) {
        return new TypingAdapter() {
            @Override public boolean canType() {
                return canType;
            }
            @Override public boolean type(String text) {
                return typeResult;
            }
            @Override public String name() {
                return name;
            }
        };
    }
}
