package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.SaveResult;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Tab;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.ValidationError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend service for the settings UI. Manages property snapshots,
 * validation, and persistence orchestration.
 *
 * <p>Thread-safe: snapshot is loaded lazily with {@code synchronized}
 * and replaced atomically via {@code volatile} on save.
 */
@Service
public class ConfigurationService {

    private static final Logger LOG = LogManager.getLogger(ConfigurationService.class);

    private final PropertyMetadataRegistry registry;
    private final PropertyValidator validator;
    private final FileConfigurationPersistence persistence;
    private final Path configFilePath;

    private volatile Map<String, String> snapshot;

    public ConfigurationService(PropertyMetadataRegistry registry,
                                PropertyValidator validator,
                                FileConfigurationPersistence persistence,
                                @Qualifier("configFilePath") Path configFilePath) {
        this.registry = registry;
        this.validator = validator;
        this.persistence = persistence;
        this.configFilePath = configFilePath;
    }

    /**
     * Loads or returns the cached snapshot of all property values.
     * Missing keys are filled with defaults from metadata.
     *
     * @return snapshot result — either a map of values or an IO failure
     */
    public synchronized SaveResult loadSnapshot() {
        if (snapshot != null) {
            return new SaveResult.Success(0);
        }

        try {
            Map<String, String> fileValues = persistence.readAll(configFilePath);
            Map<String, String> merged = new LinkedHashMap<>();

            for (PropertyMetadata meta : registry.getAll()) {
                String value = fileValues.getOrDefault(meta.key(), meta.defaultValue());
                merged.put(meta.key(), value);
            }

            this.snapshot = merged;
            return new SaveResult.Success(0);
        } catch (IOException e) {
            return new SaveResult.IoFailure(
                    "Could not load properties from " + configFilePath, e);
        }
    }

    /**
     * Returns the current snapshot values. Loads from disk if not yet loaded.
     *
     * @return map of key-value pairs, or empty map if load failed
     */
    public Map<String, String> getSnapshot() {
        Map<String, String> local = snapshot;
        if (local == null) {
            loadSnapshot();
            local = snapshot;
        }
        return local != null ? Map.copyOf(local) : Map.of();
    }

    /**
     * Validates and saves changed values to disk.
     *
     * <p>Synchronized to prevent concurrent saves from creating TOCTOU
     * race conditions on the snapshot and file.
     *
     * @param changedValues map of key-value pairs that were modified
     * @return save result
     */
    public synchronized SaveResult save(Map<String, String> changedValues) {
        if (changedValues.isEmpty()) {
            return new SaveResult.Success(0);
        }

        // Validate each field
        List<ValidationError> errors = new ArrayList<>();
        for (var entry : changedValues.entrySet()) {
            validator.validateField(entry.getKey(), entry.getValue())
                    .ifPresent(errors::add);
        }

        if (!errors.isEmpty()) {
            return new SaveResult.ValidationFailure(errors);
        }

        // Read snapshot once for consistency
        Map<String, String> currentSnapshot = getSnapshot();
        if (currentSnapshot.isEmpty() && snapshot == null) {
            return new SaveResult.IoFailure(
                    "Cannot save: properties not loaded from " + configFilePath, null);
        }

        // Build full values map for cross-property validation
        Map<String, String> allValues = new LinkedHashMap<>(currentSnapshot);
        allValues.putAll(changedValues);

        List<ValidationError> crossErrors = validator.validateCrossProperty(allValues);
        if (!crossErrors.isEmpty()) {
            return new SaveResult.ValidationFailure(crossErrors);
        }

        // Write to disk
        try {
            persistence.writeChanges(configFilePath, changedValues);
        } catch (IOException e) {
            return new SaveResult.IoFailure(
                    "Could not save to " + configFilePath, e);
        }

        // Update snapshot atomically using the same base we validated against
        Map<String, String> updated = new LinkedHashMap<>(currentSnapshot);
        updated.putAll(changedValues);
        this.snapshot = updated;

        LOG.info("Settings saved: {} properties written to {}",
                changedValues.size(), configFilePath);
        return new SaveResult.Success(changedValues.size());
    }

    /**
     * Returns property metadata for the given tab.
     */
    public List<PropertyMetadata> getAllByTab(Tab tab) {
        return registry.findByTab(tab);
    }

    /**
     * Returns the full registry for UI building.
     */
    public PropertyMetadataRegistry getRegistry() {
        return registry;
    }

    /**
     * Returns the validator for real-time field validation in the UI.
     */
    public PropertyValidator getValidator() {
        return validator;
    }
}
