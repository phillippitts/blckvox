# Operator Guide

This guide documents configuration, metrics, logs, capacity planning, and production profile guidance for blckvox.

## Configuration Overview
All configuration uses Spring Boot properties (application.properties). The most important groups:

### Audio Validation / Capture
```properties
# Validation thresholds (ms)
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000

# Capture (push-to-talk)
audio.capture.chunk-millis=40
audio.capture.max-duration-ms=600000
# audio.capture.device-name=
```

### STT Engines
```properties
# Vosk
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.vosk.sample-rate=16000
stt.vosk.max-alternatives=1

# Whisper
stt.whisper.binary-path=tools/whisper.cpp/main
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.timeout-seconds=120
stt.whisper.language=en
stt.whisper.threads=4
stt.whisper.max-stdout-bytes=1048576
# Output mode: text | json  (default json)
stt.whisper.output=json
stt.whisper.text-mode-confidence=0.85
```

### Orchestration & Parallel
```properties
# Parallel run timeout (ms)
stt.parallel.timeout-ms=120000

# Primary engine for single-engine routing
stt.orchestration.primary-engine=vosk  # vosk | whisper

# Maximum recording duration (seconds); 0 = unlimited
stt.orchestration.max-recording-duration-seconds=120

# Automatic paragraph breaks: insert newline when silence within audio exceeds this (ms); 0 = disabled
stt.orchestration.silence-gap-ms=1000
# RMS amplitude threshold for silence detection (0-32767 for 16-bit PCM); lower = more sensitive
stt.orchestration.silence-threshold=200
```

### Reconciliation (Phase 4)
```properties
# Enable reconciled path in orchestrator
stt.reconciliation.enabled=true
# Strategy: simple | confidence | overlap
stt.reconciliation.strategy=overlap
# Jaccard overlap threshold for overlap strategy
stt.reconciliation.overlap-threshold=0.6
# If Vosk confidence < this threshold, run Whisper too and reconcile (0.0-1.0)
# Lower = more dual-engine (better accuracy, more resources); higher = more single-engine
stt.reconciliation.confidence-threshold=0.7
```

### Concurrency & Watchdog
```properties
# Lightweight bulkheads
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.concurrency.acquire-timeout-ms=1000

# Dynamic scaling
stt.concurrency.dynamic-scaling-enabled=false
stt.concurrency.cpu-threshold-high=0.80
stt.concurrency.memory-threshold-high=0.85
stt.concurrency.scaling-interval-ms=5000

# Event-driven watchdog
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
# Confidence monitoring: blacklist engine if rolling avg falls below this (0.0-1.0)
stt.watchdog.confidence-blacklist-threshold=0.3
# Number of recent confidence scores to average
stt.watchdog.confidence-window-size=10
# Minimum samples required before evaluating the confidence trend
stt.watchdog.confidence-min-samples=5
# Exponential backoff between restart attempts
stt.watchdog.backoff-base-delay-ms=1000
stt.watchdog.backoff-multiplier=2.0
stt.watchdog.backoff-max-delay-ms=60000
stt.watchdog.health-summary-interval-millis=60000
stt.watchdog.confidence-grace-transcriptions=5
```

### Hotkeys
```properties
hotkey.type=double-tap               # single-key | double-tap | modifier-combo
hotkey.key=RIGHT_META
# hotkey.modifiers=META,SHIFT        # required for modifier-combo
# hotkey.threshold-ms=300            # for double-tap (100-1000ms)
# hotkey.toggle-mode=true            # true for click-to-toggle (shipped config; Java default is false)
# hotkey.reserved=META+TAB,META+L    # OS-reserved examples
```

### Typing / Fallback
```properties
typing.chunk-size=800
typing.inter-chunk-delay-ms=30
typing.focus-delay-ms=100
typing.restore-clipboard=true
typing.clipboard-restore-delay-ms=200
typing.clipboard-only-fallback=false
typing.normalize-newlines=LF         # LF | CRLF | NONE
typing.trim-trailing-newline=true
typing.enable-robot=true
# typing.paste-shortcut=os-default    # os-default | META+V | CONTROL+V
```

## Observability

### JMX Metrics (Micrometer)

Runtime metrics are available via JMX (JConsole/VisualVM). Key metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `blckvox.transcription.duration` | Timer | Transcription latency by engine and strategy |
| `blckvox.transcription.count` | Counter | Transcription count by engine and result |
| `blckvox.processing.ratio` | DistributionSummary | Processing-time-to-audio-duration ratio by engine |
| `blckvox.engine.failure` | Counter | Engine failure count by engine |
| `blckvox.engine.restart` | Counter | Engine restart count by engine |
| `blckvox.typing.fallback` | Counter | Typing fallback count by tier |
| `blckvox.typing.count` | Counter | Successful transcriptions delivered for typing, by engine |
| `blckvox.reconciliation.confidence` | DistributionSummary | Confidence scores by engine (records all non-negative confidence values) |
| `blckvox.capture.active` | Gauge | Whether audio capture is currently active (1=active, 0=idle) |
| `blckvox.event.executor.discard` | Counter | Event executor task discards due to queue saturation |

