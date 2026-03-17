package com.boombapcompile.blckvox.config.stt;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperConfigTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateValidConfig() {
        WhisperConfig config = new WhisperConfig("bin/whisper", "models/ggml.bin", 15, "en", 4, 1048576, 0.85);

        assertThat(config.binaryPath()).isEqualTo("bin/whisper");
        assertThat(config.modelPath()).isEqualTo("models/ggml.bin");
        assertThat(config.timeoutSeconds()).isEqualTo(15);
        assertThat(config.language()).isEqualTo("en");
        assertThat(config.threads()).isEqualTo(4);
        assertThat(config.maxStdoutBytes()).isEqualTo(1048576);
        assertThat(config.textModeConfidence()).isEqualTo(0.85);
    }

    @Test
    void shouldUseDefaultValues() {
        WhisperConfig config = new WhisperConfig();

        assertThat(config.binaryPath()).isEqualTo("tools/whisper.cpp/main");
        assertThat(config.modelPath()).isEqualTo("models/ggml-base.en.bin");
        assertThat(config.timeoutSeconds()).isEqualTo(10);
        assertThat(config.language()).isEqualTo("en");
        assertThat(config.threads()).isEqualTo(4);
        assertThat(config.maxStdoutBytes()).isEqualTo(1048576);
        assertThat(config.textModeConfidence()).isEqualTo(0.85);
    }

    @Test
    void shouldRejectTextModeConfidenceAboveOne() {
        assertThatThrownBy(() -> new WhisperConfig("bin/whisper", "models/ggml.bin", 10, "en", 4, 1048576, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textModeConfidence must be between 0.0 and 1.0");
    }

    @Test
    void shouldRejectTextModeConfidenceBelowZero() {
        assertThatThrownBy(() -> new WhisperConfig("bin/whisper", "models/ggml.bin", 10, "en", 4, 1048576, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textModeConfidence must be between 0.0 and 1.0");
    }

    @Test
    void shouldRejectBlankBinaryPath() {
        WhisperConfig config = new WhisperConfig("", "models/ggml.bin", 10, "en", 4, 1048576, 0.85);
        Set<ConstraintViolation<WhisperConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("binary path must not be blank");
    }

    @Test
    void shouldRejectBlankModelPath() {
        WhisperConfig config = new WhisperConfig("bin/whisper", "", 10, "en", 4, 1048576, 0.85);
        Set<ConstraintViolation<WhisperConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("model path must not be blank");
    }

    @Test
    void shouldRejectNegativeTimeout() {
        WhisperConfig config = new WhisperConfig("bin/whisper", "models/ggml.bin", -5, "en", 4, 1048576, 0.85);
        Set<ConstraintViolation<WhisperConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Timeout must be positive");
    }

    @Test
    void shouldRejectZeroTimeout() {
        WhisperConfig config = new WhisperConfig("bin/whisper", "models/ggml.bin", 0, "en", 4, 1048576, 0.85);
        Set<ConstraintViolation<WhisperConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Timeout must be positive");
    }

    @Test
    void shouldAcceptValidNonDefaultValues() {
        WhisperConfig config = new WhisperConfig("custom/whisper", "custom/model.bin", 30, "es", 8, 2097152, 0.90);
        Set<ConstraintViolation<WhisperConfig>> violations = validator.validate(config);

        assertThat(violations).isEmpty();
    }
}
