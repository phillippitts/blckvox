# Event Flow Map

This document is the definitive reference for the Spring event wiring in the blckvox application. It covers all 15 event types, their publishers, listeners, record fields, and threading models. Blckvox is a Spring Boot event-driven desktop application with no HTTP layer; all inter-component communication flows through `ApplicationEventPublisher`. The diagrams below use Mermaid syntax and are organized from a complete wiring overview down to focused sub-flows and error paths.

---

## 1. Complete Event Wiring Map

This flowchart shows every event as a node connecting its publisher to its listener(s). Events are grouped by subsystem. Edge colors encode the threading model: green for synchronous delivery on the publisher thread, orange for `@Async("eventExecutor")` delivery on a pooled thread, and blue for `Platform.runLater` (JavaFX Application Thread) delivery inside the listener body.

```mermaid
flowchart LR
    %% ── Subsystem: Hotkey ──────────────────────────────────────────
    subgraph Hotkey ["Hotkey Subsystem"]
        HM[HotkeyManager]
        HP{{HotkeyPressedEvent}}
        HR{{HotkeyReleasedEvent}}
        HPD{{HotkeyPermissionDeniedEvent}}
        HC{{HotkeyConflictEvent}}
    end

    %% ── Subsystem: Orchestration ───────────────────────────────────
    subgraph Orchestration ["Orchestration Subsystem"]
        HRA[HotkeyRecordingAdapter]
        AST[ApplicationStateTracker]
        ASC{{ApplicationStateChangedEvent}}
        DTO[DefaultTranscriptionOrchestrator]
        TC{{TranscriptionCompletedEvent}}
    end

    %% ── Subsystem: Audio ───────────────────────────────────────────
    subgraph Audio ["Audio Capture Subsystem"]
        JSACS[JavaSoundAudioCaptureService]
        PCC{{PcmChunkCapturedEvent}}
        BOE{{BufferOverflowEvent}}
        CE{{CaptureErrorEvent}}
    end

    %% ── Subsystem: LiveCaption ─────────────────────────────────────
    subgraph LiveCaption ["Live Caption Subsystem"]
        VSS[VoskStreamingService]
        VPR{{VoskPartialResultEvent}}
        LCM[LiveCaptionManager]
    end

    %% ── Subsystem: Watchdog ────────────────────────────────────────
    subgraph Watchdog ["STT Engine Watchdog Subsystem"]
        EEP[EngineEventPublisher]
        EFE{{EngineFailureEvent}}
        ERE{{EngineRecoveredEvent}}
        SEW[SttEngineWatchdog]
    end

    %% ── Subsystem: Typing / Fallback ───────────────────────────────
    subgraph Typing ["Typing / Fallback Subsystem"]
        FM[FallbackManager]
        SCTS[StrategyChainTypingService]
        TFE{{TypingFallbackEvent}}
        ATFE{{AllTypingFallbacksFailedEvent}}
        TEL[TypingEventsListener]
    end

    %% ── Subsystem: Error Logging ───────────────────────────────────
    subgraph ErrorLogging ["Error Logging"]
        EEL[ErrorEventsListener]
    end

    %% ── Subsystem: System Tray ─────────────────────────────────────
    subgraph Tray ["System Tray"]
        STM[SystemTrayManager]
    end

    %% ── Hotkey event wiring ────────────────────────────────────────
    HM -->|publishes| HP
    HM -->|publishes| HR
    HM -->|publishes| HPD
    HM -->|publishes| HC

    HP -.->|"@Async(eventExecutor)"| HRA
    HR -.->|"@Async(eventExecutor)"| HRA

    HPD -->|sync| EEL
    HC -->|sync| EEL

    %% ── Audio event wiring ─────────────────────────────────────────
    JSACS -->|publishes| PCC
    JSACS -->|publishes| BOE
    JSACS -->|publishes| CE

    PCC -->|sync| LCM
    PCC -.->|"@Async(eventExecutor)"| VSS

    BOE -->|sync| STM

    CE -->|sync| EEL
    CE -.->|"@Async(eventExecutor)"| HRA

    %% ── State event wiring ─────────────────────────────────────────
    AST -->|publishes| ASC

    ASC -->|sync| LCM
    ASC -->|sync| STM
    ASC -->|sync| VSS

    %% ── Transcription event wiring ─────────────────────────────────
    DTO -->|publishes| TC

    TC -.->|"@Async(eventExecutor)"| FM
    TC -->|sync| SEW

    %% ── Vosk partial result wiring ─────────────────────────────────
    VSS -->|publishes| VPR
    VPR -->|sync| LCM

    %% ── Engine watchdog wiring ─────────────────────────────────────
    EEP -->|publishes| EFE
    SEW -->|"publishes (confidence)"| EFE
    EFE -.->|"@Async(sttExecutor)"| SEW

    SEW -->|publishes| ERE
    ERE -->|sync| SEW

    %% ── Typing fallback wiring ─────────────────────────────────────
    TC -.->|"@Async(eventExecutor)"| FM
    FM -->|delegates| SCTS
    SCTS -->|publishes| TFE
    SCTS -->|publishes| ATFE

    TFE -->|sync| TEL
    ATFE -->|sync| TEL

    %% ── Styles ─────────────────────────────────────────────────────
    classDef event fill:#ffffcc,stroke:#999,stroke-width:1px
    classDef publisher fill:#ddeeff,stroke:#336,stroke-width:1px
    classDef listener fill:#e8f5e9,stroke:#2e7d32,stroke-width:1px

    class HP,HR,HPD,HC,ASC,PCC,BOE,CE,TC,VPR,EFE,ERE,TFE,ATFE event
    class HM,JSACS,AST,DTO,VSS,EEP,SCTS publisher
    class HRA,EEL,LCM,STM,SEW,FM,TEL listener
```

