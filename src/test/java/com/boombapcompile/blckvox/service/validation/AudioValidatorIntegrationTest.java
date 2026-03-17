package com.boombapcompile.blckvox.service.validation;

import com.boombapcompile.blckvox.exception.InvalidAudioException;
import com.boombapcompile.blckvox.TestResourceLoader;
import com.boombapcompile.blckvox.config.IntegrationTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test using real test resources under src/test/resources/audio.
 * Ensures AudioValidator accepts a valid 1-second PCM clip and rejects too-short clips.
 *
 * <p>Audio validation properties are provided by the {@code @Primary} bean in
 * {@link IntegrationTestConfiguration} (min=100ms, max=300000ms). We do not use
 * {@code @TestPropertySource} because {@link AudioValidationProperties} is a Java record
 * (immutable, no setters), and {@code @TestPropertySource} attempts setter-based binding
 * which fails for records.
 */
@Tag("integration")
@Import(IntegrationTestConfiguration.class)
@SpringBootTest(properties = {
        "stt.validation.enabled=false" // avoid requiring real models/binaries in CI
})
class AudioValidatorIntegrationTest {

    @Autowired
    private AudioValidator validator;

    @Test
    void shouldAcceptOneSecondPcmSilence() throws IOException {
        byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");
        assertThatCode(() -> validator.validate(pcm))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTooShortPcm() {
        byte[] tiny = new byte[100];
        assertThatThrownBy(() -> validator.validate(tiny))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Audio too short");
    }
}
