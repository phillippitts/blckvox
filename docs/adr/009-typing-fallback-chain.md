# ADR-009: Typing Fallback Chain

## Status
Accepted (2025-01-20)

## Context
After transcription, the text must be delivered to the user's active application.
Desktop text injection is fragile due to:
- macOS Accessibility permissions (required for `java.awt.Robot`)
- Clipboard contention with other applications
- Headless environments where neither Robot nor Clipboard work
- Different OS keystroke conventions (Cmd+V vs Ctrl+V)

A single delivery mechanism would fail silently in restricted environments.

## Decision
Implement a **fallback chain** via `StrategyChainTypingService`:

```
Robot (Tier 1) --> Clipboard (Tier 2) --> Unknown Adapters (Tier 3, if any) --> Notify-Only (Last Resort)
```

### Tier 1: RobotTypingAdapter
- Simulates Cmd+V (macOS) or Ctrl+V (Windows/Linux) via `java.awt.Robot`
- Sets clipboard content, then pastes via keystroke simulation
- Chunks large text (>800 chars) with 30ms inter-chunk delays
- Configurable focus delay (100ms default) before pasting
- **Requires:** macOS Accessibility permission
- **Fails when:** Permission denied, headless JVM, `typing.enable-robot=false`

### Tier 2: ClipboardTypingAdapter
- Delivers text via `java.awt.Toolkit` clipboard
- Newline normalization (LF, CRLF, or NONE)
- Optional clipboard save/restore via virtual thread
- **Always available** (`canType()` returns true)
- **Fails when:** AWT toolkit unavailable (rare)

### Tier 3: NotifyOnlyAdapter
- Logs character count at INFO, full text at DEBUG only (PII-safe)
- **Always succeeds** — prevents retry loops
- **Last resort** when no OS integration is possible

### Chain Execution
```
for each adapter in [Robot, Clipboard, <unknown adapters...>, Notify]:
    if adapter.canType():
        try adapter.type(text):
            if success: return
            else: publish TypingFallbackEvent
        catch RuntimeException:
            publish TypingFallbackEvent
            continue
publish AllTypingFallbacksFailedEvent
```

Note: Any Spring-registered `TypingAdapter` beans with names other than "robot", "clipboard",
or "notify" are appended between Clipboard and Notify. An WARN log is emitted for each unknown
adapter at startup rather than silently dropping it.

### Observability
Each fallback publishes a `TypingFallbackEvent(tier, reason)` consumed by
`MetricsEventListener` to increment `blckvox.typing.fallback` counter by tier.
If the entire chain fails, `AllTypingFallbacksFailedEvent` is published.

## Consequences

### Positive
- Graceful degradation: restricted environments still get notification-level delivery
- Each tier is independently testable via facade interfaces (`RobotFacade`, `ClipboardFacade`)
- Fallback metrics enable operators to detect permission issues without logs
- Configurable per-tier behavior via `typing.*` properties

### Negative
- Clipboard-based delivery requires user to manually paste (Tier 2 without Robot)
- Notify-only tier provides no text delivery (logging only)
- Clipboard restoration race conditions under rapid transcription

### Configuration
```properties
typing.enable-robot=true              # Disable Robot entirely
typing.chunk-size=800                 # Robot text chunking threshold
typing.inter-chunk-delay-ms=30        # Delay between chunks
typing.focus-delay-ms=100             # Pre-paste focus delay
typing.restore-clipboard=true         # Save/restore prior clipboard
typing.normalize-newlines=LF          # LF | CRLF | NONE
typing.trim-trailing-newline=true     # Strip trailing newlines
```

## Related
- ADR-006: Event-driven architecture (`FallbackManager` listens to `TranscriptionCompletedEvent`)
- ADR-007: Threading model (typing runs on `eventExecutor`)
- ADR-008: JMX metrics (`TypingFallbackEvent` drives fallback counters)
- `StrategyChainTypingService.java`: Chain orchestration
- `FallbackManager.java`: Event listener entry point
