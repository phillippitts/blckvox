package com.boombapcompile.blckvox.config.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Wires settings beans. Converts the configPath string to a {@link Path}.
 */
@Configuration
public class SettingsConfig {

    private static final Logger LOG = LogManager.getLogger(SettingsConfig.class);

    @Bean
    @Qualifier("configFilePath")
    Path configFilePath(SettingsProperties props) {
        Path path = Paths.get(props.configPath());
        if (!Files.exists(path)) {
            LOG.warn("Settings config path does not exist: {}. "
                    + "Save will return IoFailure at runtime.", path);
        }
        return path;
    }
}