Configuration: `management.metrics.export.jmx.enabled=true` (enabled by default).

### Metrics Alerting Thresholds

Recommended alerting thresholds for production monitoring:

| Metric | Warning Threshold | Critical Threshold | Action |
|--------|------------------|-------------------|--------|
| `blckvox.engine.failure` | > 3/hour | > 10/hour | Check engine health, review logs for model/binary issues |
| `blckvox.engine.restart` | > 2/hour | > 5/hour | Investigate root cause; watchdog may be cycling |
| `blckvox.transcription.duration` (p95) | > 5s | > 15s | Reduce Whisper threads, check CPU load |
| `blckvox.typing.fallback` | > 1/hour (tier 2+) | > 5/hour | Check Accessibility permissions |
| `blckvox.event.executor.discard` | > 1/hour | > 10/hour | Increase event pool size or investigate bottleneck |
| `blckvox.reconciliation.confidence` (avg) | < 0.5 | < 0.3 | Model quality issue; consider upgrading model |

### Key Log Events

Key log events to monitor:
- `TranscriptionCompletedEvent` - engine used, confidence, duration, text length
- `EngineFailureEvent` / `EngineRecoveredEvent` - watchdog health transitions
- `AllTypingFallbacksFailedEvent` - text output failures
- `CaptureErrorEvent` - audio capture issues

All logs are PII-safe: INFO level never includes full transcripts, only durations and character counts.

## Logging
- INFO logs never include full transcripts; only durations and character counts.
- DEBUG logs may include truncated previews via LogSanitizer.
- Error events are centralized and throttled (Hotkey permission/ conflict; capture errors).
- Audit log: `logs/audit.log` (separate appender, daily rollover, 365-day retention).

## Capacity Planning

### Memory Budget

| Component | Memory (Resident) | Notes |
|-----------|-------------------|-------|
| JVM + Spring Boot | ~200 MB | Heap + metaspace + thread stacks |
| Vosk model (in-process) | ~1.8 GB | Loaded once via JNI, held for app lifetime |
| Whisper model (subprocess) | ~300 MB peak | Loaded per transcription by whisper.cpp process |
| Audio ring buffer | ~2 MB | PCM capture buffer (configurable via max-duration-ms) |
| Log buffers | ~10 MB | Log4j2 async appender queues |
| **Total (idle)** | **~2.0 GB** | Without active transcription |
| **Total (active)** | **~2.5 GB** | During parallel Vosk+Whisper transcription |

**Recommendation:** Minimum 4 GB available RAM. 8 GB recommended for comfortable headroom.

### CPU Budget

| Operation | CPU Cores | Duration | Frequency |
|-----------|-----------|----------|-----------|
| Idle | < 0.01 cores | Continuous | Always |
| Audio capture | < 0.1 cores | While recording | Per dictation |
| Vosk transcription | 1 core | ~100-500ms | Per dictation |
| Whisper transcription | 4 cores (configurable) | ~1-5s | Per dictation (if reconciliation enabled) |
| **Peak (reconciliation)** | **~5 cores** | ~2-5s | Per dictation |

**Recommendation:** Minimum 4 cores. 8+ cores recommended if using reconciliation.

### Disk Budget

| Component | Disk Space | Growth Rate |
|-----------|-----------|-------------|
| Vosk model | 1.8 GB | Static |
| Whisper model | 142 MB (base.en) | Static |
| whisper.cpp binary | ~15 MB | Static |
| Application JAR | ~30 MB | Per release |
| Log files | ~10 MB/day | Daily rotation, 30-day retention |
| Audit log | ~1 MB/day | Daily rotation, 365-day retention |
| Temp WAV files | ~1-5 MB each | Transient (deleted after transcription) |
| **Total (static)** | **~2.0 GB** | |
| **Total (with 30 days logs)** | **~2.3 GB** | |

**Recommendation:** Minimum 5 GB free disk space for models + logs + growth.

## Thread Pool Tuning Guide

The application uses two dedicated thread pools configured via `threadpool.*` properties:

### STT Executor (`sttExecutor`)
Handles parallel STT engine transcription tasks.

| Hardware Profile | core-pool-size | max-pool-size | queue-capacity | Rationale |
|-----------------|---------------|--------------|----------------|-----------|
| Low-end (2-4 cores) | 2 | 4 | 10 | Default; prevents CPU contention |
| Mid-range (4-8 cores) | 4 | 8 | 20 | Room for concurrent Vosk+Whisper |
| High-end (8+ cores) | 8 | 16 | 50 | Maximum throughput |

