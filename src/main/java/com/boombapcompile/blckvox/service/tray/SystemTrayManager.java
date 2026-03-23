package com.boombapcompile.blckvox.service.tray;

import com.boombapcompile.blckvox.service.audio.capture.BufferOverflowEvent;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.RecordingService;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.EngineHealthChangedEvent;
import com.boombapcompile.blckvox.service.stt.watchdog.SttEngineWatchdog.EngineState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.boombapcompile.blckvox.service.livecaption.LiveCaptionManager;
import com.boombapcompile.blckvox.service.settings.ConfigurationService;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a macOS system tray icon with Start/Stop Recording and Quit controls.
 *
 * <p>Reacts to {@link ApplicationStateChangedEvent} to update menu text, tooltip,
 * and icon color (gray=idle, red=recording, gray=transcribing).
 *
 * @since 1.2
 */
@Service
@ConditionalOnProperty(name = "tray.enabled", matchIfMissing = true)
public class SystemTrayManager implements SmartLifecycle {

    private static final Logger LOG = LogManager.getLogger(SystemTrayManager.class);
    private static final Color RECORD_RED = new Color(255, 59, 48);
    private static final Color IDLE_GRAY = new Color(140, 140, 140);

    private final RecordingService recordingService;
    private final ApplicationContext applicationContext;
    private final Optional<LiveCaptionManager> liveCaptionManager;
    private final ConfigurationService configurationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile TrayIcon trayIcon;
    private volatile MenuItem statusItem;
    private volatile MenuItem toggleItem;
    private volatile com.boombapcompile.blckvox.service.settings.ConfigurationWindow settingsWindow;

    public SystemTrayManager(RecordingService recordingService,
                             ApplicationContext applicationContext,
                             Optional<LiveCaptionManager> liveCaptionManager,
                             ConfigurationService configurationService) {
        this.recordingService = recordingService;
        this.applicationContext = applicationContext;
        this.liveCaptionManager = liveCaptionManager;
        this.configurationService = configurationService;
    }

    @Override
    public void start() {
        if (!SystemTray.isSupported()) {
            LOG.warn("System tray not supported on this platform");
            return;
        }
        running.set(true);
        SwingUtilities.invokeLater(this::initializeTray);
    }

    private void initializeTray() {
        try {
            PopupMenu popup = new PopupMenu();

            statusItem = new MenuItem("Idle");
            statusItem.setEnabled(false);
            popup.add(statusItem);

            popup.addSeparator();

            toggleItem = new MenuItem("Start Recording");
            toggleItem.addActionListener(e -> onToggleClicked());
            popup.add(toggleItem);

            liveCaptionManager.ifPresent(manager -> {
                popup.addSeparator();
                CheckboxMenuItem captionItem = new CheckboxMenuItem("Live Caption");
                captionItem.setState(manager.isEnabled());
                captionItem.addItemListener(e ->
                        Thread.ofVirtual().start(() -> manager.setEnabled(captionItem.getState())));
                popup.add(captionItem);
            });

            popup.addSeparator();

            if (isJavaFxAvailable()) {
                MenuItem settingsItem = new MenuItem("Settings...");
                settingsItem.addActionListener(e -> onSettingsClicked());
                popup.add(settingsItem);
                popup.addSeparator();
            }

            MenuItem quitItem = new MenuItem("Quit");
            quitItem.addActionListener(e -> onQuitClicked());
            popup.add(quitItem);

            Image icon = createIcon(IDLE_GRAY);
            trayIcon = new TrayIcon(icon, "Blckvox - Idle", popup);
            trayIcon.setImageAutoSize(true);

            SystemTray.getSystemTray().add(trayIcon);
            LOG.info("System tray icon initialized");
        } catch (AWTException e) {
            LOG.error("Failed to add system tray icon", e);
        }
    }

    private void onToggleClicked() {
        Thread.ofVirtual().start(() -> {
            if (recordingService.isRecording()) {
                LOG.info("Stop Recording clicked from system tray menu");
                if (recordingService.stopRecording()) {
                    LOG.info("Recording stopped successfully via tray menu");
                } else {
                    LOG.warn("Stop recording rejected via tray menu (not recording)");
                }
            } else {
                LOG.info("Start Recording clicked from system tray menu");
                if (recordingService.startRecording()) {
                    LOG.info("Recording started successfully via tray menu");
                } else {
                    LOG.warn("Start recording rejected via tray menu (already recording or error)");
                }
            }
        });
    }

