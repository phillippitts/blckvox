package com.boombapcompile.blckvox.service.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileConfigurationPersistenceTest {

    @TempDir
    Path tempDir;

    private final FileConfigurationPersistence persistence = new FileConfigurationPersistence();

    private Path createFile(String content) throws IOException {
        Path file = tempDir.resolve("test.properties");
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("readAll returns key-value pairs from standard properties file")
    void readAllReturnsKeyValuePairs() throws IOException {
        Path file = createFile("foo=bar\nbaz=qux\n");

        Map<String, String> result = persistence.readAll(file);

        assertThat(result).containsEntry("foo", "bar");
        assertThat(result).containsEntry("baz", "qux");
    }

    @Test
    @DisplayName("readAll with empty file returns empty map")
    void readAllEmptyFile() throws IOException {
        Path file = createFile("");

        assertThat(persistence.readAll(file)).isEmpty();
    }

    @Test
    @DisplayName("readAll with nonexistent file throws IOException")
    void readAllMissingFileThrows() {
        Path missing = tempDir.resolve("nonexistent.properties");

        assertThatThrownBy(() -> persistence.readAll(missing))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("readAll with read-only file succeeds")
    void readAllReadOnlyFileSucceeds() throws IOException {
        Path file = createFile("key=value\n");
        file.toFile().setReadOnly();

        try {
            assertThat(persistence.readAll(file)).containsEntry("key", "value");
        } finally {
            restoreWritable(file);
        }
    }

    @Test
    @DisplayName("writeChanges updates existing key value in-place")
    void writeChangesUpdatesExistingKey() throws IOException {
        Path file = createFile("hotkey.threshold-ms=300\n");

        persistence.writeChanges(file, Map.of("hotkey.threshold-ms", "500"));

        assertThat(Files.readString(file)).contains("hotkey.threshold-ms=500");
    }

    @Test
    @DisplayName("writeChanges appends new key at end of file")
    void writeChangesAppendsNewKey() throws IOException {
        Path file = createFile("existing=value\n");

        persistence.writeChanges(file, Map.of("new.key", "newval"));

        String content = Files.readString(file);
        assertThat(content).contains("existing=value");
        assertThat(content).contains("new.key=newval");
    }

    @Test
    @DisplayName("writeChanges with empty changes does not modify file")
    void writeChangesEmptyChangesNoOp() throws IOException {
        String original = "key=value\n";
        Path file = createFile(original);

        persistence.writeChanges(file, Map.of());

        assertThat(Files.readString(file)).isEqualTo(original);
    }

    @Test
    @DisplayName("Round-trip: write then read back")
    void roundTrip() throws IOException {
        Path file = createFile("a=1\nb=2\n");

        persistence.writeChanges(file, Map.of("a", "10", "b", "20"));

        Map<String, String> result = persistence.readAll(file);
        assertThat(result).containsEntry("a", "10");
        assertThat(result).containsEntry("b", "20");
    }

    @Test
    @DisplayName("writeChanges on read-only directory throws IOException")
    void writeChangesReadOnlyDirectoryThrows() throws IOException {
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        Path file = readOnlyDir.resolve("test.properties");
        Files.writeString(file, "key=value\n");
        readOnlyDir.toFile().setReadOnly();

        try {
            assertThatThrownBy(() -> persistence.writeChanges(file,
                    Map.of("key", "newval")))
                    .isInstanceOf(IOException.class);
        } finally {
            readOnlyDir.toFile().setWritable(true);
        }
    }

    @Test
    @DisplayName("Comment preservation: # lines survive write")
    void commentPreservation() throws IOException {
        String content = """
                # This is a comment
                key=value
                # Another comment
                other=stuff
                """;
        Path file = createFile(content);

        persistence.writeChanges(file, Map.of("key", "updated"));

        String result = Files.readString(file);
        assertThat(result).contains("# This is a comment");
        assertThat(result).contains("# Another comment");
        assertThat(result).contains("key=updated");
        assertThat(result).contains("other=stuff");
    }

    @Test
    @DisplayName("Key with = in value handled correctly (split on first = only)")
    void keyWithEqualsInValue() throws IOException {
        Path file = createFile("path=/usr/local/bin=special\n");

        Map<String, String> result = persistence.readAll(file);
        assertThat(result).containsEntry("path", "/usr/local/bin=special");
    }

    @Test
    @DisplayName("Commented-out property NOT uncommented — new active line appended")
    void commentedOutPropertyNotUncommented() throws IOException {
        String content = """
                # audio.capture.device-name=Old Device
                other=value
                """;
        Path file = createFile(content);

        persistence.writeChanges(file, Map.of("audio.capture.device-name", "New Device"));

        String result = Files.readString(file);
        assertThat(result).contains("# audio.capture.device-name=Old Device");
        assertThat(result).contains("audio.capture.device-name=New Device");
    }

    @Test
    @DisplayName("Whitespace around separator preserved")
    void whitespaceAroundSeparatorPreserved() throws IOException {
        Path file = createFile("key = value\n");

        persistence.writeChanges(file, Map.of("key", "updated"));

        String result = Files.readString(file);
        assertThat(result).contains("key = updated");
    }

    @Test
    @DisplayName("Property expansion preserved as-is (not resolved)")
    void propertyExpansionPreservedAsIs() throws IOException {
        Path file = createFile("path=${java.io.tmpdir}/blckvox\n");

        Map<String, String> result = persistence.readAll(file);
        // Properties.load() does NOT resolve ${...} — it's literal
        assertThat(result).containsEntry("path", "${java.io.tmpdir}/blckvox");
    }

    @Test
    @DisplayName("extractKey handles colon separator")
    void extractKeyWithColonSeparator() {
        assertThat(FileConfigurationPersistence.extractKey("key:value"))
                .isEqualTo("key");
    }

    @Test
    @DisplayName("extractKey handles colon when both = and : present (uses first)")
    void extractKeyWithBothSeparators() {
        // Colon at index 3, equals at index 9 → colon wins
        assertThat(FileConfigurationPersistence.extractKey("key:val=ue"))
                .isEqualTo("key");
        // Equals at index 3, colon at index 9 → equals wins
        assertThat(FileConfigurationPersistence.extractKey("key=val:ue"))
                .isEqualTo("key");
    }

    @Test
    @DisplayName("extractKey returns null for line without separator")
    void extractKeyNoSeparator() {
        assertThat(FileConfigurationPersistence.extractKey("noseparator")).isNull();
    }

    @Test
    @DisplayName("writeChanges updates key with colon separator in-place")
    void writeChangesColonSeparator() throws IOException {
        Path file = createFile("key: value\n");

        persistence.writeChanges(file, Map.of("key", "updated"));

        String result = Files.readString(file);
        assertThat(result).contains("key: updated");
    }

    private void restoreWritable(Path path) {
        path.toFile().setWritable(true);
    }
}
