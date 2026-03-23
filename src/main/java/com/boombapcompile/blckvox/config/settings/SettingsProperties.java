package com.boombapcompile.blckvox.config.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the settings UI.
 *
 * @param configPath path to the properties file to edit.
 *                   Default points to source file for development (CWD = project root).
 *                   Production should override to absolute path.
 */
@ConfigurationProperties(prefix = "settings")
public record SettingsProperties(
        @DefaultValue("src/main/resources/application.properties")
        String configPath
) {}
