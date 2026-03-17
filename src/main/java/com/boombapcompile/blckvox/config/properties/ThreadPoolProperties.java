package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for thread pools.
 *
 * <p>Provides tuneable thread pool sizing for STT executor and event executor.
 * Defaults are conservative but can be adjusted based on hardware and workload.
 */
@Validated
@ConfigurationProperties(prefix = "threadpool")
public record ThreadPoolProperties(
        @DefaultValue @Valid
        SttPoolProperties stt,

        @DefaultValue @Valid
        EventPoolProperties event
) {

    public SttPoolProperties getStt() {
        return stt;
    }

    public EventPoolProperties getEvent() {
        return event;
    }

    /**
     * STT executor pool configuration.
     */
    public record SttPoolProperties(
            @DefaultValue("4") @Min(1)
            int corePoolSize,

            @DefaultValue("8") @Min(1)
            int maxPoolSize,

            @DefaultValue("50") @Min(1)
            int queueCapacity,

            @DefaultValue("60") @Min(0)
            int keepAliveSeconds,

            @DefaultValue("stt-pool-") @NotBlank
            String threadNamePrefix
    ) {

        public SttPoolProperties {
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(
                        "maxPoolSize (%d) must be >= corePoolSize (%d)"
                                .formatted(maxPoolSize, corePoolSize));
            }
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }
    }

    /**
     * Event executor pool configuration.
     */
    public record EventPoolProperties(
            @DefaultValue("2") @Min(1)
            int corePoolSize,

            @DefaultValue("4") @Min(1)
            int maxPoolSize,

            @DefaultValue("10") @Min(1)
            int queueCapacity,

            @DefaultValue("60") @Min(0)
            int keepAliveSeconds,

            @DefaultValue("event-pool-") @NotBlank
            String threadNamePrefix
    ) {

        public EventPoolProperties {
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(
                        "maxPoolSize (%d) must be >= corePoolSize (%d)"
                                .formatted(maxPoolSize, corePoolSize));
            }
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }
    }
}
