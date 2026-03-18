package com.boombapcompile.blckvox.service.stt.parallel;

import com.boombapcompile.blckvox.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelSttServiceTest {

    @Test
    void defaultTranscribeVoskOnlyThrowsUnsupported() {
        ParallelSttService svc = (pcm, timeoutMs) -> null;
        assertThatThrownBy(() -> svc.transcribeVoskOnly(new byte[100], 1000))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultTranscribeWhisperOnlyThrowsUnsupported() {
        ParallelSttService svc = (pcm, timeoutMs) -> null;
        EngineResult precomputed = new EngineResult("text", 0.9, java.util.List.of("text"), 100, "vosk", null);
        assertThatThrownBy(() -> svc.transcribeWhisperOnly(new byte[100], 1000, precomputed))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
