package com.boombapcompile.blckvox.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.jmx.JmxMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    @Test
    void meterRegistryBeanIsJmxBacked() {
        MetricsConfig config = new MetricsConfig();
        MeterRegistry registry = config.meterRegistry();
        assertThat(registry).isInstanceOf(JmxMeterRegistry.class);
    }

    @Test
    void meterRegistryHasApplicationTag() {
        MetricsConfig config = new MetricsConfig();
        MeterRegistry registry = config.meterRegistry();
        // Common tags are applied to every meter; verify by registering a counter
        registry.counter("test.counter").increment();
        var meter = registry.find("test.counter").counter();
        assertThat(meter).isNotNull();
        assertThat(meter.getId().getTag("application")).isEqualTo("blckvox");
    }
}
