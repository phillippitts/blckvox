package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Constraints;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.PropertyType;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Tab;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable registry of all 59 user-editable configuration properties.
 *
 * <p>Entries are split into factory methods per section to comply with Checkstyle
 * MethodLength limits. Hidden properties (model paths, binary paths, list types,
 * internal-only) are intentionally excluded.
 */
@Component
public class PropertyMetadataRegistry {

    private final List<PropertyMetadata> all;
    private final Map<String, PropertyMetadata> byKey;

    public PropertyMetadataRegistry() {
        List<PropertyMetadata> entries = new ArrayList<>();
        entries.addAll(createHotkeyEntries());
        entries.addAll(createEngineEntries());
        entries.addAll(createAudioEntries());
        entries.addAll(createTypingBasicEntries());
        entries.addAll(createDisplayEntries());
        entries.addAll(createAudioCaptureEntries());
        entries.addAll(createAudioValidationEntries());
        entries.addAll(createConcurrencyEntries());
        entries.addAll(createReconciliationEntries());
        entries.addAll(createWatchdogEntries());
        entries.addAll(createThreadPoolSttEntries());
        entries.addAll(createThreadPoolEventEntries());
        entries.addAll(createTypingTuningEntries());
        entries.addAll(createWhisperTuningEntries());

        this.all = Collections.unmodifiableList(entries);

        Map<String, PropertyMetadata> map = new LinkedHashMap<>();
        for (PropertyMetadata entry : entries) {
            map.put(entry.key(), entry);
        }
        this.byKey = Collections.unmodifiableMap(map);
    }

    public List<PropertyMetadata> getAll() {
        return all;
    }

    public Optional<PropertyMetadata> findByKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public List<PropertyMetadata> findByTab(Tab tab) {
        return all.stream()
                .filter(m -> m.tab() == tab)
                .toList();
    }

    public List<PropertyMetadata> findBySection(String section) {
        return all.stream()
                .filter(m -> m.section().equals(section))
                .toList();
    }

    // --- Basic tab: Hotkey (4 entries) ---
    private static List<PropertyMetadata> createHotkeyEntries() {
        return List.of(
                new PropertyMetadata("hotkey.type", "Trigger Type",
                        "How the hotkey is activated",
                        PropertyType.ENUM, Tab.BASIC, "Hotkey", "double-tap",
                        Constraints.enumValues(List.of("single-key", "double-tap", "modifier-combo"))),
                new PropertyMetadata("hotkey.key", "Key",
                        "Primary key code name (e.g. RIGHT_META, F13)",
                        PropertyType.STRING, Tab.BASIC, "Hotkey", "RIGHT_META",
                        Constraints.requireNotBlank()),
                new PropertyMetadata("hotkey.threshold-ms", "Threshold",
                        "How fast to double-tap (100-1000ms)",
                        PropertyType.INT, Tab.BASIC, "Hotkey", "300",
                        Constraints.intRange(100, 1000)),
                new PropertyMetadata("hotkey.toggle-mode", "Toggle Mode",
                        "If enabled, first press starts recording, second press stops",
                        PropertyType.BOOLEAN, Tab.BASIC, "Hotkey", "true",
                        Constraints.NONE)
        );
    }

    // --- Basic tab: Engine (2 entries) ---
    private static List<PropertyMetadata> createEngineEntries() {
        return List.of(
                new PropertyMetadata("stt.orchestration.primary-engine", "Primary Engine",
                        "Which STT engine to use as primary",
                        PropertyType.ENUM, Tab.BASIC, "Engine", "vosk",
                        Constraints.enumValues(List.of("vosk", "whisper"))),
                new PropertyMetadata("stt.reconciliation.enabled", "Dual-Engine",
                        "Run both engines and reconcile results",
                        PropertyType.BOOLEAN, Tab.BASIC, "Engine", "true",
                        Constraints.NONE)
        );
    }

