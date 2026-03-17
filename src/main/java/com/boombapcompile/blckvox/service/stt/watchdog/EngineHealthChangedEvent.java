package com.boombapcompile.blckvox.service.stt.watchdog;

import java.time.Instant;

/**
 * Published when an STT engine's health state transitions (e.g., HEALTHY to DEGRADED).
 * Consumed by SystemTrayManager for tooltip/notification updates.
 */
public record EngineHealthChangedEvent(
        String engine,
        SttEngineWatchdog.EngineState previousState,
        SttEngineWatchdog.EngineState currentState,
        Instant timestamp
) {
    public EngineHealthChangedEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
