package com.boombapcompile.blckvox.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionExceptionBuilderTest {

    @Test
    void createWithNullMessageThrows() {
        assertThatThrownBy(() -> TranscriptionExceptionBuilder.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithEmptyMessageThrows() {
        assertThatThrownBy(() -> TranscriptionExceptionBuilder.create(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithMessageOnly() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .build();
        assertThat(ex.getMessage()).contains("fail");
        assertThat(ex.getEngineName()).isEqualTo("unknown");
    }

    @Test
    void buildWithEngine() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .engine("whisper")
                .build();
        assertThat(ex.getEngineName()).isEqualTo("whisper");
    }

    @Test
    void buildWithCause() {
        RuntimeException cause = new RuntimeException("root");
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .cause(cause)
                .build();
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void buildWithExitCode() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .exitCode(1)
                .build();
        assertThat(ex.getMessage()).contains("exitCode=1");
    }

    @Test
    void buildWithDurationMs() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .durationMs(1500)
                .build();
        assertThat(ex.getMessage()).contains("durationMs=1500");
    }

    @Test
    void buildWithAllDetails() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .exitCode(2)
                .durationMs(500)
                .metadata("model", "base")
                .build();
        String msg = ex.getMessage();
        assertThat(msg).contains("exitCode=2");
        assertThat(msg).contains("durationMs=500");
        assertThat(msg).contains("model=base");
        // verify order: exitCode before durationMs before metadata
        assertThat(msg.indexOf("exitCode")).isLessThan(msg.indexOf("durationMs"));
        assertThat(msg.indexOf("durationMs")).isLessThan(msg.indexOf("model"));
    }

    @Test
    void metadataNullKeyIgnored() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .metadata(null, "val")
                .build();
        assertThat(ex.getMessage()).isEqualTo("fail (engine: unknown)");
    }

    @Test
    void metadataNullValueIgnored() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .metadata("key", null)
                .build();
        assertThat(ex.getMessage()).isEqualTo("fail (engine: unknown)");
    }

    @Test
    void buildWithoutCauseNoCause() {
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .build();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void chainableApi() {
        TranscriptionExceptionBuilder builder = TranscriptionExceptionBuilder.create("fail");
        assertThat(builder.engine("e")).isSameAs(builder);
        assertThat(builder.cause(new RuntimeException())).isSameAs(builder);
        assertThat(builder.exitCode(1)).isSameAs(builder);
        assertThat(builder.durationMs(100)).isSameAs(builder);
        assertThat(builder.metadata("k", "v")).isSameAs(builder);
    }
}
