package com.boombapcompile.blckvox.service.settings;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Describes a single configuration property for the settings UI.
 *
 * @param key         property key (e.g. "hotkey.threshold-ms")
 * @param displayName human-readable label (e.g. "Threshold")
 * @param description tooltip/subtitle text explaining the property
 * @param type        data type for validation and UI control selection
 * @param tab         which settings tab this property appears on
 * @param section     collapsible section name within the tab
 * @param defaultValue default value as string (for missing-key fallback)
 * @param constraints validation constraints (nullable fields for optional bounds)
 */
public record PropertyMetadata(
        String key,
        String displayName,
        String description,
        PropertyType type,
        Tab tab,
        String section,
        String defaultValue,
        Constraints constraints
) {

    public PropertyMetadata {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tab, "tab must not be null");
        Objects.requireNonNull(section, "section must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");
    }

    public enum PropertyType { INT, LONG, DOUBLE, BOOLEAN, STRING, ENUM }

    public enum Tab { BASIC, ADVANCED }

    /**
     * Validation constraints for a property. Fields are nullable when not applicable.
     *
     * @param min        minimum value for INT
     * @param max        maximum value for INT
     * @param minLong    minimum value for LONG
     * @param maxLong    maximum value for LONG
     * @param minDouble  minimum value for DOUBLE
     * @param maxDouble  maximum value for DOUBLE
     * @param enumValues valid values for ENUM type
     * @param notBlank   whether empty/blank strings are rejected for STRING type
     */
    public record Constraints(
            Integer min,
            Integer max,
            Long minLong,
            Long maxLong,
            Double minDouble,
            Double maxDouble,
            List<String> enumValues,
            boolean notBlank
    ) {

        public Constraints {
            enumValues = enumValues != null ? List.copyOf(enumValues) : null;
        }

        public static final Constraints NONE =
                new Constraints(null, null, null, null, null, null, null, false);

        public static Constraints intRange(int min, int max) {
            return new Constraints(min, max, null, null, null, null, null, false);
        }

        public static Constraints longRange(long min, long max) {
            return new Constraints(null, null, min, max, null, null, null, false);
        }

        public static Constraints doubleRange(double min, double max) {
            return new Constraints(null, null, null, null, min, max, null, false);
        }

        public static Constraints enumValues(List<String> values) {
            return new Constraints(null, null, null, null, null, null, List.copyOf(values), false);
        }

        public static Constraints requireNotBlank() {
            return new Constraints(null, null, null, null, null, null, null, true);
        }
    }

    /**
     * Result of a save operation. Sealed to enforce exhaustive handling.
     */
    public sealed interface SaveResult {
        record Success(int changedCount) implements SaveResult {}
        record ValidationFailure(List<ValidationError> errors) implements SaveResult {
            public ValidationFailure {
                errors = List.copyOf(errors);
            }
        }
        record IoFailure(String message, IOException cause) implements SaveResult {}
    }

    /**
     * A single validation error for a specific property key.
     */
    public record ValidationError(String key, String message) {
        public ValidationError {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }
}
