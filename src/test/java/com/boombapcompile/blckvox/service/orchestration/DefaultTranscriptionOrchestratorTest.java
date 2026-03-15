package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.config.properties.OrchestrationProperties.PrimaryEngine;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultTranscriptionOrchestrator}.
 *
 * <p>Uses fake implementations for all collaborators (no Spring context required).
 * Audio data is crafted to either be silent or non-silent based on the
 * {@link com.boombapcompile.blckvox.service.audio.AudioSilenceDetector} algorithm.
 */
class DefaultTranscriptionOrchestratorTest {

    private CapturingPublisher publisher;
    private StubReconciliationService reconciliation;
    private TranscriptionMetricsPublisher metrics;

    @BeforeEach
    void setUp() {
        publisher = new CapturingPublisher();
        reconciliation = new StubReconciliationService();
        metrics = TranscriptionMetricsPublisher.NOOP;
    }

    // --- Constructor validation tests ---

    @Test
    void shouldRejectNullProps() {
        EngineSelectionStrategy selector = createEngineSelector("vosk text", "whisper text");
        assertThatThrownBy(() -> new DefaultTranscriptionOrchestrator(
                null, publisher, reconciliation, selector, metrics))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("props");
    }

    @Test
    void shouldRejectNullPublisher() {
        EngineSelectionStrategy selector = createEngineSelector("vosk text", "whisper text");
        assertThatThrownBy(() -> new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), null, reconciliation, selector, metrics))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("publisher");
    }

    @Test
    void shouldRejectNullReconciliation() {
        EngineSelectionStrategy selector = createEngineSelector("vosk text", "whisper text");
        assertThatThrownBy(() -> new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), publisher, null, selector, metrics))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reconciliation");
    }

    @Test
    void shouldRejectNullEngineSelector() {
        assertThatThrownBy(() -> new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), publisher, reconciliation, null, metrics))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engineSelector");
    }

    @Test
    void shouldRejectNullMetricsPublisher() {
        EngineSelectionStrategy selector = createEngineSelector("vosk text", "whisper text");
        assertThatThrownBy(() -> new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), publisher, reconciliation, selector, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metricsPublisher");
    }

    // --- Null PCM rejection ---

    @Test
    void shouldRejectNullPcm() {
        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "vosk text", "whisper text");
        assertThatThrownBy(() -> orchestrator.transcribe(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pcm");
    }

    // --- Silent audio detection ---

    @Test
    void shouldSkipTranscriptionForSilentAudio() {
        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "vosk text", "whisper text");

        // All-zero PCM data is silence (RMS = 0, well below threshold of 200)
        byte[] silentPcm = new byte[3200]; // 100ms of silence at 16kHz mono 16-bit

        orchestrator.transcribe(silentPcm);

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().text()).isEmpty();
        assertThat(event.engineUsed()).isEqualTo("silent");
    }

    // --- Single-engine transcription (reconciliation disabled) ---

    @Test
    void shouldTranscribeWithSingleEngineWhenReconciliationDisabled() {
        reconciliation.enabled = false;

        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "hello world", "whisper default");

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().text()).isEqualTo("hello world");
        assertThat(event.engineUsed()).isEqualTo("vosk");
    }

    // --- Single-engine failure handling ---

    @Test
    void shouldPublishFailureResultWhenSingleEngineThrowsTranscriptionException() {
        reconciliation.enabled = false;

        // Create an orchestrator where the primary (vosk) engine always fails
        SttEngine failingVosk = new OrchestrationTestDoubles.FailingEngine("vosk");
        SttEngine healthyWhisper = new OrchestrationTestDoubles.FakeEngine("whisper", "whisper ok");
        EngineSelectionStrategy selector = createEngineSelectorWith(failingVosk, healthyWhisper);

        DefaultTranscriptionOrchestrator orchestrator = new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), publisher, reconciliation, selector, metrics);

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().text()).isEmpty();
        assertThat(event.result().isFailure()).isTrue();
    }

    // --- Reconciliation mode ---

    @Test
    void shouldTranscribeWithReconciliationWhenEnabled() {
        reconciliation.enabled = true;
        reconciliation.result = TranscriptionResult.of("reconciled text", 0.95, "reconciled");
        reconciliation.strategy = "CONFIDENCE";

        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "vosk text", "whisper text");

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().text()).isEqualTo("reconciled text");
        assertThat(event.engineUsed()).isEqualTo("reconciled");
    }

    @Test
    void shouldPublishFailureWhenReconciliationThrowsTranscriptionException() {
        reconciliation.enabled = true;
        reconciliation.throwTranscriptionException = true;

        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "vosk text", "whisper text");

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().isFailure()).isTrue();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
    }

    @Test
    void shouldPublishFailureWhenReconciliationThrowsRuntimeException() {
        reconciliation.enabled = true;
        reconciliation.throwRuntimeException = true;

        DefaultTranscriptionOrchestrator orchestrator = createOrchestrator(
                200, "vosk text", "whisper text");

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().isFailure()).isTrue();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
    }

    @Test
    void shouldPublishFailureWhenSingleEngineThrowsRuntimeException() {
        reconciliation.enabled = false;

        SttEngine runtimeFailingEngine = new SttEngine() {
            @Override public void initialize() { }
            @Override public TranscriptionResult transcribe(byte[] audioData) {
                throw new RuntimeException("unexpected NPE");
            }
            @Override public String getEngineName() { return "vosk"; }
            @Override public boolean isHealthy() { return true; }
            @Override public void close() { }
        };
        SttEngine healthyWhisper = new OrchestrationTestDoubles.FakeEngine("whisper", "whisper ok");
        EngineSelectionStrategy selector = createEngineSelectorWith(runtimeFailingEngine, healthyWhisper);

        DefaultTranscriptionOrchestrator orchestrator = new DefaultTranscriptionOrchestrator(
                orchestrationProps(200), publisher, reconciliation, selector, metrics);

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().isFailure()).isTrue();
    }

    @Test
    void shouldPublishFailureWithUnknownEngineWhenSelectionFails() {
        reconciliation.enabled = false;

        // Create a selector that throws TranscriptionException before engine assignment
        SttEngine fakeVosk = new OrchestrationTestDoubles.FakeEngine("vosk", "text");
        SttEngine fakeWhisper = new OrchestrationTestDoubles.FakeEngine("whisper", "text");
        SttWatchdogProperties watchdogProps = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        // Use a real watchdog where both engines are disabled
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(fakeVosk, fakeWhisper), watchdogProps, publisher) {
            @Override
            public boolean isEngineEnabled(String engine) {
                return false; // both disabled → selectEngine() throws
            }
        };
        OrchestrationProperties orchProps = orchestrationProps(200);
        EngineSelectionStrategy selector = new EngineSelectionStrategy(
                fakeVosk, fakeWhisper, watchdog, orchProps);

        DefaultTranscriptionOrchestrator orchestrator = new DefaultTranscriptionOrchestrator(
                orchProps, publisher, reconciliation, selector, metrics);

        orchestrator.transcribe(loudPcm());

        assertThat(publisher.events).hasSize(1);
        TranscriptionCompletedEvent event = publisher.events.get(0);
        assertThat(event.result().isFailure()).isTrue();
        // Engine should be "unknown" since engine was null when exception was thrown
        assertThat(event.engineUsed()).isEqualTo("unknown");
    }

    // --- Helpers ---

    private DefaultTranscriptionOrchestrator createOrchestrator(
            int silenceThreshold, String voskText, String whisperText) {
        EngineSelectionStrategy selector = createEngineSelector(voskText, whisperText);
        return new DefaultTranscriptionOrchestrator(
                orchestrationProps(silenceThreshold),
                publisher,
                reconciliation,
                selector,
                metrics
        );
    }

    private static OrchestrationProperties orchestrationProps(int silenceThreshold) {
        return new OrchestrationProperties(PrimaryEngine.VOSK, 1000, silenceThreshold);
    }

    /**
     * Creates a real {@link EngineSelectionStrategy} backed by fake engines and a real watchdog
     * with all engines in healthy state.
     */
    private EngineSelectionStrategy createEngineSelector(String voskText, String whisperText) {
        SttEngine fakeVosk = new OrchestrationTestDoubles.FakeEngine("vosk", voskText);
        SttEngine fakeWhisper = new OrchestrationTestDoubles.FakeEngine("whisper", whisperText);
        return createEngineSelectorWith(fakeVosk, fakeWhisper);
    }

    /**
     * Creates a real {@link EngineSelectionStrategy} with arbitrary engine implementations.
     */
    private EngineSelectionStrategy createEngineSelectorWith(SttEngine vosk, SttEngine whisper) {
        SttWatchdogProperties watchdogProps = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(vosk, whisper), watchdogProps, publisher);
        OrchestrationProperties orchProps = orchestrationProps(200);
        return new EngineSelectionStrategy(vosk, whisper, watchdog, orchProps);
    }

    /**
     * Creates PCM audio data with high-amplitude samples to avoid silence detection.
     * Fills 3200 bytes (100ms at 16kHz, 16-bit, mono) with 16-bit samples of value 10000
     * (little-endian), producing RMS well above any reasonable silence threshold.
     */
    private static byte[] loudPcm() {
        byte[] pcm = new byte[3200];
        short amplitude = 10_000;
        for (int i = 0; i < pcm.length; i += 2) {
            pcm[i] = (byte) (amplitude & 0xFF);
            pcm[i + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        return pcm;
    }

    // --- Test doubles ---

    /**
     * Captures published Spring events for assertion.
     */
    private static class CapturingPublisher implements org.springframework.context.ApplicationEventPublisher {
        final List<TranscriptionCompletedEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof TranscriptionCompletedEvent tce) {
                events.add(tce);
            }
        }
    }

    /**
     * Stub ReconciliationService that can be configured to return results or throw exceptions.
     */
    private static class StubReconciliationService implements ReconciliationService {
        boolean enabled = false;
        TranscriptionResult result = TranscriptionResult.of("stub", 1.0, "reconciled");
        String strategy = "SIMPLE";
        boolean throwTranscriptionException = false;
        boolean throwRuntimeException = false;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public TranscriptionResult reconcile(byte[] pcm) {
            if (throwTranscriptionException) {
                throw new TranscriptionException("Simulated reconciliation failure");
            }
            if (throwRuntimeException) {
                throw new RuntimeException("Simulated runtime failure");
            }
            return result;
        }

        @Override
        public String getStrategy() {
            return strategy;
        }
    }
}