**Legend**

| Edge style | Threading model |
|---|---|
| Solid arrow (`-->`) with label `sync` | Synchronous -- listener runs on the publisher's thread |
| Dashed arrow (`.->`) with label `@Async(eventExecutor)` | Asynchronous -- listener runs on the `eventExecutor` thread pool |
| Inside listener body: `Platform.runLater` | JavaFX Application Thread -- noted in listener implementations (LiveCaptionManager, SystemTrayManager uses `SwingUtilities.invokeLater`) |

---

## 2. Happy Path Event Chain

This sequence diagram traces a single successful push-to-talk dictation session from hotkey press to pasted text. It includes every `ApplicationStateChangedEvent` fired at each state transition.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant HM as HotkeyManager
    participant HRA as HotkeyRecordingAdapter
    participant RS as RecordingService
    participant AST as ApplicationStateTracker
    participant JSACS as JavaSoundAudioCaptureService
    participant LCM as LiveCaptionManager
    participant VSS as VoskStreamingService
    participant STM as SystemTrayManager
    participant DTO as DefaultTranscriptionOrchestrator
    participant FM as FallbackManager
    participant SEW as SttEngineWatchdog
    participant SCTS as StrategyChainTypingService

    Note over User,SCTS: Push-to-Talk: User presses and holds hotkey

    User ->> HM: Presses hotkey
    HM ->> HM: dispatcher() matches key
    HM -->> HRA: HotkeyPressedEvent [async eventExecutor]

    activate HRA
    HRA ->> RS: startRecording()
    RS ->> AST: transitionTo(RECORDING)
    AST -->> LCM: ApplicationStateChangedEvent(IDLE→RECORDING) [sync]
    Note over LCM: Platform.runLater → show overlay window
    AST -->> STM: ApplicationStateChangedEvent(IDLE→RECORDING) [sync]
    Note over STM: SwingUtilities.invokeLater → icon turns orange
    AST -->> VSS: ApplicationStateChangedEvent(IDLE→RECORDING) [sync]
    Note over VSS: Opens Vosk streaming recognizer
    RS ->> JSACS: startSession()
    JSACS ->> JSACS: Opens TargetDataLine, starts capture thread
    deactivate HRA

    loop Every ~40ms while recording
        JSACS -->> LCM: PcmChunkCapturedEvent [sync]
        Note over LCM: Platform.runLater → updateWaveform()
        JSACS -->> VSS: PcmChunkCapturedEvent [sync]
        VSS ->> VSS: recognizer.acceptWaveForm()
        opt Vosk produces partial/final text
            VSS -->> LCM: VoskPartialResultEvent [sync]
            Note over LCM: Platform.runLater → updateCaption()
        end
    end

    Note over User,SCTS: User releases hotkey

    User ->> HM: Releases hotkey
    HM -->> HRA: HotkeyReleasedEvent [async eventExecutor]

    activate HRA
    HRA ->> RS: stopRecording()
    RS ->> JSACS: stopSession()
    JSACS ->> JSACS: Capture thread exits, audio line closed
    RS ->> JSACS: readAll() → byte[] pcm
    RS ->> AST: transitionTo(TRANSCRIBING)
    AST -->> LCM: ApplicationStateChangedEvent(RECORDING→TRANSCRIBING) [sync]
    AST -->> STM: ApplicationStateChangedEvent(RECORDING→TRANSCRIBING) [sync]
    Note over STM: SwingUtilities.invokeLater → icon turns gray
    AST -->> VSS: ApplicationStateChangedEvent(RECORDING→TRANSCRIBING) [sync]
    Note over VSS: Closes streaming recognizer

    RS ->> DTO: transcribe(pcm)
    DTO ->> DTO: Silence check, engine selection, STT execution
    DTO -->> FM: TranscriptionCompletedEvent [sync]
    DTO -->> SEW: TranscriptionCompletedEvent [sync]
    Note over SEW: Records confidence for engine health

    FM ->> SCTS: paste(text)
    SCTS ->> SCTS: Robot adapter types text
    Note over SCTS: Text delivered to active application

    RS ->> AST: transitionTo(IDLE)
    AST -->> LCM: ApplicationStateChangedEvent(TRANSCRIBING→IDLE) [sync]
    Note over LCM: Platform.runLater → hide overlay window
    AST -->> STM: ApplicationStateChangedEvent(TRANSCRIBING→IDLE) [sync]
    Note over STM: SwingUtilities.invokeLater → icon turns black
    AST -->> VSS: ApplicationStateChangedEvent(TRANSCRIBING→IDLE) [sync]
    deactivate HRA
