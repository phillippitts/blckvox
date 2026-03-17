package com.boombapcompile.blckvox.service.diagnostics;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class StartupTimingLoggerTest {

    @Test
    void onApplicationStartedWithWatchdogDoesNotThrow() {
        RecordingEngine vosk = new RecordingEngine("vosk");
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L);
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(vosk), props, event -> { });

        StartupTimingLogger logger = new StartupTimingLogger(List.of(vosk), watchdog);

        assertThatCode(() -> logger.onApplicationStarted(null)).doesNotThrowAnyException();
    }

    @Test
    void onApplicationStartedWithNullWatchdogDoesNotThrow() {
        RecordingEngine vosk = new RecordingEngine("vosk");

        StartupTimingLogger logger = new StartupTimingLogger(List.of(vosk), null);

        assertThatCode(() -> logger.onApplicationStarted(null)).doesNotThrowAnyException();
    }

    @Test
    void onApplicationStartedWithNoEnginesDoesNotThrow() {
        StartupTimingLogger logger = new StartupTimingLogger(List.of(), null);

        assertThatCode(() -> logger.onApplicationStarted(null)).doesNotThrowAnyException();
    }

    static class RecordingEngine implements SttEngine {
        final String name;

        RecordingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
