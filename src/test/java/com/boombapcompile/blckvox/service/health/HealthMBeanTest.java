package com.boombapcompile.blckvox.service.health;

import com.boombapcompile.blckvox.config.properties.SttWatchdogProperties;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthMBeanTest {

    private static final String OBJECT_NAME = "com.boombapcompile.blckvox:type=Health";
    private Health health;

    @BeforeEach
    void setUp() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, 60_000L, 0.3, 10, 5, 1000L, 2.0, 60_000L, 5);
        SttEngine engine = new StubEngine("vosk");
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, event -> { });
        ApplicationHealthService service = new ApplicationHealthService(watchdog);
        health = new Health(service);
    }

    @AfterEach
    void tearDown() {
        if (health != null) {
            health.deregister();
        }
    }

    @Test
    void registersAtCorrectObjectName() throws Exception {
        health.register();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(OBJECT_NAME);
        assertThat(server.isRegistered(name)).isTrue();
    }

    @Test
    void getStatusReturnsCorrectValue() {
        health.register();

        assertThat(health.getStatus()).isEqualTo("UP");
    }

    @Test
    void getDetailsIsNotNull() {
        health.register();

        assertThat(health.getDetails()).isNotNull();
        assertThat(health.getDetails()).contains("vosk");
    }

    @Test
    void getLastCheckEpochMsIsPositive() {
        health.register();

        assertThat(health.getLastCheckEpochMs()).isPositive();
    }

    @Test
    void getUptimeMsIsPositive() {
        health.register();

        assertThat(health.getUptimeMs()).isPositive();
    }

    @Test
    void deregistersCleanly() throws Exception {
        health.register();
        health.deregister();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(OBJECT_NAME);
        assertThat(server.isRegistered(name)).isFalse();
    }

    @Test
    void deregisterWithoutRegisterDoesNotThrow() {
        // objectName is null if register() was never called
        health.deregister();
        // no exception thrown
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
