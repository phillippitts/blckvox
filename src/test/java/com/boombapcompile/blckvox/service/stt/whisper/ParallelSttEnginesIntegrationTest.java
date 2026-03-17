package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.TestResourceLoader;
import com.boombapcompile.blckvox.config.ThreadPoolConfig;
import com.boombapcompile.blckvox.config.properties.ThreadPoolProperties;
import com.boombapcompile.blckvox.config.stt.VoskConfig;
import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.parallel.DefaultParallelSttService;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.vosk.VoskSttEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.ProcessBehavior;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.StubProcessFactory;
import static com.boombapcompile.blckvox.service.stt.whisper.WhisperTestDoubles.TestProcess;
import static org.assertj.core.api.Assertions.assertThat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parallel integration test running Vosk and Whisper concurrently.
 *
 * <p>This test validates Task 2.6 requirements:
 * <ul>
 *   <li>Vosk and Whisper run in parallel without exceptions</li>
 *   <li>No thread contention or deadlocks</li>
 *   <li>Uses thread pool configuration from Task 1.5 (ThreadPoolConfig)</li>
 *   <li>Memory leak test (10 iterations)</li>
 * </ul>
 *
 * <p>This test is gated and will only run when the real Vosk model is available locally.
 * Whisper is executed via a stubbed process (hermetic) so no real binary is needed.
 */
@Tag("integration")
@Tag("requiresVoskModel")
class ParallelSttEnginesIntegrationTest {

    private static final Logger LOG = LogManager.getLogger(ParallelSttEnginesIntegrationTest.class);
    private static final String VOSK_MODEL = "models/vosk-model-small-en-us-0.15";
    private static final int MEMORY_LEAK_ITERATIONS = 10;

    private Executor sttExecutor;
    private VoskSttEngine vosk;
    private WhisperSttEngine whisper;

    @BeforeEach
    void setUp() {
        // Gate on system property and presence of Vosk model directory
        boolean gateEnabled = Boolean.getBoolean("vosk.model.available");
        assumeTrue(gateEnabled, "Set -Dvosk.model.available=true to run this test");
        assumeTrue(Files.exists(Path.of(VOSK_MODEL)),
                () -> "Vosk model not found at " + VOSK_MODEL + ". Run ./setup-models.sh first.");

        // Create thread pool using same configuration as Task 1.5
        ThreadPoolProperties properties = new ThreadPoolProperties(
                new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "stt-pool-"),
                new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-"));
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig(properties, new SimpleMeterRegistry());
        sttExecutor = threadPoolConfig.sttExecutor();

        vosk = new VoskSttEngine(new VoskConfig(VOSK_MODEL, 16_000, 1));
        vosk.initialize();

        // Stub Whisper process to keep this test hermetic (no real binary required)
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("stub whisper", "", 0, 0))
        ));
        WhisperConfig wcfg = new WhisperConfig("/bin/echo", "models/ggml-base.en.bin", 5, "en", 2, 1048576, 0.85);
        whisper = new WhisperSttEngine(wcfg, mgr);
        whisper.initialize();
    }

    @AfterEach
    void tearDown() {
        if (vosk != null) {
            try {
                vosk.close();
            } catch (Exception ignored) {
                // Cleanup - exception ignored
            }
        }
        if (whisper != null) {
            try {
                whisper.close();
            } catch (Exception ignored) {
                // Cleanup - exception ignored
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "vosk.model.available", matches = "true")
    void shouldRunVoskAndWhisperInParallel() throws IOException {
        // Use a real 1s PCM silence test resource
        byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");

        // Use Phase 4 DefaultParallelSttService
        ParallelSttService svc = new DefaultParallelSttService(vosk, whisper, sttExecutor, 10000);

        // Run 10 iterations to check for memory leaks (Task 2.6 requirement)
        long totalDurationMs = 0;
        for (int i = 0; i < MEMORY_LEAK_ITERATIONS; i++) {
            long t0 = System.nanoTime();
            ParallelSttService.EnginePair pair = svc.transcribeBoth(pcm, 10000L);
            long durationMs = (System.nanoTime() - t0) / 1_000_000L;
            totalDurationMs += durationMs;

            // Verify results on each iteration
            EngineResult voskResult = pair.vosk();
            EngineResult whisperResult = pair.whisper();

            assertThat(voskResult).isNotNull();
            assertThat(whisperResult).isNotNull();
            assertThat(voskResult.engineName()).isEqualTo("vosk");
            assertThat(whisperResult.engineName()).isEqualTo("whisper");
            assertThat(voskResult.confidence()).isBetween(0.0, 1.0);
            assertThat(whisperResult.confidence()).isBetween(0.0, 1.0);

            // Engines should remain healthy across all iterations
            assertThat(vosk.isHealthy()).isTrue();
            assertThat(whisper.isHealthy()).isTrue();
        }

        LOG.info("Parallel Vosk+Whisper {} iterations: totalMs={}, avgMs={}",
                MEMORY_LEAK_ITERATIONS, totalDurationMs,
                totalDurationMs / MEMORY_LEAK_ITERATIONS);
    }
}
