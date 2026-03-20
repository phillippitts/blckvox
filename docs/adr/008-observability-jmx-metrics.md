# ADR-008: Observability via JMX Metrics

## Status
Accepted (2025-01-20)

## Context
The application is a headless desktop process (no web layer, `spring.main.web-application-type=none`).
Standard Spring Boot Actuator endpoints are not available. Operators need runtime observability for:
- Transcription latency and throughput
- Engine health and failure rates
- Typing fallback frequency
- Processing ratio (processing time vs. audio duration)

## Decision
Use **Micrometer with a JMX registry** for runtime metrics, accessible via JConsole/VisualVM.

### Registry Configuration
- `JmxMeterRegistry` bean in `MetricsConfig`
- Common tag: `application=blckvox`
- JMX access: local attach only (no remote port configured; use JConsole/VisualVM local attach)
- No web dependencies required

### Metrics Tracked

| Metric | Type | Tags | Source |
|--------|------|------|--------|
| `blckvox.transcription.duration` | Timer | `engine`, `strategy` (defaults to `"single"` when non-reconciled) | `TranscriptionMetricsPublisher` |
| `blckvox.transcription.count` | Counter | `engine`, `result` (+ `error` on failure only) | `TranscriptionMetricsPublisher` |
| `blckvox.processing.ratio` | DistributionSummary | `engine` | `TranscriptionMetricsPublisher` |
| `blckvox.engine.failure` | Counter | `engine` | `MetricsEventListener` |
| `blckvox.engine.restart` | Counter | `engine` | `MetricsEventListener` |
| `blckvox.typing.fallback` | Counter | `tier` | `MetricsEventListener` |
| `blckvox.typing.count` | Counter | `engine` | `MetricsEventListener` |
| `blckvox.reconciliation.confidence` | DistributionSummary | `engine` | `MetricsEventListener` |
| `blckvox.capture.active` | Gauge | (none) | `MetricsEventListener` |

### Event-Driven Recording
Metrics are recorded reactively via Spring `@EventListener` in `MetricsEventListener`,
not via polling or interceptors. This keeps metrics orthogonal to business logic:

```
TranscriptionCompletedEvent  --> records duration, count, confidence
EngineFailureEvent           --> increments failure counter
EngineRecoveredEvent         --> increments restart counter
TypingFallbackEvent          --> increments fallback counter by tier
```

### Dependencies
```groovy
implementation 'io.micrometer:micrometer-core'
implementation 'io.micrometer:micrometer-registry-jmx'
```

## Consequences

### Positive
- Zero web dependencies (no Actuator, no HTTP server)
- Standard JMX tooling works out of the box
- Event-driven recording adds no coupling to service classes
- All metrics tagged for filtering in JMX clients

### Negative
- JMX requires local or SSH access (no remote dashboard without additional tooling)
- No built-in alerting (would need external JMX monitoring for production)

### Future
- Could add Prometheus registry for remote scraping if a web layer is ever introduced
- Could add custom MBeans for engine lifecycle management

## Related
- ADR-006: Event-driven architecture (metrics listen to same events)
- ADR-007: Threading model (metrics are thread-safe, called from both pools)
- `MetricsConfig.java`: JMX registry setup
- `TranscriptionMetricsPublisher.java`: Transcription-specific metrics
- `MetricsEventListener.java`: Event-driven metric recording
