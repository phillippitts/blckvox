package com.boombapcompile.blckvox.testutil;

import com.boombapcompile.blckvox.service.settings.FileConfigurationPersistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory test double for {@link FileConfigurationPersistence}.
 * Mirrors the same public method signatures for duck-typing in tests.
 */
public class FakeConfigurationPersistence extends FileConfigurationPersistence {

    private final Map<String, String> store = new LinkedHashMap<>();
    private boolean failOnWrite;
    private boolean failOnRead;

    public FakeConfigurationPersistence() {
    }

    public FakeConfigurationPersistence(Map<String, String> initialValues) {
        store.putAll(initialValues);
    }

    public void setFailOnWrite(boolean fail) {
        this.failOnWrite = fail;
    }

    public void setFailOnRead(boolean fail) {
        this.failOnRead = fail;
    }

    @Override
    public Map<String, String> readAll(Path path) throws IOException {
        if (failOnRead) {
            throw new IOException("Simulated read failure");
        }
        return new LinkedHashMap<>(store);
    }

    @Override
    public void writeChanges(Path path, Map<String, String> changes) throws IOException {
        if (changes.isEmpty()) {
            return;
        }
        if (failOnWrite) {
            throw new IOException("Simulated write failure");
        }
        store.putAll(changes);
    }

    public Map<String, String> getStore() {
        return Map.copyOf(store);
    }
}