    private void onSettingsClicked() {
        try {
            javafx.application.Platform.runLater(() -> {
                if (settingsWindow == null) {
                    settingsWindow = new com.boombapcompile.blckvox.service.settings
                            .ConfigurationWindow(configurationService);
                }
                settingsWindow.show();
            });
        } catch (IllegalStateException e) {
            LOG.warn("Cannot open settings: JavaFX platform not running");
        }
    }

    private static boolean isJavaFxAvailable() {
        try {
            Class.forName("javafx.application.Platform");
            return javafx.application.Platform.isFxApplicationThread()
                    || isJavaFxPlatformRunning();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isJavaFxPlatformRunning() {
        try {
            // If Platform.runLater doesn't throw, FX is running
            javafx.application.Platform.runLater(() -> { });
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void onQuitClicked() {
        LOG.info("Quit requested from system tray");
        Thread.ofVirtual().start(() -> SpringApplication.exit(applicationContext, () -> 0));
    }

    @EventListener
    public void onBufferOverflow(BufferOverflowEvent event) {
        if (trayIcon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (trayIcon == null) {
                return;
            }
            trayIcon.displayMessage("Audio Dropped",
                    "Recording exceeds capacity. Try shorter dictations.",
                    TrayIcon.MessageType.WARNING);
        });
    }

    @EventListener
    public void onEngineHealthChanged(EngineHealthChangedEvent event) {
        if (trayIcon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (trayIcon == null) {
                return;
            }
            EngineState newState = event.currentState();
            String engine = event.engine();
            if (newState == EngineState.DEGRADED || newState == EngineState.DISABLED) {
                trayIcon.displayMessage("Engine " + newState,
                        engine + " is " + newState.name().toLowerCase() + ". "
                                + (newState == EngineState.DISABLED
                                        ? "Watchdog will attempt recovery after cooldown."
                                        : "Performance may be reduced."),
                        newState == EngineState.DISABLED
                                ? TrayIcon.MessageType.ERROR
                                : TrayIcon.MessageType.WARNING);
            }
        });
    }

    @EventListener
    public void onStateChanged(ApplicationStateChangedEvent event) {
        if (trayIcon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (trayIcon == null) {
                return;
            }
            updateTrayForState(event.current());
        });
    }

    private void updateTrayForState(ApplicationState state) {
        if (trayIcon == null) {
            return;
        }
        switch (state) {
            case IDLE -> {
                trayIcon.setImage(createIcon(IDLE_GRAY));
                trayIcon.setToolTip("Blckvox - Idle");
                statusItem.setLabel("Idle");
                toggleItem.setLabel("Start Recording");
                toggleItem.setEnabled(true);
            }
            case RECORDING -> {
                trayIcon.setImage(createIcon(RECORD_RED));
                trayIcon.setToolTip("Blckvox - Recording...");
                statusItem.setLabel("\u25CF Recording...");
                toggleItem.setLabel("Stop Recording");
                toggleItem.setEnabled(true);
            }
            case TRANSCRIBING -> {
                trayIcon.setImage(createIcon(Color.GRAY));
                trayIcon.setToolTip("Blckvox - Transcribing...");
                statusItem.setLabel("\u231B Transcribing...");
                toggleItem.setLabel("Stop Recording");
                toggleItem.setEnabled(false);
            }
        }
    }

    /**
     * Creates a 32x32 anti-aliased filled circle icon with the given color.
     */
    static Image createIcon(Color color) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(2, 2, 28, 28);
        g2.dispose();
        return img;
    }

    @Override
    public void stop() {
        running.set(false);
        if (trayIcon != null && SystemTray.isSupported()) {
            try {
                // Must access tray on EDT to avoid threading violations on macOS
                SwingUtilities.invokeAndWait(() -> {
                    if (trayIcon != null) {
                        SystemTray.getSystemTray().remove(trayIcon);
                        trayIcon = null;
                    }
                });
                LOG.info("System tray icon removed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while removing tray icon");
            } catch (java.lang.reflect.InvocationTargetException e) {
                LOG.warn("Error removing tray icon on EDT: {}", e.getCause().getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // Start last, stop first
    }
}
