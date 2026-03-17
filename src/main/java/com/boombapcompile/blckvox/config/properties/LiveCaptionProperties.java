package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the live caption overlay window.
 *
 * @since 1.3
 */
@ConfigurationProperties(prefix = "live-caption")
@Validated
public record LiveCaptionProperties(
        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("600")
        @Positive(message = "Window width must be positive")
        int windowWidth,

        @DefaultValue("250")
        @Positive(message = "Window height must be positive")
        int windowHeight,

        @DefaultValue("0.85")
        double windowOpacity
) {

    public LiveCaptionProperties {
        if (windowOpacity < 0.0 || windowOpacity > 1.0) {
            throw new IllegalArgumentException("live-caption.window-opacity must be in [0,1]");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public double getWindowOpacity() {
        return windowOpacity;
    }
}
