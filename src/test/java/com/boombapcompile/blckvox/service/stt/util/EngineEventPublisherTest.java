package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.service.stt.watchdog.EngineFailureEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EngineEventPublisherTest {

    @Test
    void publishesFailureEventWhenPublisherNonNull() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        EngineEventPublisher.publishFailure(publisher, "vosk", "test failure", null, Map.of());

        ArgumentCaptor<EngineFailureEvent> captor = ArgumentCaptor.forClass(EngineFailureEvent.class);
        verify(publisher).publishEvent(captor.capture());
        EngineFailureEvent event = captor.getValue();
        assertThat(event.engine()).isEqualTo("vosk");
        assertThat(event.message()).isEqualTo("test failure");
    }

    @Test
    void doesNothingWhenPublisherNull() {
        assertThatCode(() -> EngineEventPublisher.publishFailure(
                null, "vosk", "test failure", null, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void includesCauseAndContext() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        RuntimeException cause = new RuntimeException("root cause");
        Map<String, String> context = Map.of("key", "value");

        EngineEventPublisher.publishFailure(publisher, "whisper", "error", cause, context);

        ArgumentCaptor<EngineFailureEvent> captor = ArgumentCaptor.forClass(EngineFailureEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().cause()).isSameAs(cause);
        assertThat(captor.getValue().context()).containsEntry("key", "value");
    }
}
