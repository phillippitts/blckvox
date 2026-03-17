package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.SttEngine;

/**
 * Shared test doubles for orchestration service tests.
 *
 * <p>Provides fake STT engine implementations for hermetic testing without
 * real Vosk or Whisper dependencies.
 */
final class OrchestrationTestDoubles {

    private OrchestrationTestDoubles() {
        // Utility class
    }

    /**
     * Fake engine that returns a fixed text result instantly.
     */
    static final class FakeEngine implements SttEngine {
        private final String name;
        private final String text;
        private final double confidence;
        private boolean initialized;

        FakeEngine(String name, String text) {
            this(name, text, 1.0);
        }

        FakeEngine(String name, String text, double confidence) {
            this.name = name;
            this.text = text;
            this.confidence = confidence;
            this.initialized = true;
        }

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            if (!initialized) {
                throw new TranscriptionException("not initialized", name);
            }
            return TranscriptionResult.of(text, confidence, name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return initialized;
        }

        @Override
        public void close() {
            this.initialized = false;
        }
    }

    /**
     * Engine that sleeps for a configured duration before returning a result.
     * Useful for testing timeouts and parallelism.
     */
    static final class SlowEngine implements SttEngine {
        private final String name;
        private final long delayMs;
        private boolean initialized = true;

        SlowEngine(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!initialized) {
                throw new TranscriptionException("not initialized", name);
            }
            return TranscriptionResult.of("slow", 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return initialized;
        }

        @Override
        public void close() {
            this.initialized = false;
        }
    }

    /**
     * Engine that always throws TranscriptionException.
     * Useful for testing error handling.
     */
    static final class FailingEngine implements SttEngine {
        private final String name;
        private boolean initialized = true;

        FailingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            throw new TranscriptionException("Simulated engine failure", name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return initialized;
        }

        @Override
        public void close() {
            this.initialized = false;
        }
    }
}
