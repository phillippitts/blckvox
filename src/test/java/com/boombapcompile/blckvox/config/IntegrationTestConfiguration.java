package com.boombapcompile.blckvox.config;

import com.boombapcompile.blckvox.service.stt.vosk.VoskSttEngine;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog;
import com.boombapcompile.blckvox.service.stt.whisper.WhisperSttEngine;
import com.boombapcompile.blckvox.config.properties.AudioCaptureProperties;
import com.boombapcompile.blckvox.service.audio.capture.AudioCaptureService;
import com.boombapcompile.blckvox.service.audio.capture.JavaSoundAudioCaptureService;
import com.boombapcompile.blckvox.config.properties.AudioValidationProperties;
import com.boombapcompile.blckvox.testutil.FakeAudioCaptureService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing mocked/stubbed beans for integration tests.
 *
 * <p>This configuration avoids hardware dependencies (microphone, audio devices) by providing
 * test doubles for audio-related components. Tests that need real audio capture should not
 * import this configuration.
 *
 * <p><b>Usage:</b> Import this class in integration tests that don't require real audio hardware:
 * <pre>
 * {@literal @}SpringBootTest
 * {@literal @}Import(IntegrationTestConfiguration.class)
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * <p><b>Implementation note:</b> This configuration provides {@code @Primary} beans that should
 * take precedence over production beans. The {@link AudioCaptureService} bean is conditionally
 * created only if no other AudioCaptureService bean exists (preventing conflicts with production
 * {@link JavaSoundAudioCaptureService}).
 * STT engine components are excluded from component scanning to prevent bean conflicts.
 */
@Configuration
@ComponentScan(
        basePackages = "com.boombapcompile.blckvox",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        VoskSttEngine.class,
                        WhisperSttEngine.class
                }
        )
)
public class IntegrationTestConfiguration {

    /**
     * Provides test AudioCaptureProperties with sensible defaults.
     *
     * <p>These properties avoid the need for application.properties configuration in tests.
     *
     * @return test audio capture properties
     */
    @Bean
    @Primary
    public AudioCaptureProperties testAudioCaptureProperties() {
        return new AudioCaptureProperties(
                40,      // chunkMillis
                60000,   // maxDurationMs (60 seconds)
                null     // deviceName (use default)
        );
    }

    /**
     * Provides test AudioValidationProperties with permissive defaults.
     *
     * <p>These properties allow test audio clips of varying lengths without validation errors.
     *
     * @return test audio validation properties
     */
    @Bean
    @Primary
    public AudioValidationProperties testAudioValidationProperties() {
        return new AudioValidationProperties(100, 300000, 100 * 1024 * 1024);
    }

    /**
     * Provides fake AudioCaptureService for tests that don't need real microphone access.
     *
     * <p>This bean is marked {@code @Primary} to override the real {@code JavaSoundAudioCaptureService}
     * in integration test contexts, avoiding microphone permission and hardware availability issues.
     *
     * @return fake audio capture service
     */
    @Bean
    @Primary
    public AudioCaptureService testAudioCaptureService() {
        return new FakeAudioCaptureService();
    }

    /**
     * Provides mock SttEngineWatchdog bean for tests.
     *
     * <p>Returns a Mockito mock that avoids actual engine monitoring and restart logic.
     * All engines are stubbed as enabled so that {@code EngineSelectionStrategy} can
     * select them during integration tests. Without this, the mock returns {@code false}
     * by default, causing "Both engines unavailable" failures.
     *
     * @return mock STT engine watchdog for tests
     */
    @Bean
    public SttEngineWatchdog sttEngineWatchdog() {
        SttEngineWatchdog mock = org.mockito.Mockito.mock(SttEngineWatchdog.class);
        org.mockito.Mockito.when(mock.isEngineEnabled(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        return mock;
    }

    /**
     * Provides mock VoskSttEngine bean for tests.
     *
     * <p>Returns a Mockito mock that avoids model loading and initialization.
     * Stubbed to return "vosk" for getEngineName() to satisfy SttEngineWatchdog.
     * Tests that need transcription behavior should configure this mock.
     * Bean name matches production for autowiring by name.
     *
     * @return mock vosk STT engine for tests
     */
    @Bean("voskSttEngine")
    public VoskSttEngine voskSttEngine() {
        VoskSttEngine mock =
                org.mockito.Mockito.mock(VoskSttEngine.class);
        org.mockito.Mockito.when(mock.getEngineName()).thenReturn("vosk");
        return mock;
    }

    /**
     * Provides mock WhisperSttEngine bean for tests.
     *
     * <p>Returns a Mockito mock that avoids binary execution and initialization.
     * Stubbed to return "whisper" for getEngineName() to satisfy SttEngineWatchdog.
     * Tests that need transcription behavior should configure this mock.
     * Bean name matches production for autowiring by name.
     *
     * @return mock whisper STT engine for tests
     */
    @Bean("whisperSttEngine")
    public WhisperSttEngine whisperSttEngine() {
        WhisperSttEngine mock =
                org.mockito.Mockito.mock(WhisperSttEngine.class);
        org.mockito.Mockito.when(mock.getEngineName()).thenReturn("whisper");
        return mock;
    }
}