```

---

## 3. Error Path Event Chains

This diagram shows three error scenarios that diverge from the happy path.

```mermaid
sequenceDiagram
    autonumber
    participant JSACS as JavaSoundAudioCaptureService
    participant HRA as HotkeyRecordingAdapter
    participant RS as RecordingService
    participant EEL as ErrorEventsListener
    participant DTO as DefaultTranscriptionOrchestrator
    participant EEP as EngineEventPublisher
    participant SEW as SttEngineWatchdog
    participant Engine as SttEngine
    participant SCTS as StrategyChainTypingService
    participant TEL as TypingEventsListener
    participant AST as ApplicationStateTracker
    participant FM as FallbackManager

    rect rgb(255, 235, 235)
        Note over JSACS,EEL: Scenario A: Audio Capture Error
        JSACS ->> JSACS: LineUnavailableException in doCapture()
        JSACS -->> EEL: CaptureErrorEvent("MIC_UNAVAILABLE") [sync]
        Note over EEL: Logs warning with throttle check
        JSACS -->> HRA: CaptureErrorEvent("MIC_UNAVAILABLE") [async eventExecutor]
        HRA ->> RS: cancelRecording()
        RS ->> JSACS: cancelSession() — discards buffer
        RS ->> AST: transitionTo(IDLE)
        Note over AST: Publishes ApplicationStateChangedEvent(→IDLE)
    end

    rect rgb(255, 243, 224)
        Note over DTO,Engine: Scenario B: Engine Failure During Transcription
        DTO ->> Engine: transcribe(pcm) — throws TranscriptionException
        DTO ->> DTO: Catches exception, creates failure result
        DTO -->> FM: TranscriptionCompletedEvent (result.isFailure=true) [sync]
        Note over FM: Skips paste for failed transcription
        DTO -->> SEW: TranscriptionCompletedEvent [sync]
        Note over SEW: Records low confidence

        Note over EEP,SEW: Separately, engine may report failure directly
        EEP -->> SEW: EngineFailureEvent [sync]
        SEW ->> SEW: state → DEGRADED
        SEW ->> Engine: close() then initialize()
        alt Restart succeeds
            SEW -->> SEW: EngineRecoveredEvent [sync, self-listening]
            SEW ->> SEW: state → HEALTHY, clear budgets
        else Restart fails and budget exhausted
            SEW ->> SEW: state → DISABLED, enter cooldown
            Note over SEW: If ALL engines disabled → SAFETY MODE<br/>force-enables best engine by avg confidence
        end
    end

    rect rgb(232, 245, 233)
        Note over SCTS,TEL: Scenario C: All Typing Fallbacks Fail
        FM ->> SCTS: paste(text)
        SCTS ->> SCTS: Robot adapter → type() returns false
        SCTS -->> TEL: TypingFallbackEvent("robot", "type returned false") [sync]
        Note over TEL: Logs "Typing fallback: tier=robot"
        SCTS ->> SCTS: Clipboard adapter → type() throws exception
        SCTS -->> TEL: TypingFallbackEvent("clipboard", "RuntimeException") [sync]
        Note over TEL: Logs "Typing fallback: tier=clipboard"
        SCTS ->> SCTS: Notify adapter → type() returns false
        SCTS -->> TEL: TypingFallbackEvent("notify", "type returned false") [sync]
        SCTS -->> TEL: AllTypingFallbacksFailedEvent("no adapters succeeded") [sync]
        Note over TEL: Logs "All typing fallbacks failed"
    end
