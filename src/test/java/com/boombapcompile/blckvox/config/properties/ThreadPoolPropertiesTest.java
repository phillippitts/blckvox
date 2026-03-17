package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ThreadPoolProperties} validation.
 */
class ThreadPoolPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // --- Valid defaults ---

    @Test
    void shouldConstructWithValidDefaults() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
        assertThat(props.stt().corePoolSize()).isEqualTo(4);
        assertThat(props.event().corePoolSize()).isEqualTo(2);
    }

    // --- SttPoolProperties validation ---

    @Test
    void shouldRejectZeroCorePoolSize() {
        var stt = new ThreadPoolProperties.SttPoolProperties(0, 8, 50, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("corePoolSize"));
    }

    @Test
    void shouldRejectMaxPoolSizeLessThanCorePoolSize() {
        assertThatThrownBy(() ->
                new ThreadPoolProperties.SttPoolProperties(8, 4, 50, 60, "stt-pool-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPoolSize")
                .hasMessageContaining("corePoolSize");
    }

    @Test
    void shouldRejectZeroQueueCapacity() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 0, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("queueCapacity"));
    }

    @Test
    void shouldRejectBlankThreadNamePrefix() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "  ");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("threadNamePrefix"));
    }

    @Test
    void shouldAcceptZeroKeepAliveSeconds() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 0, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 0, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectNegativeKeepAliveSeconds() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 50, -1, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("keepAliveSeconds"));
    }

    // --- EventPoolProperties validation ---

    @Test
    void shouldRejectEventPoolMaxLessThanCore() {
        assertThatThrownBy(() ->
                new ThreadPoolProperties.EventPoolProperties(4, 2, 10, 60, "event-pool-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPoolSize")
                .hasMessageContaining("corePoolSize");
    }

    @Test
    void shouldAcceptEqualCoreAndMaxPoolSize() {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 4, 50, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 2, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }

    // --- Parameterized boundary tests ---

    @ParameterizedTest(name = "SttPoolProperties: corePoolSize={0}, maxPoolSize={1}, queueCapacity={2} should be valid")
    @CsvSource({
        "1, 1, 1",     // minimum valid values
        "1, 8, 50",    // min core, normal max
        "4, 4, 1",     // equal core/max, min queue
        "16, 32, 100", // large values
        "1, 1000, 1",  // extreme max pool
    })
    void sttPoolPropertiesBoundaryValid(int core, int max, int queue) {
        var stt = new ThreadPoolProperties.SttPoolProperties(core, max, queue, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest(name = "SttPoolProperties: corePoolSize={0} should be invalid (must be >= 1)")
    @ValueSource(ints = {0, -1, -100})
    void sttPoolPropertiesInvalidCorePoolSize(int core) {
        var stt = new ThreadPoolProperties.SttPoolProperties(core, 8, 50, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest(name = "SttPoolProperties: queueCapacity={0} should be invalid (must be >= 1)")
    @ValueSource(ints = {0, -1})
    void sttPoolPropertiesInvalidQueueCapacity(int queue) {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, queue, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest(name = "EventPoolProperties: core={0}, max={1}, queue={2} valid")
    @CsvSource({
        "1, 1, 1",     // minimum valid values
        "2, 4, 10",    // default values
        "8, 16, 50",   // larger configuration
    })
    void eventPoolPropertiesBoundaryValid(int core, int max, int queue) {
        var stt = new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "stt-pool-");
        var event = new ThreadPoolProperties.EventPoolProperties(core, max, queue, 60, "event-pool-");
        var props = new ThreadPoolProperties(stt, event);

        Set<ConstraintViolation<ThreadPoolProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }
}