    // --- Basic tab: Audio (2 entries) ---
    private static List<PropertyMetadata> createAudioEntries() {
        return List.of(
                new PropertyMetadata("stt.orchestration.silence-threshold", "Silence Threshold",
                        "RMS amplitude threshold for silence detection (0-32767)",
                        PropertyType.INT, Tab.BASIC, "Audio", "200",
                        Constraints.intRange(0, 32767)),
                new PropertyMetadata("stt.orchestration.silence-gap-ms", "Silence Gap",
                        "Insert paragraph break when silence exceeds this (ms). 0 to disable.",
                        PropertyType.INT, Tab.BASIC, "Audio", "1000",
                        Constraints.intRange(0, 60000))
        );
    }

    // --- Basic tab: Typing (5 entries) ---
    private static List<PropertyMetadata> createTypingBasicEntries() {
        return List.of(
                new PropertyMetadata("typing.restore-clipboard", "Restore Clipboard",
                        "Restore prior clipboard contents after paste",
                        PropertyType.BOOLEAN, Tab.BASIC, "Typing", "true",
                        Constraints.NONE),
                new PropertyMetadata("typing.clipboard-only-fallback", "Clipboard Only",
                        "Only place text on clipboard, do not send paste shortcut",
                        PropertyType.BOOLEAN, Tab.BASIC, "Typing", "false",
                        Constraints.NONE),
                new PropertyMetadata("typing.normalize-newlines", "Normalize Newlines",
                        "Newline normalization mode before paste",
                        PropertyType.ENUM, Tab.BASIC, "Typing", "LF",
                        Constraints.enumValues(List.of("LF", "CRLF", "NONE"))),
                new PropertyMetadata("typing.trim-trailing-newline", "Trim Trailing Newline",
                        "Remove trailing newline at end of transcription",
                        PropertyType.BOOLEAN, Tab.BASIC, "Typing", "true",
                        Constraints.NONE),
                new PropertyMetadata("typing.enable-robot", "Enable Robot",
                        "Enable Robot-based typing (Tier 1). If disabled, uses clipboard tier.",
                        PropertyType.BOOLEAN, Tab.BASIC, "Typing", "true",
                        Constraints.NONE)
        );
    }

    // --- Basic tab: Display (1 entry) ---
    private static List<PropertyMetadata> createDisplayEntries() {
        return List.of(
                new PropertyMetadata("live-caption.enabled", "Live Caption",
                        "Show live caption overlay during recording",
                        PropertyType.BOOLEAN, Tab.BASIC, "Display", "true",
                        Constraints.NONE)
        );
    }

    // --- Advanced tab: Audio Capture (4 entries) ---
    private static List<PropertyMetadata> createAudioCaptureEntries() {
        return List.of(
                new PropertyMetadata("audio.capture.chunk-millis", "Chunk Size",
                        "Read chunk size from microphone in milliseconds",
                        PropertyType.INT, Tab.ADVANCED, "Audio Capture", "40",
                        Constraints.intRange(10, 200)),
                new PropertyMetadata("audio.capture.device-name", "Device Name",
                        "Audio input device name hint (blank for system default)",
                        PropertyType.STRING, Tab.ADVANCED, "Audio Capture", "",
                        Constraints.NONE),
                new PropertyMetadata("audio.capture.max-duration-ms", "Max Duration",
                        "Maximum capture duration in milliseconds (hard stop)",
                        PropertyType.INT, Tab.ADVANCED, "Audio Capture", "600000",
                        Constraints.intRange(100, 600000)),
                new PropertyMetadata("stt.orchestration.max-recording-duration-seconds",
                        "Max Recording Duration",
                        "Auto-cancel stale recordings after this many seconds. 0 to disable.",
                        PropertyType.INT, Tab.ADVANCED, "Audio Capture", "120",
                        Constraints.intRange(0, 3600))
        );
    }

