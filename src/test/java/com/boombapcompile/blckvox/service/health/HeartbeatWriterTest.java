package com.boombapcompile.blckvox.service.health;

import com.boombapcompile.blckvox.config.properties.HealthProperties;
import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HeartbeatWriterTest {

    @Test
    void writesFileWithCorrectFormat(@TempDir Path tempDir) throws IOException {
        Path heartbeatFile = tempDir.resolve("heartbeat");
        HealthProperties props = new HealthProperties(true, heartbeatFile.toString(), 30_000L);
        ApplicationHealthService healthService = createHealthService();

        HeartbeatWriter writer = new HeartbeatWriter(healthService, props);
        writer.writeHeartbeat();

        assertThat(heartbeatFile).exists();
        String content = Files.readString(heartbeatFile).trim();
        String[] parts = content.split(" ");
        assertThat(parts).hasSize(2);
        assertThat(Long.parseLong(parts[0])).isPositive();
        assertThat(parts[1]).isEqualTo("UP");
    }

    @Test
    void createsParentDirectories(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("sub/dir/heartbeat");
        HealthProperties props = new HealthProperties(true, nested.toString(), 30_000L);
        ApplicationHealthService healthService = createHealthService();

        HeartbeatWriter writer = new HeartbeatWriter(healthService, props);
        writer.writeHeartbeat();

        assertThat(nested).exists();
    }

    @Test
    void noFileWrittenWhenDisabled(@TempDir Path tempDir) {
        Path heartbeatFile = tempDir.resolve("heartbeat");
        HealthProperties props = new HealthProperties(false, heartbeatFile.toString(), 30_000L);
        ApplicationHealthService healthService = createHealthService();

        HeartbeatWriter writer = new HeartbeatWriter(healthService, props);
        writer.writeHeartbeat();

        assertThat(heartbeatFile).doesNotExist();
    }

    @Test
    void handlesIoErrorGracefully() {
        // Point to an invalid path that can't be written
        HealthProperties props = new HealthProperties(true, "/\u0000invalid", 30_000L);
        ApplicationHealthService healthService = createHealthService();

        HeartbeatWriter writer = new HeartbeatWriter(healthService, props);

        assertThatCode(() -> writer.writeHeartbeat()).doesNotThrowAnyException();
    }

    @Test
    void overwritesPreviousHeartbeat(@TempDir Path tempDir) throws IOException {
        Path heartbeatFile = tempDir.resolve("heartbeat");
        HealthProperties props = new HealthProperties(true, heartbeatFile.toString(), 30_000L);
        ApplicationHealthService healthService = createHealthService();

        HeartbeatWriter writer = new HeartbeatWriter(healthService, props);
        writer.writeHeartbeat();
        String first = Files.readString(heartbeatFile).trim();

        writer.writeHeartbeat();
        String second = Files.readString(heartbeatFile).trim();

        // Both valid, second has same or later timestamp
        long firstTs = Long.parseLong(first.split(" ")[0]);
        long secondTs = Long.parseLong(second.split(" ")[0]);
        assertThat(secondTs).isGreaterThanOrEqualTo(firstTs);
    }

    private ApplicationHealthService createHealthService() {
        SttWatchdogProperties watchdogProps = new SttWatchdogProperties(
                true, 60, 3, 10, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L, 5);
        SttEngine engine = new StubEngine("vosk");
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), watchdogProps, event -> { });
        return new ApplicationHealthService(watchdog);
    }

    static class StubEngine implements SttEngine {
        final String name;

        StubEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() { }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public void close() { }
    }
}
