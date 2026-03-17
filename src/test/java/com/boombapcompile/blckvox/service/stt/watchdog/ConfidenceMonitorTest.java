package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceMonitorTest {

    private final SttWatchdogProperties props = new SttWatchdogProperties(
            true, 60, 3, 10, false, 60000L, 0.3, 10, 5, 1000L, 2.0, 60000L);

    @Test
    void isTrackedReturnsFalseForUnregisteredEngine() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.isTracked("unknown")).isFalse();
    }

    @Test
    void isTrackedReturnsTrueAfterRegistration() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        assertThat(monitor.isTracked("vosk")).isTrue();
    }

    @Test
    void recordReturnsNullWhenNotEnoughSamples() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        // minSamples is 5, record only 4
        for (int i = 0; i < 4; i++) {
            assertThat(monitor.record("vosk", 0.8)).isNull();
        }
    }

    @Test
    void recordReturnsEvaluationWhenEnoughSamples() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        // minSamples is 5
        ConfidenceMonitor.Evaluation eval = null;
        for (int i = 0; i < 5; i++) {
            eval = monitor.record("vosk", 0.8);
        }

        assertThat(eval).isNotNull();
        assertThat(eval.degraded()).isFalse(); // 0.8 > 0.3 threshold
        assertThat(eval.average()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void recordDetectsDegradation() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        // Record low confidence scores
        ConfidenceMonitor.Evaluation eval = null;
        for (int i = 0; i < 5; i++) {
            eval = monitor.record("vosk", 0.1); // Below 0.3 threshold
        }

        assertThat(eval).isNotNull();
        assertThat(eval.degraded()).isTrue();
    }

    @Test
    void clearOnRecoveryClearsWindow() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        monitor.record("vosk", 0.8);
        monitor.record("vosk", 0.9);
        monitor.clearOnRecovery("vosk");

        assertThat(monitor.averageConfidence("vosk")).isEqualTo(0.0);
    }

    @Test
    void averageConfidenceReturnsZeroForEmptyWindow() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        assertThat(monitor.averageConfidence("vosk")).isEqualTo(0.0);
    }

    @Test
    void averageConfidenceReturnsZeroForUnregisteredEngine() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.averageConfidence("unknown")).isEqualTo(0.0);
    }

    @Test
    void formattedSummaryReturnsEmptyForUnregisteredEngine() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.formattedSummary("unknown")).isEmpty();
    }

    @Test
    void formattedSummaryReturnsEmptyForEmptyWindow() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        assertThat(monitor.formattedSummary("vosk")).isEmpty();
    }

    @Test
    void formattedSummaryReturnsFormattedString() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        monitor.record("vosk", 0.8);

        String summary = monitor.formattedSummary("vosk");
        assertThat(summary).contains("conf=");
        assertThat(summary).contains("/1)");
    }

    @Test
    void recordReturnsNullForUnregisteredEngine() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        assertThat(monitor.record("unknown", 0.8)).isNull();
    }

    @Test
    void windowSizeIsRespected() {
        // windowSize is 10, record 15 items
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        for (int i = 0; i < 15; i++) {
            monitor.record("vosk", 0.5);
        }

        assertThat(monitor.getWindow("vosk")).hasSize(10);
    }
}
