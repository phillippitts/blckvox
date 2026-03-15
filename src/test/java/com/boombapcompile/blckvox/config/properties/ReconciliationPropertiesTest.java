package com.boombapcompile.blckvox.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationPropertiesTest {

    @Test
    void validConstructionSucceeds() {
        var props = new ReconciliationProperties(true,
                ReconciliationProperties.Strategy.OVERLAP, 0.6, 0.7);
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getStrategy()).isEqualTo(ReconciliationProperties.Strategy.OVERLAP);
        assertThat(props.getOverlapThreshold()).isEqualTo(0.6);
        assertThat(props.getConfidenceThreshold()).isEqualTo(0.7);
    }

    @Test
    void boundaryOverlapThresholdValues() {
        var zero = new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 0.0, 0.5);
        assertThat(zero.getOverlapThreshold()).isEqualTo(0.0);

        var one = new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 1.0, 0.5);
        assertThat(one.getOverlapThreshold()).isEqualTo(1.0);
    }

    @Test
    void boundaryConfidenceThresholdValues() {
        var zero = new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.CONFIDENCE, 0.5, 0.0);
        assertThat(zero.getConfidenceThreshold()).isEqualTo(0.0);

        var one = new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.CONFIDENCE, 0.5, 1.0);
        assertThat(one.getConfidenceThreshold()).isEqualTo(1.0);
    }

    @Test
    void overlapThresholdBelowZeroThrows() {
        assertThatThrownBy(() -> new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, -0.1, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap-threshold");
    }

    @Test
    void overlapThresholdAboveOneThrows() {
        assertThatThrownBy(() -> new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 1.1, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap-threshold");
    }

    @Test
    void confidenceThresholdBelowZeroThrows() {
        assertThatThrownBy(() -> new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 0.5, -0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence-threshold");
    }

    @Test
    void confidenceThresholdAboveOneThrows() {
        assertThatThrownBy(() -> new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 0.5, 1.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence-threshold");
    }

    @Test
    void disabledByDefault() {
        var props = new ReconciliationProperties(false,
                ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);
        assertThat(props.isEnabled()).isFalse();
    }
}
