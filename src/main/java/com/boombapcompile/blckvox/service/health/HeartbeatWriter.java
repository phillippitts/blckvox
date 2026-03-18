package com.boombapcompile.blckvox.service.health;

import com.boombapcompile.blckvox.config.properties.HealthProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Periodically writes a heartbeat file with the current health status.
 *
 * <p>Format: {@code <epoch_ms> <STATUS>} (e.g. {@code 1711234567890 UP}).
 * Uses atomic write (temp file + rename) to prevent partial reads.
 *
 * <p>Staleness contract: if the file is not updated within
 * {@code 2 * interval-ms}, the application should be considered unhealthy.
 */
@Component
public class HeartbeatWriter {

    private static final Logger LOG = LogManager.getLogger(HeartbeatWriter.class);

    private final ApplicationHealthService healthService;
    private final HealthProperties properties;
    private boolean parentCreated;

    public HeartbeatWriter(ApplicationHealthService healthService,
                           HealthProperties properties) {
        this.healthService = healthService;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "#{${health.heartbeat.interval-ms:30000}}")
    void writeHeartbeat() {
        if (!properties.enabled()) {
            return;
        }

        try {
            HealthStatus status = healthService.check();
            Path target = Path.of(properties.path()).toAbsolutePath();
            Path parent = target.getParent();

            if (!parentCreated && parent != null) {
                Files.createDirectories(parent);
                parentCreated = true;
            }

            String content = status.timestamp().toEpochMilli() + " " + status.status() + "\n";
            Path tmp = parent != null
                    ? parent.resolve(target.getFileName() + ".tmp")
                    : Path.of(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            LOG.warn("Failed to write heartbeat file: {}", ex.toString());
        }
    }
}
