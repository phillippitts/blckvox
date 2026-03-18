package com.boombapcompile.blckvox.service.stt.vosk;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.SttConcurrencyProperties;
import com.boombapcompile.blckvox.exception.TranscriptionExceptionBuilder;
import com.boombapcompile.blckvox.service.stt.AbstractSttEngine;
import com.boombapcompile.blckvox.config.stt.VoskConfig;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.audio.AudioSilenceDetector;
import com.boombapcompile.blckvox.service.audio.SilenceDetector;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;
import com.boombapcompile.blckvox.service.stt.util.ConcurrencyScaler;
import com.boombapcompile.blckvox.service.stt.util.EngineEventPublisher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Vosk-based implementation of the SttEngine interface.
 *
 * <p>This engine provides offline speech-to-text transcription using the Vosk library.
 * It supports per-call recognizer creation for thread-safe concurrent transcription.
 *
 * <p>Thread-safe: All state-modifying operations are synchronized.
 * Transcription operations use per-call recognizers for safe concurrent access.
 *
 * <p>Audio contract: This engine expects raw PCM audio in the format defined by
 * com.boombapcompile.blckvox.service.audio.AudioFormat (16kHz, 16-bit, mono, little-endian).
 * Callers must validate/convert inputs (e.g., strip WAV headers) before invoking {@link #transcribe(byte[])}.
 */
@Component
public class VoskSttEngine extends AbstractSttEngine {

    private static final Logger LOG = LogManager.getLogger(VoskSttEngine.class);

    /**
     * Default concurrency limit for Vosk transcription when no configuration provided.
     */
    private static final int DEFAULT_CONCURRENCY_LIMIT = 4;

    /**
     * Default semaphore acquire timeout in milliseconds.
     */
    private static final int DEFAULT_ACQUIRE_TIMEOUT_MS = 1000;

    private final VoskConfig config;
    private final VoskModelProvider modelProvider;
    private final SilenceDetector silenceDetector;

    // Optional event publisher for watchdog events
    private ApplicationEventPublisher publisher;

    // Silence gap threshold for pause-based paragraph detection (milliseconds)
    private final int silenceGapMs;

    // Fallback model for non-Spring usage (basic constructor); null when using shared provider
    // @GuardedBy("lock")
    private org.vosk.Model localModel;

    public VoskSttEngine(VoskConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.modelProvider = null;
        this.silenceDetector = new AudioSilenceDetector();
        initializeDefaultGuard(SttEngineNames.VOSK, DEFAULT_CONCURRENCY_LIMIT, DEFAULT_ACQUIRE_TIMEOUT_MS);
        this.silenceGapMs = 0; // Disabled in basic constructor
    }

    /**
     * Spring-friendly constructor to inject concurrency limits and pause detection config.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public VoskSttEngine(VoskConfig config,
            VoskModelProvider modelProvider,
            SttConcurrencyProperties concurrencyProperties,
            ApplicationEventPublisher publisher,
            OrchestrationProperties orchestrationProperties,
            SilenceDetector silenceDetector,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ConcurrencyScaler concurrencyScaler) {
        this.config = Objects.requireNonNull(config, "config");
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider");
        this.silenceDetector = Objects.requireNonNull(silenceDetector, "silenceDetector");
        int max = Math.max(1, concurrencyProperties.getVoskMax());
        long timeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        initializeGuard(SttEngineNames.VOSK, max, timeoutMs, publisher, concurrencyProperties, concurrencyScaler);
        this.publisher = publisher;
        this.silenceGapMs = orchestrationProperties != null ?
                orchestrationProperties.getSilenceGapMs() : 0;
    }

    /**
     * Initializes the Vosk engine by loading the model (recognizer is created per transcription call).
     *
     * <p>Called by {@link #initialize()} within synchronized context. Supports reinitialization
     * after close by resetting the {@code closed} flag.
     *
     * @throws TranscriptionException if model loading fails
     */
    @Override
    protected void doInitialize() {
        LOG.info("Initializing Vosk engine: modelPath={}, sampleRate={}, maxAlternatives={}",
                config.modelPath(), config.sampleRate(), config.maxAlternatives());
        try {
            // Use shared model provider if available (Spring context), otherwise load directly
            if (modelProvider != null) {
                modelProvider.getModel(); // Ensure model is loaded
            } else {
                // Fallback for non-Spring usage (e.g., tests using basic constructor)
                this.localModel = new org.vosk.Model(config.modelPath());
            }
            closed = false; // Support reinitialization after close
            LOG.info("Vosk engine initialized");
        } catch (Throwable t) {
            safeCloseUnlocked();
            EngineEventPublisher.publishFailure(
                publisher,
                SttEngineNames.VOSK,
                "initialize failure",
                t,
                Map.of("modelPath", String.valueOf(config.modelPath()),
                       "sampleRate", String.valueOf(config.sampleRate()))
            );
            throw TranscriptionExceptionBuilder
                    .create("Failed to initialize Vosk")
                    .engine(SttEngineNames.VOSK)
                    .cause(t)
                    .metadata("modelPath", config.modelPath())
                    .metadata("sampleRate", config.sampleRate())
                    .build();
        }
    }

    /**
     * Transcribes audio data to text.
     *
     * <p>Creates a new recognizer per call for thread-safe operation.
     * Empty text results are valid (e.g., silence or unclear audio).
     *
     * @param audioData PCM audio data (16kHz, 16-bit, mono)
     * @return transcription result with text and confidence score
     * @throws IllegalArgumentException if audioData is null or empty
     * @throws TranscriptionException if engine not initialized or transcription fails
     */
    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        LOG.debug("VoskSttEngine.transcribe: received {} bytes of audio data", audioData.length);

        acquireTranscriptionLock();
        try {
            acquireGuard();
            try {
                org.vosk.Model localModel = getModelForTranscription();
                return transcribeWithModel(localModel, audioData);
            } finally {
                releaseGuard();
            }
        } finally {
            releaseTranscriptionLock();
        }
    }

    /**
     * Retrieves the model for transcription, ensuring the engine is initialized.
     *
     * <p>Thread-safe: synchronized to ensure consistent state.
     *
     * @return the Vosk model instance
     * @throws TranscriptionException if engine not initialized or closed
     */
    private org.vosk.Model getModelForTranscription() {
        ensureInitialized();
        if (modelProvider != null) {
            return modelProvider.getModel();
        }
        lock.lock();
        try {
            return this.localModel;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transcribes audio data using the provided Vosk model.
     *
     * <p>If pause detection is enabled ({@code silenceGapMs > 0}), splits audio at
     * detected silence boundaries and joins transcriptions with newlines.
     *
     * @param localModel the Vosk model to use for transcription
     * @param audioData PCM audio data to transcribe
     * @return transcription result with text, confidence, and metadata
     */
    private TranscriptionResult transcribeWithModel(org.vosk.Model localModel, byte[] audioData) {
        if (silenceGapMs > 0) {
            return transcribeWithPauseDetection(localModel, audioData);
        } else {
            return transcribeSingleSegment(localModel, audioData);
        }
    }

    /**
     * Transcribes a single audio segment without pause detection.
     *
     * @param localModel the Vosk model to use
     * @param audioData PCM audio data
     * @return transcription result
     */
    private TranscriptionResult transcribeSingleSegment(org.vosk.Model localModel, byte[] audioData) {
        try (org.vosk.Recognizer localRecognizer = new org.vosk.Recognizer(localModel, config.sampleRate())) {
            configureRecognizer(localRecognizer);
            String json = processAudioAndGetResult(localRecognizer, audioData);
            return parseJsonAndCreateResult(json);
        } catch (Exception e) {
            throw handleTranscriptionError(e, publisher, null);
        }
    }

    /**
     * Transcribes audio with pause detection, inserting newlines at silence boundaries.
     *
     * <p>Detects silence regions in the PCM audio buffer, splits at those boundaries,
     * transcribes each segment separately, and joins with newlines.
     *
     * @param localModel the Vosk model to use
     * @param audioData PCM audio data
     * @return transcription result with newlines at pause boundaries
     */
    private TranscriptionResult transcribeWithPauseDetection(org.vosk.Model localModel, byte[] audioData) {
        try {
            // Detect silence boundaries in the audio
            List<Integer> boundaries = silenceDetector.detectSilenceBoundaries(
                    audioData, silenceGapMs, config.sampleRate());

            if (boundaries.isEmpty()) {
                // No pauses detected, transcribe as single segment
                return transcribeSingleSegment(localModel, audioData);
            }

            // Process audio segments at detected boundaries
            SegmentAggregation result = transcribeAudioSegments(localModel, audioData, boundaries);

            // Join segments with newlines
            String fullText = String.join("\n", result.segments);
            double avgConfidence = result.segmentCount > 0
                    ? result.totalConfidence / result.segmentCount
                    : 1.0;

            LOG.debug("Vosk pause detection: split into {} segments", result.segmentCount);
            return TranscriptionResult.of(fullText, avgConfidence, getEngineName());

        } catch (Exception e) {
            throw handleTranscriptionError(e, publisher, null);
        }
    }

    /**
     * Transcribes audio segments split at silence boundaries.
     *
     * <p>Processes each segment between boundaries and the remaining audio after the last boundary.
     * Only includes non-blank transcriptions in the result.
     *
     * @param localModel the Vosk model to use for transcription
     * @param audioData the complete PCM audio data
     * @param boundaries list of byte offsets marking silence boundaries
     * @return aggregated results containing segments, total confidence, and count
     */
    private SegmentAggregation transcribeAudioSegments(
            org.vosk.Model localModel, byte[] audioData, List<Integer> boundaries) {
        List<String> segments = new ArrayList<>();
        double totalConfidence = 0.0;
        int segmentCount = 0;

        int start = 0;
        for (int boundary : boundaries) {
            if (boundary > start) {
                TranscriptionResult result = transcribeSegmentRange(localModel, audioData, start, boundary);
                if (!result.text().isBlank()) {
                    segments.add(result.text());
                    totalConfidence += result.confidence();
                    segmentCount++;
                }
                start = boundary;
            }
        }

        // Handle remaining audio after last boundary
        if (start < audioData.length) {
            TranscriptionResult result = transcribeSegmentRange(localModel, audioData, start, audioData.length);
            if (!result.text().isBlank()) {
                segments.add(result.text());
                totalConfidence += result.confidence();
                segmentCount++;
            }
        }

        return new SegmentAggregation(segments, totalConfidence, segmentCount);
    }

    /**
     * Transcribes a single audio segment defined by byte range.
     *
     * @param localModel the Vosk model to use
     * @param audioData the complete audio data
     * @param start start byte offset (inclusive)
     * @param end end byte offset (exclusive)
     * @return transcription result containing text and confidence
     */
    private TranscriptionResult transcribeSegmentRange(
            org.vosk.Model localModel, byte[] audioData, int start, int end) {
        byte[] segment = Arrays.copyOfRange(audioData, start, end);
        return transcribeSingleSegment(localModel, segment);
    }

    /**
     * Holds aggregated results from transcribing multiple audio segments.
     */
    private record SegmentAggregation(List<String> segments, double totalConfidence, int segmentCount) {
    }

    /**
     * Configures the recognizer with application settings.
     *
     * <p>Some Vosk versions may not expose certain configuration methods;
     * this method guards against NoSuchMethodError for forward compatibility.
     *
     * @param recognizer the recognizer to configure
     */
    private void configureRecognizer(org.vosk.Recognizer recognizer) {
        try {
            recognizer.setMaxAlternatives(config.maxAlternatives());
        } catch (NoSuchMethodError ignored) {
            // Older Vosk versions may not have this method - silently ignore
        }
    }

    /**
     * Processes audio data through the recognizer and retrieves the final JSON result.
     *
     * @param recognizer the Vosk recognizer instance
     * @param audioData PCM audio data to process
     * @return JSON string containing the transcription result
     */
    private String processAudioAndGetResult(org.vosk.Recognizer recognizer, byte[] audioData) {
        LOG.debug("VoskSttEngine: feeding {} bytes to Vosk recognizer", audioData.length);
        recognizer.acceptWaveForm(audioData, audioData.length);
        String json = recognizer.getFinalResult();
        LOG.debug("VoskSttEngine: Vosk returned JSON (length={} chars): {}", json.length(), json);
        return json;
    }

    /**
     * Parses Vosk JSON response and creates a TranscriptionResult.
     *
     * @param json JSON string from Vosk recognizer
     * @return TranscriptionResult containing text, confidence, and engine name
     */
    private TranscriptionResult parseJsonAndCreateResult(String json) {
        VoskJsonParser.VoskTranscription transcription = VoskJsonParser.parse(json);
        LOG.debug("VoskSttEngine: parsed transcription text='{}' (length={} chars), confidence={}",
                 transcription.text(), transcription.text().length(), transcription.confidence());
        // Empty text is valid (e.g., silence or unclear audio)
        return TranscriptionResult.of(transcription.text(), transcription.confidence(), getEngineName());
    }

    @Override
    public String getEngineName() {
        return SttEngineNames.VOSK;
    }

    /**
     * Closes the Vosk engine and releases JNI resources.
     *
     * <p>Called by {@link #close()} within synchronized context.
     */
    @Override
    protected void doClose() {
        safeCloseUnlocked();
        LOG.info("Vosk engine closed");
    }

    /**
     * Closes JNI resources without acquiring lock.
     *
     * <p>Caller must hold lock. Used internally during cleanup.
     *
     * <p>GuardedBy: lock (caller must hold lock)
     */
    private void safeCloseUnlocked() {
        // Only close the local model; shared model lifecycle is managed by VoskModelProvider
        if (localModel != null) {
            try {
                localModel.close();
            } catch (Throwable t) {
                LOG.warn("Error closing model", t);
            }
            localModel = null;
        }
    }

}
