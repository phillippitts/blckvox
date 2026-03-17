# ADR-006: 2-Tier Event-Driven Architecture

## Status
Accepted (2025-01-14)
**Revised** (2025-01-20) — Updated to reflect removal of persistence layer and event-driven design

## Context
Desktop application (hotkey-triggered) with:
- No database persistence (removed PostgreSQL, JPA in cleanup)
- Event-driven coordination between services
- No REST/HTTP layer (`spring.main.web-application-type=none`)
- Spring ApplicationEvents for decoupled communication

## Decision
Follow **2-tier event-driven architecture**:

```
src/main/java/com/boombapcompile/blckvox/
├── service/           # Tier 1: Business logic (event publishers & listeners)
│   ├── hotkey/        # HotkeyTrigger → publishes HotkeyPressedEvent
│   ├── orchestration/ # HotkeyRecordingAdapter → RecordingService → publishes TranscriptionCompletedEvent
│   ├── fallback/      # FallbackManager → listens to TranscriptionCompletedEvent
│   ├── stt/           # Speech-to-text engines (Vosk, Whisper)
│   ├── audio/         # Audio capture
│   └── tray/          # SystemTrayManager (menu bar UI)
├── config/            # Tier 2: Infrastructure (Spring beans, properties)
│   ├── hotkey/
│   ├── orchestration/
│   ├── stt/
│   └── ...
├── domain/            # Shared records (TranscriptionResult, etc.)
└── exception/         # Custom exceptions
```

**Event Flow:**
1. `HotkeyTrigger` publishes `HotkeyPressedEvent` → triggers audio capture
2. `RecordingService` transcribes audio → publishes `TranscriptionCompletedEvent`
3. `FallbackManager` listens to `TranscriptionCompletedEvent` → types text via clipboard

**Design Rules:**
- Use `ApplicationEventPublisher` for cross-service communication
- Use `@EventListener` for consuming events
- Constructor injection only (no `@Autowired` fields)
- Services are stateless (event data carries context)
- No HTTP layer (event-driven desktop application)

## Consequences

### Positive
- ✅ **Decoupled services**: Publishers don't know about listeners
- ✅ **Testability**: EventCapturingPublisher mocks in tests
- ✅ **No database overhead**: Removed 41% JAR bloat (93MB → 55MB)
- ✅ **Scalable**: Add new listeners without modifying publishers
- ✅ **Observable**: Events naturally map to metrics/logs

### Negative
- ❌ **Implicit control flow**: Event chains harder to trace than direct calls
- ❌ **No event persistence**: Events lost if app crashes (acceptable for desktop app)
- ❌ **Debugging complexity**: Stack traces don't show full event chain

### Mitigation
- Comprehensive logging at event publish/consume points
- MDC (requestId) propagates through event chain
- Integration tests verify full event flows (HotkeyToTypingIntegrationTest)

## Alternatives Considered

### 3-Tier with Repository Layer
- **Rejected**: Database removed in dependency cleanup (ADR-006 original assumed PostgreSQL)
- **Advantage**: Would enable transcription history, user preferences
- **Disadvantage**: Unnecessary complexity for current scope

### Direct Service Calls (No Events)
- **Rejected**: Tight coupling between services
- **Advantage**: Simpler control flow, easier debugging
- **Disadvantage**: FallbackManager would depend on RecordingService, HotkeyManager would depend on AudioCaptureService

### Message Queue (RabbitMQ, Kafka)
- **Rejected**: Over-engineering for single-JVM desktop app
- **Advantage**: Durability, distributed systems support
- **Disadvantage**: External dependencies, operational complexity

## Implementation Evidence
**Event Publishers:**
- `DefaultTranscriptionOrchestrator` → `publisher.publishEvent(new TranscriptionCompletedEvent(...))`
- `HotkeyManager` → publishes `HotkeyPressedEvent`, `HotkeyReleasedEvent`

**Event Listeners:**
- `HotkeyRecordingAdapter` → `@EventListener onHotkeyPressed/onHotkeyReleased`
- `TypingEventsListener` → `@EventListener` for `TypingFallbackEvent` + `AllTypingFallbacksFailedEvent`

**Test Verification:**
- `ReconciliationE2ETest` → Verifies full event chain with EventCapturingPublisher
- `HotkeyToTypingIntegrationTest` → End-to-end hotkey → typing flow

## References
- Event classes: `service/orchestration/event/`, `service/hotkey/event/`, `service/fallback/event/`
- Dependency cleanup: build.gradle (removed spring-data-jpa, postgresql, flyway)
