# Class and Package Dependencies

## Core Interface Hierarchy

```mermaid
classDiagram
    class SttEngine {
        <<interface>>
        +initialize()
        +transcribe(byte[]) TranscriptionResult
        +isHealthy() boolean
        +close()
        +getEngineName() String
    }

    class DetailedTranscriptionEngine {
        <<interface>>
        +transcribeDetailed(byte[]) TranscriptionOutput
    }

    class AbstractSttEngine {
        <<abstract>>
        #ReentrantLock lock
        -ReentrantReadWriteLock transcriptionLock
        #boolean initialized
        #boolean closed
        -TranscriptionGuard guard
        +initialize() final
        +isHealthy() final
        +close() final
        #doInitialize()* abstract
        #doClose()* abstract
        #ensureInitialized() final
        #acquireGuard() final
        #releaseGuard() final
        #acquireTranscriptionLock() final
        #releaseTranscriptionLock() final
        #handleTranscriptionError() final
    }

    class VoskSttEngine {
        -VoskConfig config
        -VoskModelProvider modelProvider
        -SilenceDetector silenceDetector
        -ApplicationEventPublisher publisher
        -int silenceGapMs
        +transcribe(byte[]) TranscriptionResult
        #doInitialize()
        #doClose()
        +getEngineName() String
    }

    class WhisperSttEngine {
        -WhisperConfig cfg
        -ProcessManager manager
        -ApplicationEventPublisher publisher
        -boolean jsonMode
        -int silenceGapMs
        +transcribe(byte[]) TranscriptionResult
        #doInitialize()
        #doClose()
        +getEngineName() String
        +transcribeDetailed(byte[]) TranscriptionOutput
    }

    SttEngine <|.. AbstractSttEngine
    SttEngine <|-- DetailedTranscriptionEngine
    DetailedTranscriptionEngine <|.. WhisperSttEngine
    AbstractSttEngine <|-- VoskSttEngine
    AbstractSttEngine <|-- WhisperSttEngine

    AbstractSttEngine --> TranscriptionGuard : uses
    TranscriptionGuard <|.. ConcurrencyGuard
    TranscriptionGuard <|.. DynamicConcurrencyGuard
    VoskSttEngine --> VoskModelProvider : uses
    VoskSttEngine --> SilenceDetector : uses
    WhisperSttEngine --> ProcessManager : delegates to
```

## Reconciliation Strategy Pattern

```mermaid
classDiagram
    class TranscriptReconciler {
        <<interface>>
        +reconcile(EngineResult, EngineResult) TranscriptionResult
    }

    class AbstractReconciler {
        <<abstract>>
    }

    class SimplePreferenceReconciler {
        +reconcile() TranscriptionResult
    }

    class ConfidenceReconciler {
        +reconcile() TranscriptionResult
    }

    class WordOverlapReconciler {
        -double threshold
        +reconcile() TranscriptionResult
        -calculateOverlap() double
    }

    TranscriptReconciler <|.. AbstractReconciler
    AbstractReconciler <|-- SimplePreferenceReconciler
    AbstractReconciler <|-- ConfidenceReconciler
    AbstractReconciler <|-- WordOverlapReconciler
```

## Hotkey Trigger Strategy Pattern

```mermaid
classDiagram
    class HotkeyTrigger {
        <<interface>>
        +name() String
        +onKeyPressed(NormalizedKeyEvent) boolean
        +onKeyReleased(NormalizedKeyEvent) boolean
    }

    class SingleKeyTrigger {
        -String targetKey
        -boolean held
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    class DoubleTapTrigger {
        -String targetKey
        -int thresholdMs
        -long lastPressTime
        -boolean active
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    class ModifierCombinationTrigger {
        -String primaryKey
        -Set~String~ requiredModifiers
        -boolean held
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    HotkeyTrigger <|.. SingleKeyTrigger
    HotkeyTrigger <|.. DoubleTapTrigger
    HotkeyTrigger <|.. ModifierCombinationTrigger

    class HotkeyTriggerFactory {
        +from(HotkeyProperties) HotkeyTrigger
    }

    HotkeyTriggerFactory ..> HotkeyTrigger : creates
```

## Orchestration Layer Composition

