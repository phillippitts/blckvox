package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Orchestrates text delivery to active applications using graceful fallback strategies.
 *
 * <p>This manager listens for transcription completion events and attempts to deliver
 * the transcribed text using a chain of fallback mechanisms:
 * <ol>
 *   <li>Robot API (direct keystroke simulation - requires Accessibility permission)</li>
 *   <li>Clipboard paste (fallback when Robot unavailable)</li>
 *   <li>User notification (last resort when all typing methods fail)</li>
 * </ol>
 *
 * <p>The fallback chain ensures text delivery succeeds even without macOS Accessibility
 * permissions, providing graceful degradation for better user experience.
 *
 * <p><b>Thread Safety:</b> This Spring-managed singleton is stateless and thread-safe.
 * Multiple transcription events can be processed concurrently.
 *
 * @see TranscriptionCompletedEvent
 * @see TypingService
 * @since 1.0
 */
@Service
public class FallbackManager {
    private static final Logger LOG = LogManager.getLogger(FallbackManager.class);

    private final TypingService typingService;

    /**
     * Constructs a new FallbackManager with the specified typing service.
     *
     * @param typingService the typing service implementing fallback chain logic
     * @throws NullPointerException if typingService is null
     */
    public FallbackManager(TypingService typingService) {
        this.typingService = Objects.requireNonNull(typingService);
    }

    /**
     * Handles transcription completion events and delivers text to active application.
     *
     * <p>This method is invoked asynchronously by Spring's event system when
     * {@link TranscriptionCompletedEvent} is published. It attempts to paste the
     * transcribed text using the fallback chain configured in {@link TypingService}.
     *
     * <p>If all fallback mechanisms fail, a warning is logged with the text length
     * (not the full text, for privacy).
     *
     * <p><b>Performance:</b> This method executes on Spring's event thread pool and
     * should complete quickly (typically &lt;100ms). Blocking operations are handled
     * by the {@link TypingService} implementation.
     *
     * @param evt the transcription completion event containing result and metadata
     * @see TranscriptionCompletedEvent
     * @see TypingService#paste(String)
     */
    @EventListener
    public void onTranscription(TranscriptionCompletedEvent evt) {
        if (evt.result().isFailure()) {
            LOG.debug("Skipping paste for failed transcription: {}", evt.result().failureReason());
            return;
        }
        String text = evt.result().text();
        LOG.info("[paste] engine={}, chars={}", evt.engineUsed(), text.length());
        LOG.debug("[paste] text='{}'", text);
        boolean ok = typingService.paste(text);
        if (!ok) {
            LOG.warn("All typing fallbacks failed (chars={})", text.length());
        }
    }
}
