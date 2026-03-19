package com.boombapcompile.blckvox.service.stt.watchdog;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceMonitorTest {

    private final SttWatchdogProperties props = new SttWatchdogProperties(
            true, 60, 3, 10, 60000L, 0.3, 10, 5, 1000L, 2.0, 60000L, 5);

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

    @Test
    void graceSkipsSamplesAfterRecovery() {
        // graceTranscriptions=5, so first 5 records after clearOnRecovery() return null
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        monitor.clearOnRecovery("vosk");

        for (int i = 0; i < 5; i++) {
            assertThat(monitor.record("vosk", 0.1))
                    .as("Grace sample %d should be skipped", i)
                    .isNull();
        }
        // Window should still be empty — grace samples are not recorded
        assertThat(monitor.getWindow("vosk")).isEmpty();
    }

    @Test
    void graceCountdownExpiresThenRecordsNormally() {
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        monitor.clearOnRecovery("vosk");

        // Burn through 5 grace samples
        for (int i = 0; i < 5; i++) {
            monitor.record("vosk", 0.1);
        }

        // 6th sample should be recorded normally
        monitor.record("vosk", 0.8);
        assertThat(monitor.getWindow("vosk")).hasSize(1);
        assertThat(monitor.averageConfidence("vosk")).isCloseTo(0.8,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void noGraceOnFreshRegistration() {
        // register() initializes grace counter to 0 (no grace on first registration)
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        // First record should be tracked immediately, not skipped
        monitor.record("vosk", 0.8);
        assertThat(monitor.getWindow("vosk")).hasSize(1);
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void recordReturnsEvaluationExactlyAtMinSamples() {
        // minSamples=5. 4th record → null, 5th record → non-null
        // Kills < to <= on L70 (window.size() < minSamples)
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        for (int i = 0; i < 4; i++) {
            assertThat(monitor.record("vosk", 0.8)).isNull();
        }
        // 5th sample: window.size()=5, 5 < 5 is false → returns Evaluation
        ConfidenceMonitor.Evaluation eval = monitor.record("vosk", 0.8);
        assertThat(eval).isNotNull();
        assertThat(eval.average()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void evaluationDegradedBoundaryExactlyAtThreshold() {
        // blacklistThreshold=0.3. avg=0.3 exactly → avg < 0.3 is false → NOT degraded
        // Kills < to <= on L75
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        for (int i = 0; i < 5; i++) {
            monitor.record("vosk", 0.3);
        }
        ConfidenceMonitor.Evaluation eval = monitor.record("vosk", 0.3);
        assertThat(eval).isNotNull();
        assertThat(eval.degraded()).isFalse(); // 0.3 < 0.3 is false
        assertThat(eval.average()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void gracePeriodExactBoundaryRecordsOnNextCall() {
        // graceTranscriptions=5. After clearOnRecovery, 5 calls are skipped.
        // 6th call should record normally. Kills > 0 to >= 0 on L59
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");
        monitor.clearOnRecovery("vosk");

        // 5 grace samples (skipped)
        for (int i = 0; i < 5; i++) {
            assertThat(monitor.record("vosk", 0.1)).isNull();
        }
        // Grace counter is now 0. grace.get() > 0 is false → records normally
        monitor.record("vosk", 0.8);
        assertThat(monitor.getWindow("vosk")).hasSize(1);
    }

    @Test
    void recordReturnValueContainsCorrectAverage() {
        // Verify exact average in returned Evaluation
        ConfidenceMonitor monitor = new ConfidenceMonitor(props);
        monitor.register("vosk");

        // Record: 0.2, 0.4, 0.6, 0.8, 1.0 → avg = 0.6
        monitor.record("vosk", 0.2);
        monitor.record("vosk", 0.4);
        monitor.record("vosk", 0.6);
        monitor.record("vosk", 0.8);
        ConfidenceMonitor.Evaluation eval = monitor.record("vosk", 1.0);

        assertThat(eval).isNotNull();
        assertThat(eval.average()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        assertThat(eval.degraded()).isFalse(); // 0.6 > 0.3
    }
}
