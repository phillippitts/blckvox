# Operator Guide

This guide documents configuration, metrics, logs, and production profile guidance for blckvox.

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
```

### Orchestration & Parallel
```properties
# Parallel run timeout (ms)
stt.parallel.timeout-ms=120000

# Primary engine for single-engine routing
stt.orchestration.primary-engine=vosk  # vosk | whisper
```

### Reconciliation (Phase 4)
```properties
# Enable reconciled path in orchestrator
stt.reconciliation.enabled=true
# Strategy: simple | confidence | overlap
stt.reconciliation.strategy=overlap
# Jaccard overlap threshold for overlap strategy
stt.reconciliation.overlap-threshold=0.6
```

### Concurrency & Watchdog
```properties
# Lightweight bulkheads
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.concurrency.acquire-timeout-ms=1000

# Event-driven watchdog
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

### Hotkeys
```properties
hotkey.type=double-tap               # single-key | double-tap | modifier-combo
hotkey.key=RIGHT_META
# hotkey.modifiers=META,SHIFT        # required for modifier-combo
# hotkey.threshold-ms=300            # for double-tap (100-1000ms)
# hotkey.toggle-mode=true            # true for click-to-toggle (default)
# hotkey.reserved=META+TAB,META+L    # OS-reserved examples
```

### Typing / Fallback
```properties
typing.chunk-size=800
typing.inter-chunk-delay-ms=30
typing.focus-delay-ms=100
typing.restore-clipboard=true
typing.clipboard-only-fallback=false
typing.normalize-newlines=LF         # LF | CRLF | NONE
typing.trim-trailing-newline=true
typing.enable-robot=true
# typing.paste-shortcut=os-default    # os-default | META+V | CONTROL+V
```

## Observability

**Note:** Micrometer/Prometheus metrics are planned for Phase 6. Current observability is via structured logging.

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
