package com.boombapcompile.blckvox.service.health;

import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog.EngineState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes application health based on STT engine states.
 */
@Component
public class ApplicationHealthService {

    private final SttEngineWatchdog watchdog;

    public ApplicationHealthService(@Autowired(required = false) SttEngineWatchdog watchdog) {
        this.watchdog = watchdog;
    }

    /**
     * Returns a fresh health snapshot.
     *
     * <ul>
     *   <li><b>UP</b> — at least one engine HEALTHY</li>
     *   <li><b>DEGRADED</b> — at least one engine enabled, but none HEALTHY</li>
     *   <li><b>DOWN</b> — no engines enabled</li>
     * </ul>
     */
    public HealthStatus check() {
        if (watchdog == null) {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("watchdog", "disabled");
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            details.put("uptimeMs", String.valueOf(uptimeMs));
            return new HealthStatus(HealthStatus.Status.UP, Instant.now(), details);
        }

        Map<String, EngineState> states = watchdog.getEngineStates();
        Map<String, String> details = buildDetails(states);

        boolean anyHealthy = states.values().stream()
                .anyMatch(s -> s == EngineState.HEALTHY);
        boolean anyEnabled = states.values().stream()
                .anyMatch(s -> s != EngineState.DISABLED);

        HealthStatus.Status status;
        if (anyHealthy) {
            status = HealthStatus.Status.UP;
        } else if (anyEnabled) {
            status = HealthStatus.Status.DEGRADED;
        } else {
            status = HealthStatus.Status.DOWN;
        }

        return new HealthStatus(status, Instant.now(), details);
    }

    private Map<String, String> buildDetails(Map<String, EngineState> states) {
        Map<String, String> details = new LinkedHashMap<>();
        states.forEach((name, state) -> details.put(name, state.name()));
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        details.put("uptimeMs", String.valueOf(uptimeMs));
        return details;
    }
}
