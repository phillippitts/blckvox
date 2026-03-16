package com.boombapcompile.blckvox.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Micrometer JMX metrics for runtime observability.
 *
 * <p>Provides a {@link JmxMeterRegistry} that exposes all metrics as JMX MBeans,
 * accessible via JConsole, VisualVM, or any JMX client.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        JmxMeterRegistry registry = new JmxMeterRegistry(
                JmxConfig.DEFAULT, io.micrometer.core.instrument.Clock.SYSTEM);
        registry.config().commonTags("application", "blckvox");
        return registry;
    }
}
