package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.PropertyType;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Tab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyMetadataRegistryTest {

    private PropertyMetadataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PropertyMetadataRegistry();
    }

    @Test
    @DisplayName("Registry contains exactly 59 entries")
    void registryContains59Entries() {
        assertThat(registry.getAll()).hasSize(59);
    }

    @Test
    @DisplayName("All keys are unique (no duplicates)")
    void allKeysAreUnique() {
        List<String> keys = registry.getAll().stream()
                .map(PropertyMetadata::key)
                .toList();
        Set<String> unique = new HashSet<>(keys);
        assertThat(unique).hasSameSizeAs(keys);
    }

    @Test
    @DisplayName("findByKey returns correct entry")
    void findByKeyReturnsCorrectEntry() {
        var result = registry.findByKey("hotkey.threshold-ms");
        assertThat(result).isPresent();
        assertThat(result.get().displayName()).isEqualTo("Threshold");
    }

    @Test
    @DisplayName("findByKey returns empty for unknown key")
    void findByKeyReturnsEmptyForUnknownKey() {
        assertThat(registry.findByKey("nonexistent.property")).isEmpty();
    }

    @Test
    @DisplayName("findByTab(BASIC) returns 14 entries")
    void findByTabBasicReturns14() {
        assertThat(registry.findByTab(Tab.BASIC)).hasSize(14);
    }

    @Test
    @DisplayName("findByTab(ADVANCED) returns 45 entries")
    void findByTabAdvancedReturns45() {
        assertThat(registry.findByTab(Tab.ADVANCED)).hasSize(45);
    }

    @Test
    @DisplayName("findBySection returns grouped entries")
    void findBySectionReturnsGroupedEntries() {
        assertThat(registry.findBySection("Hotkey")).hasSize(4);
        assertThat(registry.findBySection("Watchdog")).hasSize(12);
        assertThat(registry.findBySection("Thread Pools (STT)")).hasSize(4);
    }

    @Test
    @DisplayName("Spot-check: hotkey.threshold-ms is Basic INT with min/max")
    void spotCheckHotkeyThreshold() {
        var meta = registry.findByKey("hotkey.threshold-ms").orElseThrow();
        assertThat(meta.tab()).isEqualTo(Tab.BASIC);
        assertThat(meta.type()).isEqualTo(PropertyType.INT);
        assertThat(meta.constraints().min()).isEqualTo(100);
        assertThat(meta.constraints().max()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Spot-check: stt.reconciliation.overlap-threshold is Advanced DOUBLE 0.0-1.0")
    void spotCheckOverlapThreshold() {
        var meta = registry.findByKey("stt.reconciliation.overlap-threshold").orElseThrow();
        assertThat(meta.tab()).isEqualTo(Tab.ADVANCED);
        assertThat(meta.type()).isEqualTo(PropertyType.DOUBLE);
        assertThat(meta.constraints().minDouble()).isEqualTo(0.0);
        assertThat(meta.constraints().maxDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Spot-check: hotkey.type is ENUM with kebab-case values matching properties file")
    void spotCheckHotkeyType() {
        var meta = registry.findByKey("hotkey.type").orElseThrow();
        assertThat(meta.type()).isEqualTo(PropertyType.ENUM);
        assertThat(meta.constraints().enumValues())
                .containsExactly("single-key", "double-tap", "modifier-combo");
    }

    @Test
    @DisplayName("Spot-check: hotkey.toggle-mode is BOOLEAN")
    void spotCheckToggleMode() {
        var meta = registry.findByKey("hotkey.toggle-mode").orElseThrow();
        assertThat(meta.type()).isEqualTo(PropertyType.BOOLEAN);
    }

    @Test
    @DisplayName("Spot-check: LONG properties use longRange constraints")
    void spotCheckLongProperties() {
        var meta = registry.findByKey("stt.watchdog.health-summary-interval-millis")
                .orElseThrow();
        assertThat(meta.type()).isEqualTo(PropertyType.LONG);
        assertThat(meta.constraints().minLong()).isEqualTo(1000L);
        assertThat(meta.constraints().maxLong()).isEqualTo(3600000L);
        // Ensure Integer constraints are null (not set via intRange)
        assertThat(meta.constraints().min()).isNull();
        assertThat(meta.constraints().max()).isNull();
    }

    @Test
    @DisplayName("Spot-check: typing.paste-shortcut is ENUM")
    void spotCheckPasteShortcut() {
        var meta = registry.findByKey("typing.paste-shortcut").orElseThrow();
        assertThat(meta.type()).isEqualTo(PropertyType.ENUM);
        assertThat(meta.constraints().enumValues())
                .containsExactly("os-default", "META+V", "CONTROL+V");
    }

    @Test
    @DisplayName("Nested record properties are present: threadpool.stt.core-pool-size")
    void nestedRecordSttCorePoolSize() {
        assertThat(registry.findByKey("threadpool.stt.core-pool-size")).isPresent();
    }

    @Test
    @DisplayName("Nested record properties are present: threadpool.event.queue-capacity")
    void nestedRecordEventQueueCapacity() {
        assertThat(registry.findByKey("threadpool.event.queue-capacity")).isPresent();
    }
}
