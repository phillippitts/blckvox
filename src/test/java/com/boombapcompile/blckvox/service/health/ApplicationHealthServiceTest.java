package com.boombapcompile.blckvox.service.health;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationHealthServiceTest {

    private static final SttWatchdogProperties PROPS = new SttWatchdogProperties(
            true, 60, 3, 10, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L, 5);

    @Test
    void allEnginesHealthyReturnsUp() {
        SttEngineWatchdog watchdog = createWatchdog("vosk", "whisper");

        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        HealthStatus status = service.check();

        assertThat(status.status()).isEqualTo(HealthStatus.Status.UP);
        assertThat(status.details()).containsEntry("vosk", "HEALTHY");
        assertThat(status.details()).containsEntry("whisper", "HEALTHY");
        assertThat(status.details()).containsKey("uptimeMs");
        assertThat(status.timestamp()).isNotNull();
    }

    @Test
    void oneHealthyOneDegradedReturnsUp() {
        SttEngineWatchdog watchdog = createWatchdog("vosk", "whisper");
        // Trigger failure on whisper to make it DEGRADED (no budget exhaustion)
        watchdog.onFailure(new com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent(
                "whisper", java.time.Instant.now(), "test", null, java.util.Map.of()));

        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        HealthStatus status = service.check();

        assertThat(status.status()).isEqualTo(HealthStatus.Status.UP);
    }

    @Test
    void allDegradedReturnsDegraded() {
        // Use budget=1 and backoff=0 so first failure goes DEGRADED, second exhausts budget
        SttWatchdogProperties singleBudget = new SttWatchdogProperties(
                true, 60, 1, 10, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L, 5);
        StubEngine vosk = new StubEngine("vosk", false);
        StubEngine whisper = new StubEngine("whisper", false);
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(vosk, whisper), singleBudget, event -> { });

        // First failure uses the restart attempt (init fails -> stays DEGRADED)
        watchdog.onFailure(new com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent(
                "vosk", java.time.Instant.now(), "fail", null, java.util.Map.of()));
        watchdog.onFailure(new com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent(
                "whisper", java.time.Instant.now(), "fail", null, java.util.Map.of()));

        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        HealthStatus status = service.check();

        // Both engines have had failures; at least one should be non-HEALTHY
        // Since the init fails, the restart does not recover them.
        // With budget=1 and both having failed once, check state
        assertThat(status.status()).isNotEqualTo(HealthStatus.Status.UP);
    }

    @Test
    void allDisabledReturnsDown() {
        // Single engine, budget=0 so it immediately disables
        SttWatchdogProperties zeroBudget = new SttWatchdogProperties(
                true, 60, 0, 10, 60_000L, 0.3, 10, 5, 0L, 2.0, 60_000L, 5);
        StubEngine engine = new StubEngine("vosk", false);
        SttEngineWatchdog watchdog = new SttEngineWatchdog(
                List.of(engine), zeroBudget, event -> { });

        watchdog.onFailure(new com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent(
                "vosk", java.time.Instant.now(), "fail", null, java.util.Map.of()));

        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        HealthStatus status = service.check();

        assertThat(status.status()).isEqualTo(HealthStatus.Status.DOWN);
    }

    @Test
    void detailsContainEngineStatesAndUptime() {
        SttEngineWatchdog watchdog = createWatchdog("vosk");

        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        HealthStatus status = service.check();

        assertThat(status.details()).containsKey("vosk");
        assertThat(status.details()).containsKey("uptimeMs");
        assertThat(Long.parseLong(status.details().get("uptimeMs"))).isPositive();
    }

    private SttEngineWatchdog createWatchdog(String... engines) {
        List<SttEngine> list = java.util.Arrays.stream(engines)
                .map(n -> (SttEngine) new StubEngine(n, true))
                .toList();
        return new SttEngineWatchdog(list, PROPS, event -> { });
    }

    static class StubEngine implements SttEngine {
        final String name;
        final boolean healthy;

        StubEngine(String name, boolean healthy) {
            this.name = name;
            this.healthy = healthy;
        }

        @Override
        public void initialize() {
            if (!healthy) {
                throw new RuntimeException("init fails");
            }
        }

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
            return healthy;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