- **Rejection policy:** Custom `AbortPolicy` — logs and throws `RejectedExecutionException` when pool and queue are full
- **MDC propagation:** Log4j2 ThreadContext copied to worker threads

### Event Executor (`eventExecutor`)
Offloads CPU-intensive transcription work from Spring's event bus.

| Hardware Profile | core-pool-size | max-pool-size | queue-capacity | Rationale |
|-----------------|---------------|--------------|----------------|-----------|
| Low-end (2-4 cores) | 2 | 4 | 10 | Default; adequate for typical hotkey rate |
| Mid-range (4-8 cores) | 2 | 4 | 20 | Larger queue for burst tolerance |
| High-end (8+ cores) | 4 | 8 | 50 | High concurrency |

- **Rejection policy:** Custom discard-oldest — discards the oldest queued task, increments the discard counter, then re-submits the new task; prevents the event bus from blocking
- **Discard metric:** `blckvox.event.executor.discard` counter increments on each discard
- **MDC propagation:** Same as sttExecutor

### Tuning Symptoms

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `sttExecutor rejected task -- pool and queue are full` in logs | STT pool saturated | Increase `threadpool.stt.max-pool-size` |
| `blckvox.event.executor.discard` > 0 | Event pool saturated | Increase `threadpool.event.queue-capacity` or `max-pool-size` |
| High CPU during idle | Thread pool too large | Reduce `core-pool-size` |
| Slow transcription response | Not enough threads | Increase `max-pool-size` (up to CPU core count) |

## Engine Health Indicators

The system tray displays engine health notifications based on `EngineHealthChangedEvent` transitions published by the watchdog.

### Health States

| State | Meaning | Tray Behavior |
|-------|---------|---------------|
| **HEALTHY** | Engine initialized and performing well | No notification (normal operation) |
| **DEGRADED** | Engine experienced failure or low confidence | Warning notification in system tray |
| **DISABLED** | Engine exceeded restart budget, in cooldown | Error notification in system tray |

### State Transitions

```
HEALTHY → DEGRADED : Engine failure or low confidence detected
DEGRADED → HEALTHY : Successful restart or recovery
DEGRADED → DISABLED : Restart budget exhausted
DISABLED → DEGRADED : Safety mode force-enables best engine (all disabled)
```

### Confidence Monitoring Filtering

The watchdog's confidence monitor excludes two categories of results from the rolling average to prevent distorted readings:

- **Failed results** (`isFailure() == true`): excluded because they report a confidence of 0.0 and would artificially drag the average below the blacklist threshold.
- **Silent results** (empty transcription text with confidence >= 1.0): excluded because Vosk reports confidence 1.0 for silence, which would inflate the average and mask real degradation.

Only results with actual transcribed text and meaningful confidence scores affect the `stt.watchdog.confidence-blacklist-threshold` evaluation.

### Secondary Engine Lazy Initialization

When `stt.orchestration.primary-engine` is configured, only the primary engine is initialized at startup. Secondary engines are deferred and initialized on first use (`initializeOnDemand`). This reduces startup time and memory pressure when reconciliation is disabled. If lazy initialization fails, the secondary engine transitions directly to DISABLED state.

### Troubleshooting Engine Health

| Symptom | Likely Cause | Resolution |
|---------|-------------|------------|
| Frequent DEGRADED notifications | Intermittent engine failures | Check model file integrity, verify binary permissions |
| Engine DISABLED | Restart budget exhausted (3 failures in 60 min) | Wait for cooldown (10 min default), check logs for root cause |
| Both engines DISABLED | Systemic issue | Safety mode auto-enables best engine; investigate model paths and disk space |
| Low confidence warnings | Poor audio quality or wrong model | Verify microphone settings, consider upgrading to larger model |

### Relevant Metrics

Monitor these metrics alongside tray notifications:
- `blckvox.engine.failure` — correlates with DEGRADED transitions
- `blckvox.engine.restart` — tracks restart attempts
- `blckvox.reconciliation.confidence` — tracks confidence scores that may trigger DEGRADED

## Production profile
Use Spring profiles to adjust configuration per environment:
```properties
# application-production.properties
stt.watchdog.enabled=true
stt.reconciliation.enabled=true
```
Run with: `--spring.profiles.active=production`.

## Operations Quick Tips
- Reconciliation rollout
  - Default is `stt.reconciliation.enabled=true` + `strategy=overlap`
  - Alternative strategies: `simple` (prefer primary engine) or `confidence` (highest confidence wins)
  - Optional: enable `stt.whisper.output=json` for better overlap tokens
- High CPU / slow Whisper
  - Reduce `stt.whisper.threads`
  - Increase `stt.parallel.timeout-ms` if needed
- Frequent Whisper failures
  - Check `whisper.cpp` quarantine/permissions
  - Watchdog will auto-restart up to budget; see logs
- Typing issues
  - If Robot fails (Accessibility), clipboard tier still works
  - Consider `typing.clipboard-only-fallback=true`
