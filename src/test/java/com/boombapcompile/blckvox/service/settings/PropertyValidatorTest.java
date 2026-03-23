package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyValidatorTest {

    private PropertyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PropertyValidator(new PropertyMetadataRegistry());
    }

    @Nested
    @DisplayName("INT validation")
    class IntValidation {

        @Test
        @DisplayName("Valid integer within range")
        void validInt() {
            assertThat(validator.validateField("hotkey.threshold-ms", "500")).isEmpty();
        }

        @Test
        @DisplayName("Exact min boundary is valid")
        void minBoundaryValid() {
            assertThat(validator.validateField("hotkey.threshold-ms", "100")).isEmpty();
        }

        @Test
        @DisplayName("Exact max boundary is valid")
        void maxBoundaryValid() {
            assertThat(validator.validateField("hotkey.threshold-ms", "1000")).isEmpty();
        }

        @Test
        @DisplayName("Below min boundary returns error")
        void belowMinReturnsError() {
            var result = validator.validateField("hotkey.threshold-ms", "99");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains(">= 100");
        }

        @Test
        @DisplayName("Above max boundary returns error")
        void aboveMaxReturnsError() {
            var result = validator.validateField("hotkey.threshold-ms", "1001");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("<= 1000");
        }

        @Test
        @DisplayName("Non-numeric string returns error")
        void nonNumericReturnsError() {
            var result = validator.validateField("hotkey.threshold-ms", "abc");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("valid integer");
        }

        @Test
        @DisplayName("Value exceeding Integer.MAX_VALUE returns error")
        void integerOverflowReturnsError() {
            var result = validator.validateField("hotkey.threshold-ms", "99999999999");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("valid integer");
        }
    }

    @Nested
    @DisplayName("LONG validation")
    class LongValidation {

        @Test
        @DisplayName("Valid long within range")
        void validLong() {
            assertThat(validator.validateField("stt.watchdog.health-summary-interval-millis",
                    "30000")).isEmpty();
        }

        @Test
        @DisplayName("Exact min boundary is valid")
        void minBoundaryValid() {
            assertThat(validator.validateField("stt.watchdog.health-summary-interval-millis",
                    "1000")).isEmpty();
        }

        @Test
        @DisplayName("Exact max boundary is valid")
        void maxBoundaryValid() {
            assertThat(validator.validateField("stt.watchdog.health-summary-interval-millis",
                    "3600000")).isEmpty();
        }

        @Test
        @DisplayName("Below min returns error")
        void belowMinReturnsError() {
            var result = validator.validateField(
                    "stt.watchdog.health-summary-interval-millis", "500");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains(">= 1000");
        }

        @Test
        @DisplayName("Above max returns error")
        void aboveMaxReturnsError() {
            var result = validator.validateField(
                    "stt.watchdog.health-summary-interval-millis", "3600001");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("<= 3600000");
        }

        @Test
        @DisplayName("Non-numeric string returns error for LONG")
        void nonNumericReturnsError() {
            var result = validator.validateField(
                    "stt.watchdog.health-summary-interval-millis", "abc");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("valid integer");
        }
    }

    @Nested
    @DisplayName("DOUBLE validation")
    class DoubleValidation {

        @Test
        @DisplayName("Valid double within range")
        void validDouble() {
            assertThat(validator.validateField(
                    "stt.reconciliation.overlap-threshold", "0.5")).isEmpty();
        }

        @Test
        @DisplayName("Below range returns error")
        void belowRangeReturnsError() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "-0.1");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains(">= 0.0");
        }

        @Test
        @DisplayName("Above range returns error")
        void aboveRangeReturnsError() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "1.1");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("<= 1.0");
        }
    }

    @Nested
    @DisplayName("BOOLEAN validation")
    class BooleanValidation {

        @Test
        @DisplayName("true is valid")
        void trueIsValid() {
            assertThat(validator.validateField("hotkey.toggle-mode", "true")).isEmpty();
        }

        @Test
        @DisplayName("false is valid")
        void falseIsValid() {
            assertThat(validator.validateField("hotkey.toggle-mode", "false")).isEmpty();
        }

        @Test
        @DisplayName("yes is invalid")
        void yesIsInvalid() {
            var result = validator.validateField("hotkey.toggle-mode", "yes");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("true");
        }
    }

    @Nested
    @DisplayName("ENUM validation")
    class EnumValidation {

        @Test
        @DisplayName("Valid enum value (kebab-case matching file format)")
        void validEnum() {
            assertThat(validator.validateField("hotkey.type", "double-tap")).isEmpty();
        }

        @Test
        @DisplayName("Invalid enum value")
        void invalidEnum() {
            var result = validator.validateField("hotkey.type", "triple-tap");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("Must be one of");
        }

        @Test
        @DisplayName("Case-insensitive match")
        void caseInsensitiveMatch() {
            assertThat(validator.validateField(
                    "stt.orchestration.primary-engine", "VOSK")).isEmpty();
        }
    }

    @Nested
    @DisplayName("STRING validation")
    class StringValidation {

        @Test
        @DisplayName("notBlank rejects empty string")
        void notBlankRejectsEmpty() {
            var result = validator.validateField("hotkey.key", "");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("blank");
        }

        @Test
        @DisplayName("notBlank rejects whitespace-only string")
        void notBlankRejectsWhitespace() {
            var result = validator.validateField("hotkey.key", "   ");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("blank");
        }

        @Test
        @DisplayName("STRING without notBlank allows empty value")
        void stringAllowsEmptyWhenNotBlankFalse() {
            // audio.capture.device-name is STRING with notBlank=false
            assertThat(validator.validateField("audio.capture.device-name", "")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Null/empty handling")
    class NullHandling {

        @Test
        @DisplayName("Null value returns error for any type")
        void nullReturnsError() {
            var result = validator.validateField("hotkey.threshold-ms", null);
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("null");
        }

        @Test
        @DisplayName("Empty value returns error for non-STRING types")
        void emptyReturnsErrorForNonString() {
            var result = validator.validateField("hotkey.threshold-ms", "");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("empty");
        }

        @Test
        @DisplayName("Unknown property key returns error")
        void unknownKeyReturnsError() {
            var result = validator.validateField("nonexistent.key", "value");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("Unknown");
        }
    }

    @Nested
    @DisplayName("Double edge cases")
    class DoubleEdgeCases {

        @Test
        @DisplayName("NaN is rejected for DOUBLE type")
        void nanIsRejected() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "NaN");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("finite");
        }

        @Test
        @DisplayName("Infinity is rejected for DOUBLE type")
        void infinityIsRejected() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "Infinity");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("finite");
        }
    }

    @Nested
    @DisplayName("Cross-property validation")
    class CrossPropertyValidation {

        @Test
        @DisplayName("STT max-pool-size < core-pool-size returns error")
        void sttPoolSizeViolation() {
            Map<String, String> values = new HashMap<>();
            values.put("threadpool.stt.core-pool-size", "8");
            values.put("threadpool.stt.max-pool-size", "4");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().key()).isEqualTo("threadpool.stt.max-pool-size");
        }

        @Test
        @DisplayName("Event max-pool-size < core-pool-size returns error")
        void eventPoolSizeViolation() {
            Map<String, String> values = new HashMap<>();
            values.put("threadpool.event.core-pool-size", "4");
            values.put("threadpool.event.max-pool-size", "2");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().key()).isEqualTo("threadpool.event.max-pool-size");
        }

        @Test
        @DisplayName("max-pool-size == core-pool-size is valid")
        void equalPoolSizeIsValid() {
            Map<String, String> values = new HashMap<>();
            values.put("threadpool.stt.core-pool-size", "4");
            values.put("threadpool.stt.max-pool-size", "4");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("All valid values pass cross-property validation")
        void allValidValuesPasses() {
            Map<String, String> values = new HashMap<>();
            values.put("threadpool.stt.core-pool-size", "2");
            values.put("threadpool.stt.max-pool-size", "8");
            values.put("threadpool.event.core-pool-size", "1");
            values.put("threadpool.event.max-pool-size", "4");
            values.put("audio.validation.min-duration-ms", "250");
            values.put("audio.validation.max-duration-ms", "300000");

            assertThat(validator.validateCrossProperty(values)).isEmpty();
        }

        @Test
        @DisplayName("Range constraint: overlap-threshold outside [0,1] returns error")
        void overlapThresholdOutOfRange() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "1.5");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Range constraint: confidence-threshold outside [0,1] returns error")
        void confidenceThresholdOutOfRange() {
            var result = validator.validateField(
                    "stt.reconciliation.confidence-threshold", "-0.5");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Audio min-duration-ms >= max-duration-ms returns error")
        void audioDurationViolation() {
            Map<String, String> values = new HashMap<>();
            values.put("audio.validation.min-duration-ms", "5000");
            values.put("audio.validation.max-duration-ms", "5000");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().key())
                    .isEqualTo("audio.validation.min-duration-ms");
        }

        @Test
        @DisplayName("Audio min-duration-ms < max-duration-ms is valid")
        void audioDurationValid() {
            Map<String, String> values = new HashMap<>();
            values.put("audio.validation.min-duration-ms", "250");
            values.put("audio.validation.max-duration-ms", "300000");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("Audio duration with only one key present is valid (no error)")
        void audioDurationPartialKeysValid() {
            Map<String, String> values = new HashMap<>();
            values.put("audio.validation.min-duration-ms", "250");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("Audio min > max returns error with meaningful message")
        void audioDurationMinGreaterThanMax() {
            Map<String, String> values = new HashMap<>();
            values.put("audio.validation.min-duration-ms", "50000");
            values.put("audio.validation.max-duration-ms", "1000");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().message()).contains("must be <");
        }

        @Test
        @DisplayName("Pool size violation includes meaningful error message")
        void poolSizeViolationHasMeaningfulMessage() {
            Map<String, String> values = new HashMap<>();
            values.put("threadpool.stt.core-pool-size", "8");
            values.put("threadpool.stt.max-pool-size", "4");

            List<ValidationError> errors = validator.validateCrossProperty(values);
            assertThat(errors.getFirst().message())
                    .containsIgnoringCase("max-pool-size");
        }
    }

    @Nested
    @DisplayName("Additional boundary tests")
    class AdditionalBoundaries {

        @Test
        @DisplayName("DOUBLE exact min boundary is valid")
        void doubleMinBoundaryValid() {
            assertThat(validator.validateField(
                    "stt.reconciliation.overlap-threshold", "0.0")).isEmpty();
        }

        @Test
        @DisplayName("DOUBLE exact max boundary is valid")
        void doubleMaxBoundaryValid() {
            assertThat(validator.validateField(
                    "stt.reconciliation.overlap-threshold", "1.0")).isEmpty();
        }

        @Test
        @DisplayName("Non-numeric DOUBLE returns error")
        void nonNumericDoubleReturnsError() {
            var result = validator.validateField(
                    "stt.reconciliation.overlap-threshold", "abc");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("number");
        }

        @Test
        @DisplayName("LONG value exceeding Long.MAX_VALUE returns error")
        void longOverflowReturnsError() {
            var result = validator.validateField(
                    "stt.watchdog.health-summary-interval-millis",
                    "99999999999999999999");
            assertThat(result).isPresent();
            assertThat(result.get().message()).contains("valid integer");
        }
    }
}
