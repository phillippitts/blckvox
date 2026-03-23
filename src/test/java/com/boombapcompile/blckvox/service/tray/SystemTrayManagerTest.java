package com.boombapcompile.blckvox.service.tray;

import com.boombapcompile.blckvox.service.audio.capture.BufferOverflowEvent;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.RecordingService;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import com.boombapcompile.blckvox.service.settings.ConfigurationService;
import org.junit.jupiter.api.Test;
import com.boombapcompile.blckvox.service.livecaption.LiveCaptionManager;
import org.springframework.context.ApplicationContext;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class SystemTrayManagerTest {

    @Test
    void createIconReturnsNonNullImage() {
        var img = SystemTrayManager.createIcon(Color.GREEN);
        assertThat(img).isNotNull();
        assertThat(img).isInstanceOf(BufferedImage.class);
        BufferedImage bi = (BufferedImage) img;
        assertThat(bi.getWidth()).isEqualTo(32);
        assertThat(bi.getHeight()).isEqualTo(32);
    }

    @Test
    void createIconDrawsCircleShape() {
        BufferedImage img = (BufferedImage) SystemTrayManager.createIcon(Color.RED);
        // Center pixel should be opaque (part of the filled circle)
        int centerArgb = img.getRGB(16, 16);
        assertThat((centerArgb >> 24) & 0xFF).as("center pixel alpha").isEqualTo(255);
        // Corner pixel (0,0) should be transparent (outside the circle)
        int cornerArgb = img.getRGB(0, 0);
        assertThat((cornerArgb >> 24) & 0xFF).as("corner pixel alpha").isEqualTo(0);
    }

    @Test
    void lifecycleTracksRunningState() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void stateChangeEventDoesNotThrowWhenTrayNotInitialized() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        // Should be a no-op, not throw
        mgr.onStateChanged(new ApplicationStateChangedEvent(
                ApplicationState.IDLE, ApplicationState.RECORDING, Instant.now()));
    }

    @Test
    void getPhaseReturnsMaxValue() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        assertThat(mgr.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void bufferOverflowDoesNotThrowWhenTrayNotInitialized() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        // trayIcon is null because start() was never called
        assertThatCode(() -> mgr.onBufferOverflow(
                new BufferOverflowEvent(1024, 65536, Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void stopDoesNotThrowWhenNotRunning() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        // stop() without start() should be safe
        assertThatCode(mgr::stop).doesNotThrowAnyException();
        assertThat(mgr.isRunning()).isFalse();
    }

    @Test
    void startOnHeadlessEnvironmentDoesNotThrow() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        // start() should not throw regardless of whether SystemTray is supported
        assertThatCode(mgr::start).doesNotThrowAnyException();

        // In a headless CI environment where SystemTray.isSupported() is false,
        // start() returns early and isRunning() remains false.
        // On a desktop with tray support, running will be true.
        // Either way, no exception is thrown.
    }

    @Test
    void onStateChangedAllStatesDoNotThrowWithNullTray() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        SystemTrayManager mgr = new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class));

        // trayIcon is null because start() was never called;
        // all state transitions should be no-ops
        for (ApplicationState state : ApplicationState.values()) {
            assertThatCode(() -> mgr.onStateChanged(
                    new ApplicationStateChangedEvent(
                            ApplicationState.IDLE, state, Instant.now())))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void createIconUsesCorrectDimensions() {
        Color[] colors = {Color.GRAY, Color.RED, Color.BLUE};
        for (Color color : colors) {
            BufferedImage img = (BufferedImage) SystemTrayManager.createIcon(color);
            assertThat(img.getWidth()).as("width for %s", color).isEqualTo(32);
            assertThat(img.getHeight()).as("height for %s", color).isEqualTo(32);
        }
    }

    @Test
    void constructorAcceptsEmptyLiveCaptionManager() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);

        assertThatCode(() -> new SystemTrayManager(rs, ctx, Optional.empty(), mock(ConfigurationService.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void constructorAcceptsNonEmptyLiveCaptionManager() {
        RecordingService rs = mock(RecordingService.class);
        ApplicationContext ctx = mock(ApplicationContext.class);
        LiveCaptionManager lcm = mock(LiveCaptionManager.class);

        assertThatCode(() -> new SystemTrayManager(rs, ctx, Optional.of(lcm), mock(ConfigurationService.class)))
                .doesNotThrowAnyException();
    }
}
