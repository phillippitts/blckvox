package com.boombapcompile.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the health heartbeat file writer.
 */
@ConfigurationProperties(prefix = "health.heartbeat")
@Validated
public record HealthProperties(

        @DefaultValue("true")
        boolean enabled,

        @DefaultValue("${java.io.tmpdir}/blckvox-heartbeat")
        String path,

        @DefaultValue("30000")
        @Positive(message = "Interval ms must be positive")
        long intervalMs
) { }
