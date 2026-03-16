package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractSttEngineTest {

    // --- Testable concrete subclass ---

    static class TestableEngine extends AbstractSttEngine {
        boolean doInitCalled = false;
        boolean doCloseCalled = false;
        RuntimeException initException = null;

        @Override
        protected void doInitialize() {
            if (initException != null) {
                throw initException;
            }
            doInitCalled = true;
            closed = false;
        }

        @Override
        protected void doClose() {
            doCloseCalled = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            ensureInitialized();
            return TranscriptionResult.of("test", 1.0, getEngineName());
        }

        @Override
        public String getEngineName() {
            return "testable";
        }

        TranscriptionException testHandleError(Exception e) {
            return handleTranscriptionError(e, null, null);
        }
    }

    @Test
    void initializeSetsHealthyTrue() {
        TestableEngine engine = new TestableEngine();

        engine.initialize();

        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void doubleInitializeIsIdempotent() {
        TestableEngine engine = new TestableEngine();

        engine.initialize();
        assertThat(engine.doInitCalled).isTrue();

        // Reset the flag to detect a second doInitialize() call
        engine.doInitCalled = false;
        engine.initialize();

        assertThat(engine.doInitCalled).isFalse();
    }

    @Test
    void closeAfterInitSetsHealthyFalse() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        engine.close();

        assertThat(engine.isHealthy()).isFalse();
        assertThat(engine.doCloseCalled).isTrue();
    }

    @Test
    void doubleCloseIsIdempotent() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        engine.close();
        assertThat(engine.doCloseCalled).isTrue();

        // Reset the flag to detect a second doClose() call
        engine.doCloseCalled = false;
        engine.close();

        // doClose() should not have been called again
        assertThat(engine.doCloseCalled).isFalse();
    }

    @Test
    void ensureInitializedThrowsWhenNotInitialized() {
        TestableEngine engine = new TestableEngine();

        assertThatThrownBy(() -> engine.transcribe(new byte[10]))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void ensureInitializedThrowsWhenClosed() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        engine.close();

        assertThatThrownBy(() -> engine.transcribe(new byte[10]))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void handleTranscriptionErrorPreservesTranscriptionException() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        TranscriptionException original = new TranscriptionException("original error", "testable");

        assertThatThrownBy(() -> engine.testHandleError(original))
                .isSameAs(original);
    }

    @Test
    void handleTranscriptionErrorWrapsOtherExceptions() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        RuntimeException cause = new RuntimeException("something went wrong");

        assertThatThrownBy(() -> engine.testHandleError(cause))
                .isInstanceOf(TranscriptionException.class)
                .hasCause(cause)
                .hasMessageContaining("testable")
                .hasMessageContaining("something went wrong");
    }

    @Test
    void isHealthyReturnsFalseWhenNeverInitialized() {
        TestableEngine engine = new TestableEngine();
        assertThat(engine.isHealthy()).isFalse();
    }

    @Test
    void handleTranscriptionErrorWithPublisherPublishesEvent() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;

        assertThatThrownBy(() -> engine.handleTranscriptionError(
                new RuntimeException("test"), publisher, Map.of("key", "val")))
                .isInstanceOf(TranscriptionException.class);

        // Publisher should have received a failure event
        assertThat(events).hasSize(1);
    }

    @Test
    void handleTranscriptionErrorWithPublisherPreservesTranscriptionException() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;

        TranscriptionException original = new TranscriptionException("original", "testable");
        assertThatThrownBy(() -> engine.handleTranscriptionError(original, publisher, null))
                .isSameAs(original);

        // Publisher should still get the event
        assertThat(events).hasSize(1);
    }

    @Test
    void acquireTranscriptionLockThrowsWhenClosed() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        engine.close();

        assertThatThrownBy(engine::acquireTranscriptionLock)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("closing or closed");
    }

    @Test
    void reinitializeAfterCloseWorks() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        assertThat(engine.isHealthy()).isTrue();

        engine.close();
        assertThat(engine.isHealthy()).isFalse();

        // Reinitialize — doInitialize resets closed=false
        engine.initialize();
        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void acquireAndReleaseTranscriptionLockSucceeds() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        // Should not throw — engine is initialized and not closed
        engine.acquireTranscriptionLock();
        engine.releaseTranscriptionLock();

        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void initializeWhenInitializedAndClosedReInitializes() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        assertThat(engine.isHealthy()).isTrue();

        // Directly set closed=true while initialized remains true
        // This covers the initialized=true && closed=true branch at line 94
        engine.closed = true;
        engine.doInitCalled = false;

        engine.initialize();
        assertThat(engine.doInitCalled).isTrue();
        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void initializeFailureDoesNotSetInitialized() {
        TestableEngine engine = new TestableEngine();
        engine.initException = new RuntimeException("init boom");

        assertThatThrownBy(engine::initialize).isInstanceOf(RuntimeException.class);
        assertThat(engine.isHealthy()).isFalse();
    }

    @Test
    void ensureInitializedThrowsWhenInitializedButClosed() {
        // Covers the `closed` branch in `!initialized || closed` at line 248
        // Need initialized=true && closed=true so !initialized is false,
        // forcing evaluation of the `closed` operand
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        engine.closed = true; // simulate without going through close()

        assertThatThrownBy(() -> engine.transcribe(new byte[10]))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void isHealthyReturnsFalseWhenInitializedButClosed() {
        // Covers the `!closed` false branch in `initialized && !closed` at line 140
        // Need initialized=true && closed=true so initialized is true,
        // forcing evaluation of the `!closed` operand
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        engine.closed = true;

        assertThat(engine.isHealthy()).isFalse();
    }
}