```mermaid
classDiagram
    class HotkeyRecordingAdapter {
        -RecordingService recordingService
        -HotkeyProperties hotkeyProps
        +onHotkeyPressed(HotkeyPressedEvent)
        +onHotkeyReleased(HotkeyReleasedEvent)
        +onCaptureError(CaptureErrorEvent)
    }

    class RecordingService {
        <<interface>>
        +startRecording() boolean
        +stopRecording() boolean
        +cancelRecording()
        +toggleRecording() boolean
    }

    class CaptureOrchestrator {
        <<interface>>
        +startCapture() UUID
        +stopCapture(UUID) byte[]
        +cancelCapture(UUID)
    }

    class TranscriptionOrchestrator {
        <<interface>>
        +transcribe(byte[]) TranscriptionResult
    }

    class CaptureStateMachine {
        -Lock lock
        -UUID activeSession
        +startCapture(UUID) boolean
        +stopCapture(UUID) boolean
        +cancelCapture() UUID
        +getActiveSession() UUID
        +isActive() boolean
    }

    HotkeyRecordingAdapter --> RecordingService : delegates
    RecordingService --> CaptureOrchestrator : uses
    RecordingService --> TranscriptionOrchestrator : uses
    CaptureOrchestrator --> CaptureStateMachine : uses
    CaptureOrchestrator --> AudioCaptureService : uses
    TranscriptionOrchestrator --> ParallelSttService : uses
    TranscriptionOrchestrator --> TranscriptReconciler : uses
```

## Live Caption Package

```mermaid
classDiagram
    class LiveCaptionManager {
        -LiveCaptionProperties props
        -AtomicBoolean enabled
        -LiveCaptionWindow window
        +isEnabled() boolean
        +setEnabled(boolean)
        +onStateChanged(ApplicationStateChangedEvent)
        +onPcmChunk(PcmChunkCapturedEvent)
        +onVoskPartialResult(VoskPartialResultEvent)
    }

    class LiveCaptionWindow {
        -Stage stage
        -Canvas canvas
        -Label captionLabel
        +show()
        +hide()
        +updateWaveform(short[])
        +updateCaption(String, boolean)
    }

    class VoskStreamingService {
        -VoskConfig config
        -ApplicationEventPublisher publisher
        -VoskModelProvider modelProvider
        -Recognizer recognizer
        -Object recognizerLock
        +onStateChanged(ApplicationStateChangedEvent)
        +onPcmChunk(PcmChunkCapturedEvent)
        +shutdown()
    }

    class PcmChunkCapturedEvent {
        <<record>>
        +byte[] pcmData
        +int length
        +UUID sessionId
    }

    class VoskPartialResultEvent {
        <<record>>
        +String text
        +boolean isFinal
    }

    LiveCaptionManager --> LiveCaptionWindow : creates
    LiveCaptionManager --> PcmSampleConverter : uses
    LiveCaptionManager ..> PcmChunkCapturedEvent : listens
    LiveCaptionManager ..> VoskPartialResultEvent : listens
    VoskStreamingService ..> PcmChunkCapturedEvent : listens
    VoskStreamingService ..> VoskPartialResultEvent : publishes
```

## Package Dependency Graph

```mermaid
graph TB
    subgraph service["com.boombapcompile.blckvox.service"]
        Orch[orchestration]
        STT[stt + stt.parallel]
        Audio[audio.capture]
        Hotkey[hotkey]
        Reconcile[reconcile]
        Fallback[fallback]
        LiveCaption[livecaption]
        Tray[tray]
    end

    subgraph domain["com.boombapcompile.blckvox.domain"]
        Entities[TranscriptionResult<br/>AudioFormat]
    end

    subgraph config["com.boombapcompile.blckvox.config"]
        Props[Properties<br/>Beans]
    end

    subgraph exception["com.boombapcompile.blckvox.exception"]
        Exceptions[TranscriptionException<br/>InvalidAudioException]
    end

    subgraph util["com.boombapcompile.blckvox.util"]
        Utils[TimeUtils<br/>ProcessTimeouts]
    end

    Orch --> Audio
    Orch --> STT
    Orch --> Hotkey
    Orch --> Reconcile
    Orch --> Fallback
    STT --> domain
    Reconcile --> domain
    Audio --> domain
    LiveCaption --> Audio
    LiveCaption --> STT
    LiveCaption --> config
    Tray --> LiveCaption

    STT --> exception
    Audio --> exception

    STT --> config
    Audio --> config
    Hotkey --> config
    Orch --> config

    STT --> util
    Audio --> util

    style service fill:#fff4e1
    style domain fill:#c8e6c9
    style config fill:#f3e5f5
    style exception fill:#ffcdd2
    style util fill:#fff9c4
```

## Dependency Rules

### Allowed Dependencies (Clean Architecture)

```mermaid
flowchart LR
    Svc[Service] --> Domain[Domain]
    Svc --> Config[Config]
    Svc --> Exception[Exception]
    Svc --> Util[Util]

    style Svc fill:#fff4e1
    style Domain fill:#c8e6c9
    style Config fill:#f3e5f5
    style Exception fill:#ffcdd2
    style Util fill:#fff9c4
```

