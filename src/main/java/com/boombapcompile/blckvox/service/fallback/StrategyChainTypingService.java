package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.config.properties.TypingProperties;
import com.boombapcompile.blckvox.service.fallback.event.AllTypingFallbacksFailedEvent;
import com.boombapcompile.blckvox.service.fallback.event.TypingFallbackEvent;
import com.boombapcompile.blckvox.util.LogSanitizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tries adapters in order until one succeeds. Defaults: Robot -> Clipboard -> Notify.
 */
@Service
public class StrategyChainTypingService implements TypingService {
    private static final Logger LOG = LogManager.getLogger(StrategyChainTypingService.class);

    private final List<TypingAdapter> chain;
    private final ApplicationEventPublisher publisher;

    public StrategyChainTypingService(List<TypingAdapter> adapters, TypingProperties props,
                                      ApplicationEventPublisher publisher) {
        Objects.requireNonNull(props);
        this.publisher = Objects.requireNonNull(publisher);
        // Order adapters: Robot (if present) -> Clipboard -> unknown adapters -> Notify
        List<TypingAdapter> ordered = new ArrayList<>();
        List<TypingAdapter> unknown = new ArrayList<>();
        TypingAdapter notify = null;
        TypingAdapter clipboard = null;
        for (TypingAdapter a : adapters) {
            if ("robot".equalsIgnoreCase(a.name())) {
                ordered.add(a);
            } else if ("clipboard".equalsIgnoreCase(a.name())) {
                clipboard = a;
            } else if ("notify".equalsIgnoreCase(a.name())) {
                notify = a;
            } else {
                LOG.warn("Unknown typing adapter '{}' — appending to fallback chain", a.name());
                unknown.add(a);
            }
        }
        if (clipboard != null) {
            ordered.add(clipboard);
        }
        ordered.addAll(unknown);
        if (notify != null) {
            ordered.add(notify);
        }
        this.chain = List.copyOf(ordered);
    }

    @Override
    public boolean paste(String text) {
        for (TypingAdapter a : chain) {
            if (!a.canType()) {
                LOG.debug("Skipping adapter {}: unavailable", a.name());
                continue;
            }
            try {
                boolean ok = a.type(text);
                if (ok) {
                    LOG.info("Typed via {} (chars={})", a.name(), text == null ? 0 : text.length());
                    return true;
                } else {
                    // Publish non-PII fallback event for observability
                    publisher.publishEvent(new TypingFallbackEvent(a.name(), "type returned false", Instant.now()));
                }
            } catch (RuntimeException e) {
                LOG.warn("Adapter {} failed: {}", a.name(), e.toString());
                publisher.publishEvent(new TypingFallbackEvent(a.name(), e.getClass().getSimpleName(), Instant.now()));
            }
        }
        // Privacy-safe: only log character count at INFO level
        LOG.info("No typing adapters succeeded (chars={})", text == null ? 0 : text.length());
        if (LOG.isDebugEnabled()) {
            String preview = LogSanitizer.truncate(text, 120);
            LOG.debug("Failed text preview: '{}'", preview);
        }
        publisher.publishEvent(new AllTypingFallbacksFailedEvent("no adapters succeeded", Instant.now()));
        return false;
    }
}
