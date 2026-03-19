package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperCommandBuilderTest {

    private final WhisperConfig cfg = new WhisperConfig(
            "/usr/local/bin/whisper", "/models/base.bin", 10, "en", 4, 1048576, 0.85);

    private final Path wavPath = Path.of("/tmp/test.wav");

    @Test
    void textModeAddsOtxtFlag() {
        List<String> cmd = new WhisperCommandBuilder("text").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-otxt");
        assertThat(cmd).doesNotContain("-oj");
    }

    @Test
    void jsonModeAddsOjFlag() {
        List<String> cmd = new WhisperCommandBuilder("json").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-oj");
        assertThat(cmd).doesNotContain("-otxt");
    }

    @Test
    void nullOutputModeDefaultsToText() {
        List<String> cmd = new WhisperCommandBuilder(null).buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-otxt");
    }

    @Test
    void blankOutputModeDefaultsToText() {
        List<String> cmd = new WhisperCommandBuilder("  ").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-otxt");
    }

    @Test
    void buildCommandContainsAllRequiredArgs() {
        List<String> cmd = new WhisperCommandBuilder("text").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-m", "-f", "-l", "-of", "stdout", "-t");
        assertThat(cmd.get(0)).isEqualTo("/usr/local/bin/whisper");
    }

    @Test
    void nullCfgThrowsNpe() {
        WhisperCommandBuilder builder = new WhisperCommandBuilder("text");
        assertThatThrownBy(() -> builder.buildCommand(null, wavPath))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullWavPathThrowsNpe() {
        WhisperCommandBuilder builder = new WhisperCommandBuilder("text");
        assertThatThrownBy(() -> builder.buildCommand(cfg, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void absolutePathUsedDirectly() {
        List<String> cmd = new WhisperCommandBuilder("text").buildCommand(cfg, wavPath);
        // binary is absolute, should be used as-is
        assertThat(cmd.get(0)).isEqualTo("/usr/local/bin/whisper");
    }

    @Test
    void relativePathResolved() {
        WhisperConfig relativeCfg = new WhisperConfig(
                "tools/whisper", "/models/base.bin", 10, "en", 4, 1048576, 0.85);
        List<String> cmd = new WhisperCommandBuilder("text").buildCommand(relativeCfg, wavPath);
        // relative path should be resolved to absolute
        assertThat(Path.of(cmd.get(0)).isAbsolute()).isTrue();
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void normalizeOutputModeEmptyStringDefaultsToText() {
        // Empty string (not just blank) → defaults to "text"
        // Kills L99 condition (mode.isBlank())
        List<String> cmd = new WhisperCommandBuilder("").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-otxt");
    }

    @Test
    void relativeModelPathIsResolvedToAbsolute() {
        // Model path is relative → should be resolved to absolute
        // Kills L115 path.isAbsolute() check
        WhisperConfig relativeCfg = new WhisperConfig(
                "/usr/local/bin/whisper", "models/base.bin", 10, "en", 4, 1048576, 0.85);
        List<String> cmd = new WhisperCommandBuilder("text").buildCommand(relativeCfg, wavPath);
        // model path is at index 2 (after binary, "-m")
        String modelPath = cmd.get(2);
        assertThat(Path.of(modelPath).isAbsolute()).isTrue();
    }

    @Test
    void jsonModeCaseInsensitive() {
        // "JSON" (uppercase) → normalized to "json" → adds -oj
        // Kills L78 equalsIgnoreCase or L102 toLowerCase
        List<String> cmd = new WhisperCommandBuilder("JSON").buildCommand(cfg, wavPath);
        assertThat(cmd).contains("-oj");
    }
}
