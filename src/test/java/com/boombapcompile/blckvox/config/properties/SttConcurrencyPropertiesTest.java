package com.boombapcompile.blckvox.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SttConcurrencyPropertiesTest {

    @Test
    void allGettersReturnConstructorValues() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, false, 0.8, 0.85, 5000);

        assertThat(props.getVoskMax()).isEqualTo(4);
        assertThat(props.getWhisperMax()).isEqualTo(2);
        assertThat(props.getAcquireTimeoutMs()).isEqualTo(1000);
        assertThat(props.isDynamicScalingEnabled()).isFalse();
        assertThat(props.getCpuThresholdHigh()).isEqualTo(0.8);
        assertThat(props.getMemoryThresholdHigh()).isEqualTo(0.85);
        assertThat(props.getScalingIntervalMs()).isEqualTo(5000);
    }

    @Test
    void dynamicScalingEnabledReturnsTrue() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                8, 4, 2000, true, 0.9, 0.95, 10000);

        assertThat(props.isDynamicScalingEnabled()).isTrue();
        assertThat(props.getVoskMax()).isEqualTo(8);
        assertThat(props.getWhisperMax()).isEqualTo(4);
        assertThat(props.getAcquireTimeoutMs()).isEqualTo(2000);
        assertThat(props.getCpuThresholdHigh()).isEqualTo(0.9);
        assertThat(props.getMemoryThresholdHigh()).isEqualTo(0.95);
        assertThat(props.getScalingIntervalMs()).isEqualTo(10000);
    }
}
