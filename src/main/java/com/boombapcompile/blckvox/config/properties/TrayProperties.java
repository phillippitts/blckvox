package com.boombapcompile.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the macOS system tray icon.
 *
 * @since 1.2
 */
@ConfigurationProperties(prefix = "tray")
@Validated
public record TrayProperties(
        @DefaultValue("true")
        boolean enabled
) {

    public boolean isEnabled() {
        return enabled;
    }
}