```

---

## 4. Event Payload Reference Table

Every event record, its fields, publisher(s), listener(s), and threading model in one table.

| # | Event Class | Record Fields | Publisher | Listener(s) | Threading |
|---|---|---|---|---|---|
| 1 | `HotkeyPressedEvent` | `Instant at` | `HotkeyManager` | `HotkeyRecordingAdapter.onHotkeyPressed()` | `@Async("eventExecutor")` |
| 2 | `HotkeyReleasedEvent` | `Instant at` | `HotkeyManager` | `HotkeyRecordingAdapter.onHotkeyReleased()` | `@Async("eventExecutor")` |
| 3 | `HotkeyPermissionDeniedEvent` | `Instant at` | `HotkeyManager.start()` | `ErrorEventsListener.onHotkeyPermissionDenied()` | Sync |
| 4 | `HotkeyConflictEvent` | `String key`, `List<String> modifiers`, `Instant at` | `HotkeyManager.detectReservedConflict()` | `ErrorEventsListener.onHotkeyConflict()` | Sync |
| 5 | `ApplicationStateChangedEvent` | `ApplicationState previous`, `ApplicationState current`, `Instant timestamp` | `ApplicationStateTracker.transitionTo()` | `LiveCaptionManager.onStateChanged()` (conditional), `SystemTrayManager.onStateChanged()` (conditional), `VoskStreamingService.onStateChanged()` (conditional) | Sync (listeners use `Platform.runLater` / `SwingUtilities.invokeLater` internally) |
| 6 | `PcmChunkCapturedEvent` | `byte[] pcmData`, `int length`, `UUID sessionId` | `JavaSoundAudioCaptureService` (every ~40ms) | `LiveCaptionManager.onPcmChunk()` (conditional, sync), `VoskStreamingService.onPcmChunk()` (conditional, `@Async("eventExecutor")`) | Mixed (LiveCaptionManager sync with `Platform.runLater` internally; VoskStreamingService async) |
| 7 | `BufferOverflowEvent` | `int droppedBytes`, `int bufferCapacity`, `Instant timestamp` | `JavaSoundAudioCaptureService` (via `PcmRingBuffer` callback) | `SystemTrayManager.onBufferOverflow()` (conditional) | Sync (`SwingUtilities.invokeLater` internally) |
| 8 | `CaptureErrorEvent` | `String reason`, `Instant at` | `JavaSoundAudioCaptureService` | `ErrorEventsListener.onCaptureError()` (sync), `HotkeyRecordingAdapter.onCaptureError()` (`@Async("eventExecutor")`) | Mixed |
| 9 | `TranscriptionCompletedEvent` | `TranscriptionResult result`, `Instant timestamp`, `String engineUsed` | `DefaultTranscriptionOrchestrator.publishResult()` | `FallbackManager.onTranscription()` (`@Async("eventExecutor")`), `SttEngineWatchdog.onTranscriptionCompleted()` (conditional, sync) | Mixed |
| 10 | `VoskPartialResultEvent` | `String text`, `boolean isFinal` | `VoskStreamingService` (conditional) | `LiveCaptionManager.onVoskPartialResult()` (conditional) | Sync (`Platform.runLater` internally) |
| 11 | `EngineFailureEvent` | `String engine`, `Instant at`, `String message`, `Throwable cause`, `Map<String, String> context` | `EngineEventPublisher.publishFailure()` (utility), `SttEngineWatchdog.onTranscriptionCompleted()` (confidence degradation) | `SttEngineWatchdog.onFailure()` (conditional) | `@Async("sttExecutor")` |
| 12 | `EngineRecoveredEvent` | `String engine`, `Instant at` | `SttEngineWatchdog.attemptRestart()`, `SttEngineWatchdog.checkAllEnginesDisabled()` | `SttEngineWatchdog.onRecovered()` (self-listening, conditional) | Sync |
| 13 | `TypingFallbackEvent` | `String tier`, `String reason`, `Instant at` | `StrategyChainTypingService.paste()` | `TypingEventsListener.onFallback()` | Sync |
| 14 | `AllTypingFallbacksFailedEvent` | `String reason`, `Instant at` | `StrategyChainTypingService.paste()` | `TypingEventsListener.onAllFailed()` | Sync |
| 15 | `EngineHealthChangedEvent` | `String engine`, `EngineState previousState`, `EngineState currentState`, `Instant timestamp` | `SttEngineWatchdog.updateState()` | `SystemTrayManager.onEngineHealthChanged()` | Sync |

**Conditional beans:** `LiveCaptionManager`, `VoskStreamingService` are active only when `live-caption.enabled=true`. `SystemTrayManager` is active when `tray.enabled=true` (default). `SttEngineWatchdog` is active when `stt.watchdog.enabled=true` (default).

---

## 5. Live Caption Event Sub-flow

This focused diagram isolates the live caption pipeline: PCM audio chunks flow from the capture service through Vosk streaming recognition, producing partial text results that are rendered in the JavaFX overlay window.

```mermaid
sequenceDiagram
    autonumber
    participant AST as ApplicationStateTracker
    participant JSACS as JavaSoundAudioCaptureService
    participant VSS as VoskStreamingService
    participant LCM as LiveCaptionManager
    participant FX as JavaFX App Thread<br/>(Platform.runLater)
    participant LCW as LiveCaptionWindow

    Note over AST,LCW: Precondition: live-caption.enabled=true

    AST -->> VSS: ApplicationStateChangedEvent(→RECORDING) [sync]
    VSS ->> VSS: openRecognizer() — creates Vosk Recognizer

    AST -->> LCM: ApplicationStateChangedEvent(→RECORDING) [sync]
    LCM ->> FX: Platform.runLater(showWindow)
    FX ->> LCW: new LiveCaptionWindow(props) + show()

    loop Every ~40ms during RECORDING state
        JSACS -->> VSS: PcmChunkCapturedEvent [sync]
        VSS ->> VSS: recognizer.acceptWaveForm(pcmData)

        JSACS -->> LCM: PcmChunkCapturedEvent [sync]
        LCM ->> FX: Platform.runLater(updateWaveform)
        FX ->> LCW: updateWaveform(samples)

        alt Vosk produces partial result
            VSS -->> LCM: VoskPartialResultEvent(text, isFinal=false) [sync]
            LCM ->> FX: Platform.runLater(updateCaption)
            FX ->> LCW: updateCaption(text, false)
        else Vosk produces final result
            VSS -->> LCM: VoskPartialResultEvent(text, isFinal=true) [sync]
            LCM ->> FX: Platform.runLater(updateCaption)
            FX ->> LCW: updateCaption(text, true)
        end
    end

    AST -->> VSS: ApplicationStateChangedEvent(→TRANSCRIBING) [sync]
    VSS ->> VSS: closeRecognizer()

    AST -->> LCM: ApplicationStateChangedEvent(→IDLE) [sync]
    LCM ->> FX: Platform.runLater(hide)
    FX ->> LCW: hide()
```

### Key threading observations for the live caption path

1. **PcmChunkCapturedEvent** is published on the `audio-capture` daemon thread and delivered synchronously to both `VoskStreamingService` and `LiveCaptionManager`. The Vosk recognizer processes audio under its own `recognizerLock` to avoid contention.

2. **VoskPartialResultEvent** is published on the same `audio-capture` thread (inside the `onPcmChunk` handler) and delivered synchronously to `LiveCaptionManager`.

3. **All UI updates** (waveform, caption text, window show/hide) are marshalled to the JavaFX Application Thread via `Platform.runLater`, ensuring thread safety for the overlay window.

4. Both `VoskStreamingService` and `LiveCaptionManager` are conditional beans gated on `live-caption.enabled=true`. When disabled, neither bean is instantiated and no live caption events are published or consumed.
