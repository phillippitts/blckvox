package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.SttEngineNames;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineSelectionStrategyTest {

    // --- Test double ---

    static class StubEngine implements SttEngine {
        final String name;
        boolean healthy;

        StubEngine(String name, boolean healthy) {
            this.name = name;
            this.healthy = healthy;
        }

        @Override
        public void initialize() {
        }

        @Override
        public TranscriptionResult transcribe(byte[] data) {
            return null;
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public void close() {
        }
    }

    @Test
    void selectsPrimaryWhenBothHealthy() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, true);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(vosk);
    }

    @Test
    void fallsBackToSecondaryWhenPrimaryUnhealthy() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, false);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(whisper);
    }

    @Test
    void throwsWhenAllUnavailable() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, false);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, false);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper), watchdog, props);

        assertThatThrownBy(strategy::selectEngine)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("All engines unavailable");
    }

    @Test
    void respectsWhisperPrimary() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, true);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.WHISPER, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(whisper);
    }

    @Test
    void fallsBackWhenPrimaryDisabledByWatchdog() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, true);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(SttEngineNames.VOSK)).thenReturn(false);
        when(watchdog.isEngineEnabled(SttEngineNames.WHISPER)).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(whisper);
    }

    @Test
    void threeEngineListSelectsFirstHealthy() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, false);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, false);
        StubEngine extra = new StubEngine("extra", true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk, whisper, extra), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(extra);
    }

    @Test
    void singleEngineListWorks() {
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(vosk), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(vosk);
    }

    @Test
    void emptyEngineListRejected() {
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        assertThatThrownBy(() -> new EngineSelectionStrategy(List.of(), watchdog, props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void priorityReordersPrimaryFirst() {
        // Pass whisper first in the list but vosk as primary — vosk should be selected
        StubEngine vosk = new StubEngine(SttEngineNames.VOSK, true);
        StubEngine whisper = new StubEngine(SttEngineNames.WHISPER, true);
        SttEngineWatchdog watchdog = mock(SttEngineWatchdog.class);
        when(watchdog.isEngineEnabled(anyString())).thenReturn(true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK, 1000, 200, 120);

        // whisper is first in list, but vosk should still be selected as primary
        EngineSelectionStrategy strategy = new EngineSelectionStrategy(
                List.of(whisper, vosk), watchdog, props);

        assertThat(strategy.selectEngine()).isSameAs(vosk);
    }
}
