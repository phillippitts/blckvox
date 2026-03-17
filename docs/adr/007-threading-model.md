# ADR-007: Threading Model

## Status
Accepted (2025-01-20)

## Context
The application performs CPU-intensive STT transcription and asynchronous event delivery.
Without dedicated thread pools:
- Parallel Vosk + Whisper transcription would block the main thread
- Spring event listeners could delay each other on the shared `SimpleAsyncTaskExecutor`
- Log4j2 MDC context would be lost across thread boundaries
- Unbounded thread creation could exhaust system resources

## Decision
Use **two bounded thread pools** with MDC propagation, configured in `ThreadPoolConfig`.

### Pool Separation

| Pool | Bean Name | Purpose | Core/Max | Queue | Rejection Policy |
|------|-----------|---------|----------|-------|-----------------|
| STT | `sttExecutor` | Parallel Vosk + Whisper transcription | 2/4 | 10 | `CallerRunsPolicy` |
| Event | `eventExecutor` | Async event listener offload | 2/4 | 10 | `DiscardOldestPolicy` (logged) |

**Why two pools:**
- STT work is CPU-heavy and latency-sensitive; `CallerRunsPolicy` provides backpressure
  without dropping work.
- Event delivery is fire-and-forget; `DiscardOldestPolicy` prevents queue buildup when
  the system is overwhelmed (e.g., rapid hotkey presses).
- Separation prevents a slow transcription from starving event delivery and vice versa.

### MDC Decoration
Both pools use a custom `TaskDecorator` that captures and restores Log4j2 `ThreadContext`
across thread boundaries. This ensures log correlation IDs propagate into worker threads.

### Sizing Rationale
- **Core = 2:** Sufficient for the common dual-engine scenario (one Vosk + one Whisper task).
- **Max = 4:** Headroom for burst scenarios without over-provisioning on typical hardware.
- **Queue = 10:** Small bounded queue prevents unbounded memory growth while allowing
  brief burst absorption.
- **Keep-alive = 60s:** Threads beyond core shrink back after inactivity.

### Graceful Shutdown
Both pools configure `setWaitForTasksToCompleteOnShutdown(true)` with a 30-second
await termination, ensuring in-flight transcriptions complete on application stop.

## Consequences

### Positive
- Predictable resource usage (bounded pools, bounded queues)
- Log correlation preserved across async boundaries
- Graceful degradation under load (backpressure or discard, not OOM)
- Configurable via `threadpool.*` properties without code changes

### Negative
- Two pools consume threads even when idle (mitigated by keep-alive)
- `DiscardOldestPolicy` on event pool can silently drop events under extreme load

### Risks
- Pool sizing may need tuning on constrained hardware (Raspberry Pi, etc.)
- MDC decorator adds minor overhead per task submission

## Related
- ADR-006: Event-driven architecture (events flow through these pools)
- `ThreadPoolConfig.java`: Implementation
- `ThreadPoolProperties.java`: Configuration binding