    // --- Advanced tab: Audio Validation (3 entries) ---
    private static List<PropertyMetadata> createAudioValidationEntries() {
        return List.of(
                new PropertyMetadata("audio.validation.min-duration-ms", "Min Duration",
                        "Minimum audio duration in milliseconds for a valid clip",
                        PropertyType.INT, Tab.ADVANCED, "Audio Validation", "250",
                        Constraints.intRange(1, 60000)),
                new PropertyMetadata("audio.validation.max-duration-ms", "Max Duration",
                        "Maximum audio duration in milliseconds for a valid clip",
                        PropertyType.INT, Tab.ADVANCED, "Audio Validation", "300000",
                        Constraints.intRange(1000, 600000)),
                new PropertyMetadata("audio.validation.max-file-size-bytes", "Max File Size",
                        "Maximum audio file size in bytes",
                        PropertyType.INT, Tab.ADVANCED, "Audio Validation", "104857600",
                        Constraints.intRange(1048576, Integer.MAX_VALUE))
        );
    }

    // --- Advanced tab: Concurrency (7 entries) ---
    private static List<PropertyMetadata> createConcurrencyEntries() {
        return List.of(
                new PropertyMetadata("stt.concurrency.vosk-max", "Vosk Max",
                        "Maximum parallel Vosk transcriptions",
                        PropertyType.INT, Tab.ADVANCED, "Concurrency", "4",
                        Constraints.intRange(1, 32)),
                new PropertyMetadata("stt.concurrency.whisper-max", "Whisper Max",
                        "Maximum parallel Whisper transcriptions",
                        PropertyType.INT, Tab.ADVANCED, "Concurrency", "2",
                        Constraints.intRange(1, 16)),
                new PropertyMetadata("stt.concurrency.acquire-timeout-ms", "Acquire Timeout",
                        "Semaphore wait timeout in milliseconds",
                        PropertyType.INT, Tab.ADVANCED, "Concurrency", "1000",
                        Constraints.intRange(0, 60000)),
                new PropertyMetadata("stt.concurrency.dynamic-scaling-enabled", "Dynamic Scaling",
                        "Enable dynamic concurrency scaling based on system resources",
                        PropertyType.BOOLEAN, Tab.ADVANCED, "Concurrency", "false",
                        Constraints.NONE),
                new PropertyMetadata("stt.concurrency.cpu-threshold-high", "CPU Threshold",
                        "CPU usage above this triggers permit reduction (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Concurrency", "0.80",
                        Constraints.doubleRange(0.0, 1.0)),
                new PropertyMetadata("stt.concurrency.memory-threshold-high", "Memory Threshold",
                        "Memory usage above this triggers permit reduction (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Concurrency", "0.85",
                        Constraints.doubleRange(0.0, 1.0)),
                new PropertyMetadata("stt.concurrency.scaling-interval-ms", "Scaling Interval",
                        "How often to re-evaluate concurrency limits (ms)",
                        PropertyType.LONG, Tab.ADVANCED, "Concurrency", "5000",
                        Constraints.longRange(1000, 60000))
        );
    }

    // --- Advanced tab: Reconciliation (3 entries) ---
    private static List<PropertyMetadata> createReconciliationEntries() {
        return List.of(
                new PropertyMetadata("stt.reconciliation.strategy", "Strategy",
                        "Reconciliation strategy when dual-engine is enabled",
                        PropertyType.ENUM, Tab.ADVANCED, "Reconciliation", "overlap",
                        Constraints.enumValues(List.of("simple", "confidence", "overlap"))),
                new PropertyMetadata("stt.reconciliation.overlap-threshold", "Overlap Threshold",
                        "Word-overlap (Jaccard) threshold for reconciliation (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Reconciliation", "0.6",
                        Constraints.doubleRange(0.0, 1.0)),
                new PropertyMetadata("stt.reconciliation.confidence-threshold",
                        "Confidence Threshold",
                        "Below this, run Whisper too and reconcile (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Reconciliation", "0.7",
                        Constraints.doubleRange(0.0, 1.0))
        );
    }

