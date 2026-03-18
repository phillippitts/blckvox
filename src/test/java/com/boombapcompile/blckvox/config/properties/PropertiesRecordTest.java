package com.boombapcompile.blckvox.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises all getter methods on properties records to ensure coverage
 * and validate compact constructor logic.
 */
class PropertiesRecordTest {

    // --- LiveCaptionProperties ---

    @Test
    void liveCaptionPropertiesGetters() {
        LiveCaptionProperties props = new LiveCaptionProperties(true, 800, 300, 0.9);
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getWindowWidth()).isEqualTo(800);
        assertThat(props.getWindowHeight()).isEqualTo(300);
        assertThat(props.getWindowOpacity()).isEqualTo(0.9);
    }

    @Test
    void liveCaptionPropertiesRejectsOpacityAboveOne() {
        assertThatThrownBy(() -> new LiveCaptionProperties(true, 800, 300, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window-opacity");
    }

    @Test
    void liveCaptionPropertiesRejectsNegativeOpacity() {
        assertThatThrownBy(() -> new LiveCaptionProperties(true, 800, 300, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window-opacity");
    }

    @Test
    void liveCaptionPropertiesAcceptsBoundaryOpacityZero() {
        LiveCaptionProperties props = new LiveCaptionProperties(true, 800, 300, 0.0);
        assertThat(props.getWindowOpacity()).isEqualTo(0.0);
    }

    @Test
    void liveCaptionPropertiesAcceptsBoundaryOpacityOne() {
        LiveCaptionProperties props = new LiveCaptionProperties(true, 800, 300, 1.0);
        assertThat(props.getWindowOpacity()).isEqualTo(1.0);
    }

    // --- TrayProperties ---

    @Test
    void trayPropertiesGetters() {
        TrayProperties props = new TrayProperties(true);
        assertThat(props.isEnabled()).isTrue();

        TrayProperties disabled = new TrayProperties(false);
        assertThat(disabled.isEnabled()).isFalse();
    }

    // --- AudioCaptureProperties ---

    @Test
    void audioCapturePropertiesGetters() {
        AudioCaptureProperties props = new AudioCaptureProperties(50, 30000, "USB Mic");
        assertThat(props.getChunkMillis()).isEqualTo(50);
        assertThat(props.getMaxDurationMs()).isEqualTo(30000);
        assertThat(props.getDeviceName()).isEqualTo("USB Mic");
    }

    @Test
    void audioCapturePropertiesNormalizesBlankDeviceName() {
        AudioCaptureProperties props = new AudioCaptureProperties(50, 30000, "   ");
        assertThat(props.getDeviceName()).isNull();
    }

    @Test
    void audioCapturePropertiesKeepsNullDeviceName() {
        AudioCaptureProperties props = new AudioCaptureProperties(50, 30000, null);
        assertThat(props.getDeviceName()).isNull();
    }

    // --- ReconciliationProperties ---

    @Test
    void reconciliationPropertiesGetters() {
        ReconciliationProperties props = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.5, 0.8);
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getStrategy()).isEqualTo(ReconciliationProperties.Strategy.CONFIDENCE);
        assertThat(props.getOverlapThreshold()).isEqualTo(0.5);
        assertThat(props.getConfidenceThreshold()).isEqualTo(0.8);
    }

    @Test
    void reconciliationPropertiesRejectsInvalidOverlapThreshold() {
        assertThatThrownBy(() -> new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 1.5, 0.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap-threshold");
    }

    @Test
    void reconciliationPropertiesRejectsInvalidConfidenceThreshold() {
        assertThatThrownBy(() -> new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence-threshold");
    }

    // --- OrchestrationProperties ---

    @Test
    void orchestrationPropertiesGetters() {
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.WHISPER, 2000, 300, 120);
        assertThat(props.getPrimaryEngine()).isEqualTo(OrchestrationProperties.PrimaryEngine.WHISPER);
        assertThat(props.getSilenceGapMs()).isEqualTo(2000);
        assertThat(props.getSilenceThreshold()).isEqualTo(300);
    }

    // --- HealthProperties ---

    @Test
    void healthPropertiesGetters() {
        HealthProperties props = new HealthProperties(true, "/tmp/heartbeat", 30000L);
        assertThat(props.enabled()).isTrue();
        assertThat(props.path()).isEqualTo("/tmp/heartbeat");
        assertThat(props.intervalMs()).isEqualTo(30000L);
    }

    @Test
    void healthPropertiesDisabled() {
        HealthProperties props = new HealthProperties(false, "/tmp/heartbeat", 30000L);
        assertThat(props.enabled()).isFalse();
    }

    // --- SttWatchdogProperties ---

    @Test
    void sttWatchdogPropertiesGetters() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, 60000L, 0.3, 10, 5, 1000L, 2.0, 60000L, 5);
        assertThat(props.enabled()).isTrue();
        assertThat(props.windowMinutes()).isEqualTo(60);
        assertThat(props.maxRestartsPerWindow()).isEqualTo(3);
        assertThat(props.cooldownMinutes()).isEqualTo(10);
        assertThat(props.healthSummaryIntervalMillis()).isEqualTo(60000L);
        assertThat(props.confidenceBlacklistThreshold()).isEqualTo(0.3);
        assertThat(props.confidenceWindowSize()).isEqualTo(10);
        assertThat(props.confidenceMinSamples()).isEqualTo(5);
        assertThat(props.backoffBaseDelayMs()).isEqualTo(1000L);
        assertThat(props.backoffMultiplier()).isEqualTo(2.0);
        assertThat(props.backoffMaxDelayMs()).isEqualTo(60000L);
    }
}
