# Configuration Reference

Complete reference for all configuration properties in blckvox, organized by audience.

> **Location:** All properties are configured in `src/main/resources/application.properties`

## Audience Tiers

| Tier | Audience | Description |
|------|----------|-------------|
| **User** | End users | Settings you'll change day-to-day (hotkey, audio device, typing behavior) |
| **Operator** | System admins | Tuning and monitoring (thread pools, watchdog, concurrency, metrics) |
| **Developer** | Contributors | Engine internals, reconciliation algorithms, debugging knobs |

> Properties marked with a tier badge indicate the primary audience. All properties are accessible to everyone.

## Table of Contents

### User Settings
- [Hotkey Configuration](#hotkey-configuration)
- [Typing Configuration](#typing-configuration)
- [Live Caption Configuration](#live-caption-configuration)
- [Audio Capture](#audio-capture)

### Operator Settings
- [Audio Validation](#audio-validation)
- [Engine Orchestration](#engine-orchestration)
- [Concurrency Limits](#concurrency-limits)
- [Engine Watchdog](#engine-watchdog)
- [Thread Pool Configuration](#thread-pool-configuration)
- [System Tray Configuration](#system-tray-configuration)

### Developer Settings
- [Vosk Configuration](#vosk-configuration)
- [Whisper Configuration](#whisper-configuration)
- [Reconciliation (Phase 4)](#reconciliation-phase-4)
- [Model Validation](#model-validation)

---

# User Settings

These are the settings you'll most commonly adjust for daily use.

## Hotkey Configuration

`User` — Configures the global hotkey for push-to-talk.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hotkey.type` | String | `double-tap` | Hotkey type. Options: `single-key`, `double-tap`, or `modifier-combo`. |
| `hotkey.key` | String | `RIGHT_META` | Primary key for hotkey. Examples: `RIGHT_META` (Command on macOS), `M`, `SPACE`, `F13`. |
| `hotkey.modifiers` | String | (empty) | Comma-separated modifiers. Required for `modifier-combo`, optional for `single-key` and `double-tap`. Options: `SHIFT`, `CTRL`, `ALT`, `META`, `LEFT_META`, `RIGHT_META`. |
| `hotkey.threshold-ms` | int | `300` | For `double-tap`, this is the maximum time between taps (100-1000ms recommended). For other types, it's the minimum hold duration. |
| `hotkey.toggle-mode` | boolean | `true` | Toggle mode: `true` = click once to start recording, click again to stop and transcribe. `false` = push-to-talk (press to start, release to stop). (Java class default: false) |
| `hotkey.reserved` | String | (empty) | Comma-separated list of OS shortcuts to warn about. Platform-aware validation. |

**Example - Single Key (Right Command on macOS):**
```properties
hotkey.type=single-key
hotkey.key=RIGHT_META
```

**Example - Modifier Combination (Cmd+Shift+M):**
```properties
hotkey.type=modifier-combo
hotkey.key=M
hotkey.modifiers=META,SHIFT
```

**Example - Double Tap (Double-tap D within 300ms):**
```properties
hotkey.type=double-tap
hotkey.key=D
hotkey.threshold-ms=300
```

**Valid Key Names:**
- Letters: `A` - `Z`
- Numbers: `0` - `9`
- Special: `SPACE`, `ENTER`, `TAB`, `ESCAPE`
- Modifiers: `SHIFT`, `CTRL`, `ALT`, `META`, `LEFT_META`, `RIGHT_META`
- See [JNativeHook KeyEvent](https://github.com/kwhat/jnativehook/wiki/Key-Event-Codes) for complete list

---

## Typing Configuration

`User` — Controls text delivery to active application via clipboard/robot.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `typing.chunk-size` | int | `800` | Maximum characters per paste chunk. Large texts are split to avoid clipboard limits. |
| `typing.inter-chunk-delay-ms` | int | `30` | Delay between paste chunks (ms). Gives apps time to process before next chunk. |
| `typing.focus-delay-ms` | int | `100` | Delay after window focus before pasting (ms). Ensures app is ready to receive input. |
| `typing.restore-clipboard` | boolean | `true` | Restore original clipboard contents after pasting. Prevents losing user's clipboard. |
| `typing.clipboard-only-fallback` | boolean | `false` | Use clipboard-only mode (no Robot keystroke simulation). Useful when Accessibility permission unavailable. |
| `typing.normalize-newlines` | String | `LF` | Newline normalization. Options: `LF` (\n), `CRLF` (\r\n), `NONE` (no normalization). |
| `typing.trim-trailing-newline` | boolean | `true` | Remove trailing newline from transcription. Prevents extra blank line after paste. |
| `typing.enable-robot` | boolean | `true` | Enable Java Robot API for keystroke simulation. Requires macOS Accessibility permission. |
| `typing.paste-shortcut` | String | `os-default` | Paste keyboard shortcut. Options: `os-default` (Cmd+V on macOS, Ctrl+V on Windows), or custom like `META+V`. |
| `typing.clipboard-restore-delay-ms` | int | `200` | Delay in milliseconds before restoring clipboard after paste (50-2000ms). |

**Example - Clipboard-Only Mode (No Accessibility Permission):**
```properties
typing.enable-robot=false
typing.clipboard-only-fallback=true
```

---

## Live Caption Configuration

`User` — Controls the real-time overlay window that displays an oscilloscope waveform and streaming Vosk captions during recording.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `live-caption.enabled` | boolean | `true` | Enable the live caption overlay. When `false`, no JavaFX initialization occurs. Zero overhead when disabled. (Java class default: false) |
| `live-caption.window-width` | int | `600` | Width of the overlay window in pixels. |
| `live-caption.window-height` | int | `250` | Height of the overlay window in pixels. |
| `live-caption.window-opacity` | double | `0.85` | Window opacity (0.0 = fully transparent, 1.0 = fully opaque). |

**Runtime Toggle:** When enabled, the "Live Caption" checkbox in the system tray menu allows toggling the overlay on/off without restarting the application.

---

## Audio Capture

`User` — Controls the audio capture service behavior.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `audio.capture.chunk-millis` | int | `40` | Audio buffer chunk size in milliseconds. Smaller values = lower latency but higher CPU usage. |
| `audio.capture.max-duration-ms` | int | `600000` | Maximum recording duration (10 minutes). Hard limit to prevent unbounded capture sessions. |
| `audio.capture.device-name` | String | (system default) | Optional: Specific audio input device name. If not set, uses system default microphone. |

---

# Operator Settings

Settings for production tuning, monitoring, and reliability.

## Audio Validation

`Operator` — Controls audio validation rules for minimum and maximum recording duration.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `audio.validation.min-duration-ms` | int | `250` | Minimum audio duration in milliseconds. Clips shorter than this are rejected to avoid accidental hotkey taps. |
| `audio.validation.max-duration-ms` | int | `300000` | Maximum audio duration in milliseconds (5 minutes). Prevents unbounded memory usage and processing time. |
| `audio.validation.max-file-size-bytes` | int | `104857600` | Maximum file size in bytes for audio payloads (100 MB). Security guard against memory exhaustion. |

---

## Engine Orchestration

`Operator` — Controls which engine is used as primary and how parallel execution works.

> **Note:** Both Vosk and Whisper engines are always loaded as Spring beans. To control behavior, use `stt.orchestration.primary-engine` (which engine handles single-engine requests) and `stt.reconciliation.enabled` (whether to run both engines and reconcile).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.orchestration.primary-engine` | String | `vosk` | Primary engine preference. Options: `vosk` (fast, lower accuracy) or `whisper` (slower, higher accuracy). Falls back to secondary if primary is unhealthy. |
| `stt.parallel.timeout-ms` | int | `120000` | Timeout in milliseconds for parallel dual-engine transcription (reconciliation mode only). |
| `stt.orchestration.silence-gap-ms` | int | `1000` | Silence gap threshold in milliseconds. If silence within audio exceeds this, a paragraph break (newline) is inserted. Set to 0 to disable. |
| `stt.orchestration.silence-threshold` | int | `200` | RMS amplitude threshold for silence detection (0-32767 for 16-bit PCM). Lower = captures quieter speech. |

**Single-Engine Mode (Whisper only):**
```properties
stt.orchestration.primary-engine=whisper
stt.reconciliation.enabled=false
```

---

## Concurrency Limits

`Operator` — Lightweight bulkheads to prevent resource exhaustion from concurrent transcription requests.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.concurrency.vosk-max` | int | `4` | Maximum concurrent Vosk transcriptions. Vosk is fast, so higher concurrency is safe. |
| `stt.concurrency.whisper-max` | int | `2` | Maximum concurrent Whisper transcriptions. Whisper is CPU-intensive, keep this low. |
| `stt.concurrency.acquire-timeout-ms` | int | `1000` | Maximum wait time (ms) to acquire concurrency permit. Prevents indefinite blocking. |
| `stt.concurrency.dynamic-scaling-enabled` | boolean | `false` | Enable dynamic concurrency scaling based on system CPU and memory usage. |

**Tuning Guide:**
- **Low-end CPU (2-4 cores):** `vosk-max=2`, `whisper-max=1`
- **Mid-range CPU (4-8 cores):** `vosk-max=4`, `whisper-max=2` (default)
- **High-end CPU (8+ cores):** `vosk-max=8`, `whisper-max=4`

---

## Engine Watchdog

`Operator` — Auto-restart engines on repeated failures with sliding window rate limiting.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.watchdog.enabled` | boolean | `true` | Enable automatic engine restart on failures. Recommended for production. |
| `stt.watchdog.window-minutes` | int | `60` | Sliding window duration for restart budget (minutes). |
| `stt.watchdog.max-restarts-per-window` | int | `3` | Maximum restarts allowed within the sliding window. Prevents restart loops. |
| `stt.watchdog.cooldown-minutes` | int | `10` | Cooldown period after max restarts exhausted before allowing new restarts. |
| `stt.watchdog.probe-enabled` | boolean | `false` | Enable lightweight health probe for engines. |
| `stt.watchdog.confidence-blacklist-threshold` | double | `0.3` | Average confidence below this threshold triggers engine blacklisting (0.0-1.0). |
| `stt.watchdog.confidence-window-size` | int | `10` | Number of recent confidence scores to average for blacklisting. |
| `stt.watchdog.confidence-min-samples` | int | `5` | Minimum samples required before evaluating confidence trend. |

**How It Works:**
1. Engine fails 3 times within 60 minutes -> watchdog disables it
2. Waits 10 minutes (cooldown)
3. Re-enables engine after cooldown
4. Sliding window resets if no failures occur for 60 minutes

---

## Thread Pool Configuration

`Operator` — Controls thread pool sizing for STT processing and event handling.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `threadpool.stt.core-pool-size` | int | `2` | Core threads for STT engine execution. (Java class default: 4) |
| `threadpool.stt.max-pool-size` | int | `4` | Maximum threads for burst STT capacity. (Java class default: 8) |
| `threadpool.stt.queue-capacity` | int | `10` | Bounded queue size for STT tasks. (Java class default: 50) |
| `threadpool.stt.keep-alive-seconds` | int | `60` | Idle thread timeout before termination. |
| `threadpool.stt.thread-name-prefix` | String | `stt-pool-` | Thread name prefix for debugging. |
| `threadpool.event.core-pool-size` | int | `2` | Core threads for async event handling. |
| `threadpool.event.max-pool-size` | int | `4` | Maximum threads for event processing. |
| `threadpool.event.queue-capacity` | int | `10` | Bounded queue size for event tasks. |
| `threadpool.event.keep-alive-seconds` | int | `60` | Idle thread timeout before termination. |
| `threadpool.event.thread-name-prefix` | String | `event-pool-` | Thread name prefix for debugging. |

**Tuning Guide:**
- **Low-end CPU (2-4 cores):** Default values (stt core=2, max=4)
- **Mid-range CPU (4-8 cores):** `stt.core-pool-size=4`, `stt.max-pool-size=8`
- **High-end CPU (8+ cores):** `stt.core-pool-size=8`, `stt.max-pool-size=16`

---

## System Tray Configuration

`Operator` — Controls the macOS system tray (menu bar) icon.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tray.enabled` | boolean | `true` | Enable the system tray icon. When `false`, no tray icon is shown. |

---

# Developer Settings

Settings for engine internals, reconciliation algorithms, and debugging.

## Vosk Configuration

`Developer` — Configures the Vosk STT engine (fast, JNI-based, offline).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.vosk.model-path` | String | `models/vosk-model-en-us-0.22` | Path to Vosk model directory. Must contain `am/`, `graph/`, `rescore/` subdirectories. |
| `stt.vosk.sample-rate` | int | `16000` | Audio sample rate in Hz. Must match model requirements (typically 16000 or 8000). |
| `stt.vosk.max-alternatives` | int | `1` | Maximum number of recognition alternatives to generate. |

**Supported Models:**
- `vosk-model-en-us-0.22` (1.8GB, high accuracy) — Current default
- See [Vosk Models](https://alphacephei.com/vosk/models) for more options

---

## Whisper Configuration

`Developer` — Configures the Whisper STT engine (accurate, process-based, offline).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.whisper.binary-path` | String | `tools/whisper.cpp/main` | Path to whisper.cpp binary executable. |
| `stt.whisper.model-path` | String | `models/ggml-base.en.bin` | Path to Whisper model file (GGML format). |
| `stt.whisper.timeout-seconds` | int | `120` | Maximum transcription time in seconds. Prevents hanging on long/corrupted audio. (Java class default: 10) |
| `stt.whisper.language` | String | `en` | Language code (ISO 639-1). |
| `stt.whisper.threads` | int | `4` | Number of CPU threads for Whisper processing. |
| `stt.whisper.max-stdout-bytes` | int | `1048576` | Maximum stdout buffer size (1MB). Protects against malicious model output. |
| `stt.whisper.output` | String | `json` | Output format: `text` (plain text) or `json` (structured with tokens). JSON mode enables advanced reconciliation and pause detection. Note: read via `@Value` annotation in WhisperProcessManager. (Java default via @Value: `text`; shipped default: `json`) |

**Supported Models:**
- `ggml-tiny.en.bin` (75MB, fastest, lowest accuracy)
- `ggml-base.en.bin` (142MB, balanced, good accuracy) — Recommended
- `ggml-small.en.bin` (466MB, slower, better accuracy)

---

## Reconciliation (Phase 4)

`Developer` — Controls dual-engine reconciliation strategies.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.reconciliation.enabled` | boolean | `true` | Enable dual-engine reconciliation. When `true`, runs both engines in parallel and reconciles results. (Java class default: false) |
| `stt.reconciliation.strategy` | String | `overlap` | Reconciliation strategy. Options: `simple` (prefer primary), `confidence` (select by confidence score), `overlap` (Jaccard word overlap). (Java class default: SIMPLE) |
| `stt.reconciliation.overlap-threshold` | double | `0.6` | Minimum Jaccard similarity threshold for `overlap` strategy (0.0 to 1.0). |

**Performance Note:** Reconciliation doubles CPU usage (runs both engines) but improves accuracy by 10-25% in testing.

---

## Model Validation

`Developer` — Fail-fast validation of STT models at startup.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `stt.validation.enabled` | boolean | `true` | Validate model files exist at startup. Recommended to catch setup errors early. |

**Disable Only For:**
- Test profiles where models are intentionally missing
- CI environments using mock engines

---

# Quick Reference Profiles

## Production-Ready Configuration
```properties
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
stt.whisper.output=json
stt.watchdog.enabled=true
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
live-caption.enabled=true
```

## Testing Configuration
```properties
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false
stt.watchdog.enabled=false
stt.concurrency.vosk-max=2
stt.concurrency.whisper-max=1
```

## Optimizing for Speed (Low Latency)
```properties
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false
stt.concurrency.vosk-max=8
```

## Optimizing for Accuracy
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
stt.whisper.output=json
stt.whisper.model-path=models/ggml-small.en.bin
stt.whisper.threads=8
```

---

## Environment-Specific Configuration

Use Spring profiles to override properties per environment:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Validation Rules

| Error | Cause | Fix |
|-------|-------|-----|
| `ModelNotFoundException` | Invalid `stt.vosk.model-path` or `stt.whisper.model-path` | Run `./setup-models.sh` or manually download models |
| `IllegalArgumentException: threshold in [0,1]` | Invalid `stt.reconciliation.overlap-threshold` | Set value between 0.0 and 1.0 |
| `Engine concurrency limit reached` | Too many concurrent requests for `stt.concurrency.*-max` | Increase limits or reduce load |
| `Both engines unavailable` | All enabled engines failed to initialize | Check model paths and watchdog settings |

---

## See Also

- [Getting Started](../README.md#getting-started) - Initial setup and model installation
- [Developer Guide](developer-guide.md) - Architecture and development workflow
- [Operator Guide](operator-guide.md) - Running and maintaining the service
- [ADRs](adr/001-dual-engine-stt-strategy.md) - Architectural decision records