**Note:** There is no presentation/REST layer (`spring.main.web-application-type=none`). The application uses event-driven input via hotkeys and system tray.

### Forbidden Dependencies

| From | To | Reason |
|------|-----|--------|
| Domain | Service | Domain must be pure, no service dependencies |
| Domain | Config | Domain must be configuration-agnostic |
| Config | Service | Configuration should not depend on business logic |
| Util | Service | Utilities must be reusable across layers |
| Exception | Service | Exceptions are cross-cutting concerns |

## Key Design Patterns by Package

| Package | Pattern | Implementation |
|---------|---------|----------------|
| `service.stt` | Template Method | AbstractSttEngine (doInitialize/doClose) |
| `service.reconcile` | Strategy | TranscriptReconciler interface + 3 implementations |
| `service.hotkey` | Strategy + Factory | HotkeyTrigger + HotkeyTriggerFactory |
| `service.stt` | Adapter | VoskSttEngine wraps JNI, WhisperSttEngine wraps binary |
| `service.orchestration` | Facade | RecordingService coordinates subsystems |
| `service.*` | Observer | ApplicationEventPublisher for loose coupling |
| `service.stt` | Object Pool | ConcurrencyGuard with Semaphore |
| `service.livecaption` | Bridge | LiveCaptionManager bridges Spring events to JavaFX thread |
| `service.livecaption` | Feature Toggle | @ConditionalOnProperty for zero-overhead disable |

## Thread Safety Guarantees

| Class | Thread Safety | Mechanism |
|-------|---------------|-----------|
| VoskSttEngine | Thread-safe | Per-call recognizer, synchronized model access |
| WhisperSttEngine | Thread-safe | Per-call temp files, synchronized token cache |
| AbstractSttEngine | Thread-safe | Synchronized state transitions (lock) |
| CaptureStateMachine | Thread-safe | ReentrantLock for all state access |
| PcmRingBuffer | Thread-safe | Synchronized methods |
| ConcurrencyGuard | Thread-safe | Semaphore for bounded concurrency |
| DefaultParallelSttService | Thread-safe | CompletableFuture with bounded executor |
| HotkeyManager | Thread-safe | Receives a single `Consumer<NormalizedKeyEvent>` callback; uses `volatile boolean running` for thread safety |
| All Reconcilers | Thread-safe | Stateless (no shared mutable state) |
| FallbackManager | Thread-safe | Stateless operations |
| VoskStreamingService | Thread-safe | synchronized(recognizerLock) for recognizer access |
| LiveCaptionManager | Thread-safe | AtomicBoolean for enabled flag, JavaFX thread for UI |
| LiveCaptionWindow | Single-thread | JavaFX Application Thread only |

## Immutable Value Objects

All domain objects are immutable Java 17 records:

```mermaid
classDiagram
    class TranscriptionResult {
        <<record>>
        +String text
        +double confidence
        +Instant timestamp
        +String engineName
        +String failureReason
    }

    class EngineResult {
        <<record>>
        +String text
        +double confidence
        +List~String~ tokens
        +long durationMs
        +String engineName
        +String rawJson
    }

    class NormalizedKeyEvent {
        <<record>>
        +Type type
        +String key
        +Set~String~ modifiers
        +long whenMillis
    }

    class VoskConfig {
        <<record>>
        +String modelPath
        +int sampleRate
        +int maxAlternatives
    }

    class WhisperConfig {
        <<record>>
        +String binaryPath
        +String modelPath
        +int timeoutSeconds
        +String language
        +int threads
        +int maxStdoutBytes
        +double textModeConfidence
    }
```

## Configuration Binding Flow

```mermaid
flowchart LR
    Props[application.properties] --> Spring[Spring Boot]
    Spring --> VoskProps[@ConfigurationProperties]
    Spring --> WhisperProps[@ConfigurationProperties]
    Spring --> AudioProps[@ConfigurationProperties]
    Spring --> HotkeyProps[@ConfigurationProperties]

    VoskProps --> VoskConfig[VoskConfig record]
    WhisperProps --> WhisperConfig[WhisperConfig record]
    AudioProps --> AudioCaptureProperties[AudioCaptureProperties]
    HotkeyProps --> HotkeyProperties[HotkeyProperties]

    VoskConfig --> Bean1[@Bean VoskSttEngine]
    WhisperConfig --> Bean2[@Bean WhisperSttEngine]

    style Props fill:#f3e5f5
    style Spring fill:#e1f5ff
    style Bean1 fill:#c8e6c9
    style Bean2 fill:#c8e6c9
```