    // --- Advanced tab: Watchdog (12 entries) ---
    private static List<PropertyMetadata> createWatchdogEntries() {
        return List.of(
                new PropertyMetadata("stt.watchdog.enabled", "Enabled",
                        "Enable engine health monitoring and auto-restart",
                        PropertyType.BOOLEAN, Tab.ADVANCED, "Watchdog", "true",
                        Constraints.NONE),
                new PropertyMetadata("stt.watchdog.window-minutes", "Window",
                        "Sliding window size for restart budget (minutes)",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "60",
                        Constraints.intRange(1, 1440)),
                new PropertyMetadata("stt.watchdog.max-restarts-per-window", "Max Restarts",
                        "Maximum restarts per engine within the window",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "3",
                        Constraints.intRange(1, 100)),
                new PropertyMetadata("stt.watchdog.cooldown-minutes", "Cooldown",
                        "Minutes to wait after disabling before re-enable attempt",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "10",
                        Constraints.intRange(1, 1440)),
                new PropertyMetadata("stt.watchdog.health-summary-interval-millis",
                        "Health Summary Interval",
                        "Health summary log interval in milliseconds",
                        PropertyType.LONG, Tab.ADVANCED, "Watchdog", "60000",
                        Constraints.longRange(1000, 3600000)),
                new PropertyMetadata("stt.watchdog.confidence-blacklist-threshold",
                        "Blacklist Threshold",
                        "Average confidence below this triggers engine blacklisting (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Watchdog", "0.3",
                        Constraints.doubleRange(0.0, 1.0)),
                new PropertyMetadata("stt.watchdog.confidence-window-size",
                        "Confidence Window Size",
                        "Number of recent confidence scores to average for blacklisting",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "10",
                        Constraints.intRange(1, 1000)),
                new PropertyMetadata("stt.watchdog.confidence-min-samples",
                        "Min Samples",
                        "Minimum samples required before evaluating confidence trend",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "5",
                        Constraints.intRange(1, 1000)),
                new PropertyMetadata("stt.watchdog.backoff-base-delay-ms",
                        "Backoff Base Delay",
                        "Base delay in ms for exponential backoff between restarts",
                        PropertyType.LONG, Tab.ADVANCED, "Watchdog", "1000",
                        Constraints.longRange(0, 300000)),
                new PropertyMetadata("stt.watchdog.backoff-multiplier",
                        "Backoff Multiplier",
                        "Multiplier for exponential backoff (delay = base * multiplier^attempts)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Watchdog", "2.0",
                        Constraints.doubleRange(1.0, 10.0)),
                new PropertyMetadata("stt.watchdog.backoff-max-delay-ms",
                        "Backoff Max Delay",
                        "Maximum backoff delay in milliseconds (cap)",
                        PropertyType.LONG, Tab.ADVANCED, "Watchdog", "60000",
                        Constraints.longRange(1000, 3600000)),
                new PropertyMetadata("stt.watchdog.confidence-grace-transcriptions",
                        "Grace Transcriptions",
                        "Transcriptions to skip confidence tracking after engine restart",
                        PropertyType.INT, Tab.ADVANCED, "Watchdog", "5",
                        Constraints.intRange(0, 100))
        );
    }

    // --- Advanced tab: Thread Pools STT (4 entries) ---
    private static List<PropertyMetadata> createThreadPoolSttEntries() {
        return List.of(
                new PropertyMetadata("threadpool.stt.core-pool-size", "Core Pool Size",
                        "Core thread count for STT executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (STT)", "2",
                        Constraints.intRange(1, 64)),
                new PropertyMetadata("threadpool.stt.max-pool-size", "Max Pool Size",
                        "Maximum thread count for STT executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (STT)", "4",
                        Constraints.intRange(1, 128)),
                new PropertyMetadata("threadpool.stt.queue-capacity", "Queue Capacity",
                        "Task queue capacity for STT executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (STT)", "10",
                        Constraints.intRange(1, 10000)),
                new PropertyMetadata("threadpool.stt.keep-alive-seconds", "Keep Alive",
                        "Idle thread keep-alive time in seconds",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (STT)", "60",
                        Constraints.intRange(0, 3600))
        );
    }

