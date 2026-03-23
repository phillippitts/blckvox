package com.boombapcompile.blckvox.config.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsConfigTest {

    private final SettingsConfig config = new SettingsConfig();

    @Test
    @DisplayName("configFilePath resolves to Path from SettingsProperties")
    void configFilePathResolvesFromProperties() {
        var props = new SettingsProperties("/tmp/test.properties");

        Path result = config.configFilePath(props);

        assertThat(result).isEqualTo(Path.of("/tmp/test.properties"));
    }

    @Test
    @DisplayName("configFilePath with default value resolves correctly")
    void configFilePathWithDefaultValue() {
        var props = new SettingsProperties(
                "src/main/resources/application.properties");

        Path result = config.configFilePath(props);

        assertThat(result.getFileName().toString())
                .isEqualTo("application.properties");
    }

    @Test
    @DisplayName("configFilePath logs warning for nonexistent path but does not throw")
    void configFilePathNonexistentPathDoesNotThrow() {
        var props = new SettingsProperties("/nonexistent/path/config.properties");

        Path result = config.configFilePath(props);

        assertThat(result).isEqualTo(Path.of("/nonexistent/path/config.properties"));
    }

    @Test
    @DisplayName("configFilePath with existing file does not warn")
    void configFilePathExistingFileNoWarn(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.properties");
        Files.writeString(file, "key=value");

        var props = new SettingsProperties(file.toString());

        Path result = config.configFilePath(props);

        assertThat(result).isEqualTo(file);
    }

    @Test
    @DisplayName("SettingsProperties record holds configPath correctly")
    void settingsPropertiesRecordHoldsConfigPath() {
        var props = new SettingsProperties("/custom/path/app.properties");

        assertThat(props.configPath()).isEqualTo("/custom/path/app.properties");
    }
}
