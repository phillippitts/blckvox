package com.boombapcompile.blckvox.service.settings;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and writes configuration properties from/to a properties file.
 *
 * <p>Read uses {@link Properties#load} for standard behavior.
 * Write uses line-by-line replacement to preserve comments, blank lines,
 * and key ordering.
 */
@Component
public class FileConfigurationPersistence {

    /**
     * Reads all key-value pairs from the given properties file.
     *
     * @param path path to properties file
     * @return map of key-value pairs
     * @throws IOException if file cannot be read
     */
    public Map<String, String> readAll(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Properties file not found: " + path);
        }

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            props.load(reader);
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            result.put(name, props.getProperty(name));
        }
        return result;
    }

    /**
     * Writes changed values to the properties file, preserving comments and ordering.
     *
     * <p>Existing key-value lines are updated in-place. New keys are appended at the end.
     * Uses atomic write via temp file + rename to prevent partial writes.
     *
     * @param path    path to properties file
     * @param changes map of key-value pairs to write (only changed values)
     * @throws IOException if file cannot be written
     */
    public void writeChanges(Path path, Map<String, String> changes) throws IOException {
        if (changes.isEmpty()) {
            return;
        }

        List<String> lines = Files.readAllLines(path);
        Map<String, String> remaining = new LinkedHashMap<>(changes);
        List<String> updatedLines = new ArrayList<>(lines.size());

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip comment lines and blank lines — pass through unchanged
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                updatedLines.add(line);
                continue;
            }

            // Parse key from the line (split on first = or :)
            String key = extractKey(trimmed);
            if (key != null && remaining.containsKey(key)) {
                String newValue = remaining.remove(key);
                // Preserve the separator style (= vs : and whitespace)
                updatedLines.add(rebuildLine(line, key, newValue));
            } else {
                updatedLines.add(line);
            }
        }

        // Append any new keys not found in the file
        for (var entry : remaining.entrySet()) {
            updatedLines.add(entry.getKey() + "=" + entry.getValue());
        }

        // Atomic write: temp file + rename (fallback for filesystems without ATOMIC_MOVE)
        Path tempFile = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tempFile, updatedLines);
        try {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Extracts the property key from a line, splitting on the first unescaped = or :.
     */
    static String extractKey(String line) {
        int eqIdx = line.indexOf('=');
        int colonIdx = line.indexOf(':');

        int sepIdx;
        if (eqIdx < 0 && colonIdx < 0) {
            return null;
        } else if (eqIdx < 0) {
            sepIdx = colonIdx;
        } else if (colonIdx < 0) {
            sepIdx = eqIdx;
        } else {
            sepIdx = Math.min(eqIdx, colonIdx);
        }

        return line.substring(0, sepIdx).trim();
    }

    /**
     * Rebuilds a property line preserving the original separator and whitespace style.
     */
    private static String rebuildLine(String originalLine, String key, String newValue) {
        // Find where the key ends and the separator begins
        int keyEnd = originalLine.indexOf(key) + key.length();
        String afterKey = originalLine.substring(keyEnd);

        // Find the separator (= or :) and preserve whitespace around it
        int sepOffset = -1;
        for (int i = 0; i < afterKey.length(); i++) {
            char c = afterKey.charAt(i);
            if (c == '=' || c == ':') {
                sepOffset = i;
                break;
            }
        }

        if (sepOffset < 0) {
            return key + "=" + newValue;
        }

        // Preserve whitespace before separator and separator char
        String beforeSep = afterKey.substring(0, sepOffset);
        char sep = afterKey.charAt(sepOffset);

        // Preserve whitespace after separator (typically one space)
        String afterSep = afterKey.substring(sepOffset + 1);
        int wsEnd = 0;
        while (wsEnd < afterSep.length()
                && (afterSep.charAt(wsEnd) == ' ' || afterSep.charAt(wsEnd) == '\t')) {
            wsEnd++;
        }
        String whitespaceAfterSep = afterSep.substring(0, wsEnd);

        return key + beforeSep + sep + whitespaceAfterSep + newValue;
    }
}
