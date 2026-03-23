package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.SaveResult;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Tab;
import com.boombapcompile.blckvox.testutil.FakeConfigurationPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationServiceTest {

    private PropertyMetadataRegistry registry;
    private PropertyValidator validator;
    private FakeConfigurationPersistence persistence;
    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        registry = new PropertyMetadataRegistry();
        validator = new PropertyValidator(registry);
        persistence = new FakeConfigurationPersistence();
        service = new ConfigurationService(registry, validator, persistence,
                Path.of("test.properties"));
    }

    @Test
    @DisplayName("loadSnapshot fills missing keys with defaults from metadata")
    void loadSnapshotFillsDefaults() {
        service.loadSnapshot();

        Map<String, String> snapshot = service.getSnapshot();
        assertThat(snapshot).hasSize(59);
        assertThat(snapshot.get("hotkey.threshold-ms")).isEqualTo("300");
    }

    @Test
    @DisplayName("loadSnapshot returns all 59 registry keys even if file has fewer")
    void loadSnapshotReturnsAll59Keys() {
        persistence = new FakeConfigurationPersistence(
                Map.of("hotkey.threshold-ms", "500"));
        service = new ConfigurationService(registry, validator, persistence,
                Path.of("test.properties"));

        service.loadSnapshot();

        Map<String, String> snapshot = service.getSnapshot();
        assertThat(snapshot).hasSize(59);
        assertThat(snapshot.get("hotkey.threshold-ms")).isEqualTo("500");
    }

    @Test
    @DisplayName("loadSnapshot with empty file returns all 59 defaults")
    void loadSnapshotEmptyFileReturns59Defaults() {
        service.loadSnapshot();

        assertThat(service.getSnapshot()).hasSize(59);
    }

    @Test
    @DisplayName("loadSnapshot returns IoFailure when file not found")
    void loadSnapshotFileNotFound() {
        persistence.setFailOnRead(true);

        SaveResult result = service.loadSnapshot();
        assertThat(result).isInstanceOf(SaveResult.IoFailure.class);
    }

    @Test
    @DisplayName("loadSnapshot ignores extra keys not in registry")
    void loadSnapshotIgnoresExtraKeys() {
        persistence = new FakeConfigurationPersistence(
                Map.of("unknown.key", "value", "hotkey.threshold-ms", "500"));
        service = new ConfigurationService(registry, validator, persistence,
                Path.of("test.properties"));

        service.loadSnapshot();

        assertThat(service.getSnapshot()).hasSize(59);
        assertThat(service.getSnapshot()).doesNotContainKey("unknown.key");
    }

    @Test
    @DisplayName("save validates each field and returns ValidationFailure if invalid")
    void saveValidatesFields() {
        service.loadSnapshot();

        SaveResult result = service.save(Map.of("hotkey.threshold-ms", "abc"));

        assertThat(result).isInstanceOf(SaveResult.ValidationFailure.class);
        var failure = (SaveResult.ValidationFailure) result;
        assertThat(failure.errors()).hasSize(1);
        assertThat(failure.errors().getFirst().key()).isEqualTo("hotkey.threshold-ms");
    }

    @Test
    @DisplayName("save validates cross-property constraints")
    void saveValidatesCrossProperty() {
        service.loadSnapshot();

        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("threadpool.stt.core-pool-size", "8");
        changes.put("threadpool.stt.max-pool-size", "4");

        SaveResult result = service.save(changes);

        assertThat(result).isInstanceOf(SaveResult.ValidationFailure.class);
    }

    @Test
    @DisplayName("save writes to persistence on success and returns Success with count")
    void saveWritesToPersistenceOnSuccess() {
        service.loadSnapshot();

        SaveResult result = service.save(Map.of("hotkey.threshold-ms", "500"));

        assertThat(result).isInstanceOf(SaveResult.Success.class);
        assertThat(((SaveResult.Success) result).changedCount()).isEqualTo(1);
        assertThat(persistence.getStore()).containsEntry("hotkey.threshold-ms", "500");
    }

    @Test
    @DisplayName("save returns IoFailure on write failure, snapshot unchanged")
    void saveReturnsIoFailureOnWriteError() {
        service.loadSnapshot();
        persistence.setFailOnWrite(true);

        SaveResult result = service.save(Map.of("hotkey.threshold-ms", "500"));

        assertThat(result).isInstanceOf(SaveResult.IoFailure.class);
        // Snapshot should still have old value
        assertThat(service.getSnapshot().get("hotkey.threshold-ms")).isEqualTo("300");
    }

    @Test
    @DisplayName("save with empty changes returns Success(0)")
    void saveEmptyChangesReturnsSuccess0() {
        SaveResult result = service.save(Map.of());

        assertThat(result).isInstanceOf(SaveResult.Success.class);
        assertThat(((SaveResult.Success) result).changedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("save with mixed valid+invalid returns ValidationFailure, does NOT write")
    void saveMixedValidInvalidDoesNotWrite() {
        service.loadSnapshot();

        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("hotkey.threshold-ms", "500");
        changes.put("stt.reconciliation.overlap-threshold", "2.0");

        SaveResult result = service.save(changes);

        assertThat(result).isInstanceOf(SaveResult.ValidationFailure.class);
        assertThat(persistence.getStore()).doesNotContainKey("hotkey.threshold-ms");
    }

    @Test
    @DisplayName("save with cross-property violation does NOT write to disk")
    void saveCrossPropertyViolationDoesNotWrite() {
        service.loadSnapshot();

        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("threadpool.event.core-pool-size", "10");
        changes.put("threadpool.event.max-pool-size", "2");

        SaveResult result = service.save(changes);

        assertThat(result).isInstanceOf(SaveResult.ValidationFailure.class);
        assertThat(persistence.getStore()).doesNotContainKey("threadpool.event.core-pool-size");
    }

    @Test
    @DisplayName("getAllByTab delegates to registry")
    void getAllByTabDelegates() {
        assertThat(service.getAllByTab(Tab.BASIC)).hasSize(14);
        assertThat(service.getAllByTab(Tab.ADVANCED)).hasSize(45);
    }

    @Test
    @DisplayName("save updates snapshot on success")
    void saveUpdatesSnapshotOnSuccess() {
        service.loadSnapshot();

        service.save(Map.of("hotkey.threshold-ms", "500"));

        assertThat(service.getSnapshot().get("hotkey.threshold-ms")).isEqualTo("500");
    }

    @Test
    @DisplayName("save with unknown key returns ValidationFailure")
    void saveWithUnknownKeyReturnsValidationFailure() {
        service.loadSnapshot();

        SaveResult result = service.save(Map.of("nonexistent.key", "value"));

        assertThat(result).isInstanceOf(SaveResult.ValidationFailure.class);
        var failure = (SaveResult.ValidationFailure) result;
        assertThat(failure.errors().getFirst().message()).contains("Unknown");
    }

    @Test
    @DisplayName("getSnapshot returns immutable map")
    void getSnapshotReturnsImmutableMap() {
        service.loadSnapshot();

        Map<String, String> snapshot = service.getSnapshot();

        assertThat(snapshot).isUnmodifiable();
    }

    @Test
    @DisplayName("getSnapshot returns empty map when load fails")
    void getSnapshotReturnsEmptyWhenLoadFails() {
        persistence.setFailOnRead(true);

        Map<String, String> snapshot = service.getSnapshot();

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("save returns IoFailure when snapshot not loaded")
    void saveReturnsIoFailureWhenSnapshotNotLoaded() {
        persistence.setFailOnRead(true);

        SaveResult result = service.save(Map.of("hotkey.threshold-ms", "500"));

        assertThat(result).isInstanceOf(SaveResult.IoFailure.class);
        assertThat(((SaveResult.IoFailure) result).message())
                .contains("not loaded");
    }

    @Test
    @DisplayName("loadSnapshot caches and returns Success on subsequent calls")
    void loadSnapshotCachesOnSubsequentCalls() {
        SaveResult first = service.loadSnapshot();
        SaveResult second = service.loadSnapshot();

        assertThat(first).isInstanceOf(SaveResult.Success.class);
        assertThat(second).isInstanceOf(SaveResult.Success.class);
    }
}