    // --- Advanced tab: Thread Pools Event (4 entries) ---
    private static List<PropertyMetadata> createThreadPoolEventEntries() {
        return List.of(
                new PropertyMetadata("threadpool.event.core-pool-size", "Core Pool Size",
                        "Core thread count for event executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (Event)", "2",
                        Constraints.intRange(1, 64)),
                new PropertyMetadata("threadpool.event.max-pool-size", "Max Pool Size",
                        "Maximum thread count for event executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (Event)", "4",
                        Constraints.intRange(1, 128)),
                new PropertyMetadata("threadpool.event.queue-capacity", "Queue Capacity",
                        "Task queue capacity for event executor pool",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (Event)", "10",
                        Constraints.intRange(1, 10000)),
                new PropertyMetadata("threadpool.event.keep-alive-seconds", "Keep Alive",
                        "Idle thread keep-alive time in seconds",
                        PropertyType.INT, Tab.ADVANCED, "Thread Pools (Event)", "60",
                        Constraints.intRange(0, 3600))
        );
    }

    // --- Advanced tab: Typing Tuning (5 entries) ---
    private static List<PropertyMetadata> createTypingTuningEntries() {
        return List.of(
                new PropertyMetadata("typing.chunk-size", "Chunk Size",
                        "Characters per paste chunk",
                        PropertyType.INT, Tab.ADVANCED, "Typing Tuning", "800",
                        Constraints.intRange(100, 2000)),
                new PropertyMetadata("typing.inter-chunk-delay-ms", "Inter-Chunk Delay",
                        "Delay between paste chunks in milliseconds",
                        PropertyType.INT, Tab.ADVANCED, "Typing Tuning", "30",
                        Constraints.intRange(0, 500)),
                new PropertyMetadata("typing.focus-delay-ms", "Focus Delay",
                        "Delay before pasting to allow window focus (ms)",
                        PropertyType.INT, Tab.ADVANCED, "Typing Tuning", "100",
                        Constraints.intRange(0, 1000)),
                new PropertyMetadata("typing.paste-shortcut", "Paste Shortcut",
                        "Override paste shortcut",
                        PropertyType.ENUM, Tab.ADVANCED, "Typing Tuning", "os-default",
                        Constraints.enumValues(List.of("os-default", "META+V", "CONTROL+V"))),
                new PropertyMetadata("typing.clipboard-restore-delay-ms",
                        "Clipboard Restore Delay",
                        "Delay before restoring clipboard after paste (ms)",
                        PropertyType.INT, Tab.ADVANCED, "Typing Tuning", "200",
                        Constraints.intRange(50, 2000))
        );
    }

    // --- Advanced tab: Whisper Tuning (3 entries) ---
    private static List<PropertyMetadata> createWhisperTuningEntries() {
        return List.of(
                new PropertyMetadata("stt.whisper.timeout-seconds", "Timeout",
                        "Maximum time to wait for Whisper transcription (seconds)",
                        PropertyType.INT, Tab.ADVANCED, "Whisper Tuning", "120",
                        Constraints.intRange(1, 600)),
                new PropertyMetadata("stt.whisper.threads", "Threads",
                        "Number of CPU threads for Whisper transcription",
                        PropertyType.INT, Tab.ADVANCED, "Whisper Tuning", "4",
                        Constraints.intRange(1, 32)),
                new PropertyMetadata("stt.whisper.text-mode-confidence",
                        "Text Mode Confidence",
                        "Default confidence score when Whisper runs in text mode (0.0-1.0)",
                        PropertyType.DOUBLE, Tab.ADVANCED, "Whisper Tuning", "0.85",
                        Constraints.doubleRange(0.0, 1.0))
        );
    }
}
