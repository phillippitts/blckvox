package com.boombapcompile.blckvox.service.health;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of application health at a point in time.
 */
public record HealthStatus(
        Status status,
        Instant timestamp,
        Map<String, String> details
) {

    public HealthStatus {
        details = Map.copyOf(details);
    }

    public enum Status { UP, DEGRADED, DOWN }
}
