package com.boombapcompile.blckvox.service.stt.vosk;

import com.boombapcompile.blckvox.config.stt.VoskConfig;
import com.boombapcompile.blckvox.service.audio.capture.PcmChunkCapturedEvent;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * Feeds PCM chunks to a Vosk recognizer incrementally and publishes partial text events.
 *
 * <p>Manages a session-scoped recognizer: created when recording starts, destroyed when
 * recording stops. Uses its own Vosk model instance separate from {@link VoskSttEngine}.
 *
 * @since 1.3
 */
@Service
@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")
public class VoskStreamingService {

    private static final Logger LOG = LogManager.getLogger(VoskStreamingService.class);

    private final VoskConfig config;
    private final VoskModelProvider modelProvider;
    private final ApplicationEventPublisher publisher;

    private final Object recognizerLock = new Object();
    private org.vosk.Recognizer recognizer;

    public VoskStreamingService(VoskConfig config, VoskModelProvider modelProvider,
                                ApplicationEventPublisher publisher) {
        this.config = config;
        this.modelProvider = modelProvider;
        this.publisher = publisher;
    }

    @EventListener
    public void onStateChanged(ApplicationStateChangedEvent event) {
        if (event.current() == ApplicationState.RECORDING) {
            openRecognizer();
        } else {
            closeRecognizer();
        }
    }

    @Async("eventExecutor")
    @EventListener
    public void onPcmChunk(PcmChunkCapturedEvent event) {
        synchronized (recognizerLock) {
            if (recognizer == null) {
                return;
            }
            boolean accepted = recognizer.acceptWaveForm(event.pcmData(), event.length());
            if (accepted) {
                String text = parseTextField(recognizer.getResult(), "text");
                if (!text.isEmpty()) {
                    LOG.debug("[live-caption] final: '{}'", text);
                    publisher.publishEvent(new VoskPartialResultEvent(text, true));
                }
            } else {
                String partial = parseTextField(recognizer.getPartialResult(), "partial");
                if (!partial.isEmpty()) {
                    LOG.debug("[live-caption] partial: '{}'", partial);
                    publisher.publishEvent(new VoskPartialResultEvent(partial, false));
                }
            }
        }
    }

    private void openRecognizer() {
        synchronized (recognizerLock) {
            closeRecognizerLocked();
            try {
                org.vosk.Model model = modelProvider.getModel();
                recognizer = new org.vosk.Recognizer(model, config.sampleRate());
                LOG.info("Vosk streaming recognizer opened");
            } catch (java.io.IOException | RuntimeException e) {
                LOG.error("Failed to open Vosk streaming recognizer", e);
            }
        }
    }

    private void closeRecognizer() {
        synchronized (recognizerLock) {
            closeRecognizerLocked();
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (recognizerLock) {
            closeRecognizerLocked();
            // Model lifecycle is managed by VoskModelProvider
        }
    }

    private void closeRecognizerLocked() {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception e) {
                LOG.debug("Error closing streaming recognizer: {}", e.getMessage());
            }
            recognizer = null;
            LOG.debug("Vosk streaming recognizer closed");
        }
    }

    private static String parseTextField(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return new JSONObject(json).optString(field, "").trim();
        } catch (Exception e) {
            LOG.debug("Failed to parse Vosk streaming JSON: {}", e.getMessage());
            return "";
        }
    }
}
