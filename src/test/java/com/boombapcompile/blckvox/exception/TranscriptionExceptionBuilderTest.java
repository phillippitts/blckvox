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

    // --- Mutation-killing boundary tests ---

    @Test
    void buildWithOnlyExitCodeHasParens() {
        // hasDetails = true (exitCode != null) → message gets parens
        // Kills L147 hasDetails flag or L148 early return
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .exitCode(42)
                .build();
        // Format: "fail (exitCode=42) (engine: unknown)"
        assertThat(ex.getMessage()).contains("(exitCode=42)");
        assertThat(ex.getMessage()).startsWith("fail ");
    }

    @Test
    void buildWithAllThreeFieldsVerifyCommas() {
        // exitCode + durationMs + metadata → commas between each
        // Kills comma separator on L161, L169
        TranscriptionException ex = TranscriptionExceptionBuilder.create("error")
                .exitCode(1)
                .durationMs(500)
                .metadata("key", "val")
                .build();
        String msg = ex.getMessage();
        // Should contain: "error (exitCode=1, durationMs=500, key=val) (engine: unknown)"
        assertThat(msg).contains("exitCode=1, durationMs=500");
        assertThat(msg).contains("durationMs=500, key=val");
    }

    @Test
    void buildWithOnlyDurationMsHasParens() {
        // Only durationMs set → hasDetails true, first detail has no comma
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .durationMs(999)
                .build();
        assertThat(ex.getMessage()).contains("(durationMs=999)");
    }

    @Test
    void buildWithOnlyMetadataHasParens() {
        // Only metadata → hasDetails true
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .metadata("foo", "bar")
                .build();
        assertThat(ex.getMessage()).contains("(foo=bar)");
    }

    @Test
    void buildWithExitCodeAndMetadataNoCommaBeforeExitCode() {
        // exitCode + metadata (no durationMs) → comma only between exitCode and metadata
        TranscriptionException ex = TranscriptionExceptionBuilder.create("fail")
                .exitCode(5)
                .metadata("path", "/tmp")
                .build();
        String msg = ex.getMessage();
        assertThat(msg).contains("exitCode=5, path=/tmp");
    }
}
