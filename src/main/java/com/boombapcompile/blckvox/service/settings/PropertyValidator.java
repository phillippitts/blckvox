package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Constraints;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.ValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates individual property values against their metadata constraints
 * and cross-property relationships.
 */
@Component
public class PropertyValidator {

    private final PropertyMetadataRegistry registry;

    public PropertyValidator(PropertyMetadataRegistry registry) {
        this.registry = registry;
    }

    /**
     * Validates a single field value against its metadata constraints.
     *
     * @param key   property key
     * @param value property value (may be null)
     * @return validation error if invalid, empty if valid
     */
    public Optional<ValidationError> validateField(String key, String value) {
        var metaOpt = registry.findByKey(key);
        if (metaOpt.isEmpty()) {
            return Optional.of(new ValidationError(key, "Unknown property key"));
        }
        var meta = metaOpt.get();

        if (value == null) {
            return Optional.of(new ValidationError(key, "Value must not be null"));
        }

        // STRING type allows empty values when notBlank is false
        if (value.isEmpty() && meta.type() != PropertyMetadata.PropertyType.STRING) {
            return Optional.of(new ValidationError(key, "Value must not be empty"));
        }

        return switch (meta.type()) {
            case INT -> validateInt(key, value, meta.constraints());
            case LONG -> validateLong(key, value, meta.constraints());
            case DOUBLE -> validateDouble(key, value, meta.constraints());
            case BOOLEAN -> validateBoolean(key, value);
            case STRING -> validateString(key, value, meta.constraints());
            case ENUM -> validateEnum(key, value, meta.constraints());
        };
    }

    /**
     * Validates cross-property constraints (e.g., max-pool-size >= core-pool-size).
     *
     * @param allValues all current property values
     * @return list of validation errors (empty if all valid)
     */
    public List<ValidationError> validateCrossProperty(Map<String, String> allValues) {
        List<ValidationError> errors = new ArrayList<>();
        validatePoolSizeConstraint(allValues, "threadpool.stt", errors);
        validatePoolSizeConstraint(allValues, "threadpool.event", errors);
        validateAudioDurationConstraint(allValues, errors);
        return errors;
    }

    private void validateAudioDurationConstraint(Map<String, String> values,
                                                List<ValidationError> errors) {
        String minKey = "audio.validation.min-duration-ms";
        String maxKey = "audio.validation.max-duration-ms";
        String minStr = values.get(minKey);
        String maxStr = values.get(maxKey);

        if (minStr == null || maxStr == null) {
            return;
        }

        try {
            int min = Integer.parseInt(minStr);
            int max = Integer.parseInt(maxStr);
            if (min >= max) {
                errors.add(new ValidationError(minKey,
                        "min-duration-ms (%d) must be < max-duration-ms (%d)"
                                .formatted(min, max)));
            }
        } catch (NumberFormatException e) {
            // Individual field validation will catch parse errors
        }
    }

    private void validatePoolSizeConstraint(Map<String, String> values,
                                            String prefix,
                                            List<ValidationError> errors) {
        String coreKey = prefix + ".core-pool-size";
        String maxKey = prefix + ".max-pool-size";
        String coreStr = values.get(coreKey);
        String maxStr = values.get(maxKey);

        if (coreStr == null || maxStr == null) {
            return;
        }

        try {
            int core = Integer.parseInt(coreStr);
            int max = Integer.parseInt(maxStr);
            if (max < core) {
                errors.add(new ValidationError(maxKey,
                        "max-pool-size (%d) must be >= core-pool-size (%d)"
                                .formatted(max, core)));
            }
        } catch (NumberFormatException e) {
            // Individual field validation will catch parse errors
        }
    }

    private Optional<ValidationError> validateInt(String key, String value,
                                                  Constraints constraints) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Optional.of(new ValidationError(key,
                    "Must be a valid integer"));
        }

        if (constraints.min() != null && parsed < constraints.min()) {
            return Optional.of(new ValidationError(key,
                    "Must be >= " + constraints.min()));
        }
        if (constraints.max() != null && parsed > constraints.max()) {
            return Optional.of(new ValidationError(key,
                    "Must be <= " + constraints.max()));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateLong(String key, String value,
                                                   Constraints constraints) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            return Optional.of(new ValidationError(key,
                    "Must be a valid integer"));
        }

        if (constraints.minLong() != null && parsed < constraints.minLong()) {
            return Optional.of(new ValidationError(key,
                    "Must be >= " + constraints.minLong()));
        }
        if (constraints.maxLong() != null && parsed > constraints.maxLong()) {
            return Optional.of(new ValidationError(key,
                    "Must be <= " + constraints.maxLong()));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateDouble(String key, String value,
                                                     Constraints constraints) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Optional.of(new ValidationError(key,
                    "Must be a valid number"));
        }

        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            return Optional.of(new ValidationError(key,
                    "Must be a finite number"));
        }

        if (constraints.minDouble() != null && parsed < constraints.minDouble()) {
            return Optional.of(new ValidationError(key,
                    "Must be >= " + constraints.minDouble()));
        }
        if (constraints.maxDouble() != null && parsed > constraints.maxDouble()) {
            return Optional.of(new ValidationError(key,
                    "Must be <= " + constraints.maxDouble()));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateBoolean(String key, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            return Optional.of(new ValidationError(key,
                    "Must be 'true' or 'false'"));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateString(String key, String value,
                                                     Constraints constraints) {
        if (constraints.notBlank() && value.isBlank()) {
            return Optional.of(new ValidationError(key,
                    "Must not be blank"));
        }
        return Optional.empty();
    }

    private Optional<ValidationError> validateEnum(String key, String value,
                                                   Constraints constraints) {
        if (constraints.enumValues() == null) {
            return Optional.empty();
        }
        boolean matches = constraints.enumValues().stream()
                .anyMatch(v -> v.equalsIgnoreCase(value));
        if (!matches) {
            return Optional.of(new ValidationError(key,
                    "Must be one of: " + constraints.enumValues()));
        }
        return Optional.empty();
    }
}
