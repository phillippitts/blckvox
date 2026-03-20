# Exception Hierarchy and Error Recovery

This document describes the exception hierarchy, throw/catch relationships, and error recovery patterns in the blckvox application.

---

## 1. Exception Hierarchy Tree

```mermaid
classDiagram
    direction TB

    class RuntimeException {
        <<Java Standard Library>>
    }

    class BlckvoxException {
        <<exception/BlckvoxException.java>>
        +String message
        +Throwable cause
    }

    class TranscriptionException {
        <<exception/TranscriptionException.java>>
        +String engineName
        Built via TranscriptionExceptionBuilder
    }

    class InvalidAudioException {
        <<exception/InvalidAudioException.java>>
        Formats audioSize and reason
        directly into the exception message
    }

    class ModelNotFoundException {
        <<exception/ModelNotFoundException.java>>
        +String modelPath
    }

    class InvalidStateTransitionException {
        <<exception/InvalidStateTransitionException.java>>
        +String fromState
        +String toState
    }

    RuntimeException <|-- BlckvoxException
    BlckvoxException <|-- TranscriptionException
    BlckvoxException <|-- InvalidAudioException
    BlckvoxException <|-- ModelNotFoundException
    BlckvoxException <|-- InvalidStateTransitionException
```

---

## 2. Exception Throw and Catch Map

```mermaid
flowchart TB
    subgraph THROWN_BY["Exception Sources"]
        direction TB
        Vosk["VoskSttEngine<br/>(JNI errors)"]
        Whisper["WhisperSttEngine<br/>(process timeout / exit code)"]
        ParallelStt["DefaultParallelSttService<br/>(both engines fail)"]
        AudioVal["AudioValidator<br/>(duration too short/long,<br/>wrong format)"]
        ModelVal["ModelValidationService<br/>(@PostConstruct)"]
        StateTracker["ApplicationStateTracker<br/>.transitionTo()"]
    end

    subgraph EXCEPTIONS["Exceptions"]
        direction TB
        TE["TranscriptionException"]
        IAE["InvalidAudioException"]
        MNFE["ModelNotFoundException"]
        ISTE["InvalidStateTransitionException"]
    end

    subgraph CAUGHT_BY["Exception Handlers"]
        direction TB
        Orch["DefaultTranscriptionOrchestrator<br/>&#8594; publishes TranscriptionCompletedEvent<br/>with failure flag"]
        EngPub["EngineEventPublisher<br/>(static utility, not a Spring bean)<br/>&#8594; publishes EngineFailureEvent<br/>&#8594; watchdog evaluates restart"]
        Recon["DefaultReconciliationService<br/>&#8594; falls back to single<br/>engine result"]
        CapOrch["DefaultCaptureOrchestrator<br/>&#8594; logs warning,<br/>returns empty result,<br/>state &#8594; IDLE"]
        Spring["Spring Context<br/>&#8594; FAIL-FAST,<br/>application will not start"]
        RecSvc["DefaultRecordingService<br/>&#8594; logs error,<br/>forces state to IDLE"]
    end

    Vosk -->|throws| TE
    Whisper -->|throws| TE
    ParallelStt -->|throws| TE
    AudioVal -->|throws| IAE
    ModelVal -->|throws| MNFE
    StateTracker -->|throws| ISTE

    TE -->|caught by| Orch
    TE -->|caught by| EngPub
    TE -->|caught by| Recon
    IAE -->|caught by| CapOrch
    MNFE -->|caught by| Spring
    ISTE -->|caught by| RecSvc
```

---

## 3. Engine Failure Recovery Sequence

```mermaid
sequenceDiagram
    participant Engine as SttEngine<br/>(Vosk / Whisper)
    participant Publisher as EngineEventPublisher<br/>(static utility class)
    participant Bus as Spring Event Bus
    participant Watchdog as SttEngineWatchdog
    participant Window as Sliding Window<br/>(3 per 60 min)
    participant Recovered as EngineRecoveredEvent

    Engine->>Engine: Transcription attempt fails
    Engine-->>Publisher: throws TranscriptionException<br/>(engineName)

    Publisher->>Publisher: Catches TranscriptionException
    Publisher->>Bus: publishes EngineFailureEvent

    Bus->>Watchdog: delivers EngineFailureEvent

    Watchdog->>Window: Record failure timestamp

    alt Budget available AND cooldown elapsed
        Window-->>Watchdog: Failures < 3 in last 60 min
        Watchdog->>Engine: Restart engine
        Engine-->>Watchdog: Engine restarted successfully
        Watchdog->>Bus: publishes EngineRecoveredEvent
        Bus->>Recovered: Downstream listeners notified
    else Budget exhausted OR cooldown not elapsed
        Window-->>Watchdog: Failures >= 3 in last 60 min<br/>OR cooldown still active
        Watchdog->>Watchdog: Skip restart, log warning
        Note over Watchdog: Engine remains unavailable<br/>until sliding window clears
    end
```

---

## 4. Typing Fallback Cascade

```mermaid
sequenceDiagram
    participant Caller as TypingService
    participant Robot as RobotAdapter
    participant Clipboard as ClipboardAdapter
    participant Notify as NotifyOnlyAdapter
    participant Bus as Spring Event Bus
    participant Listener as TypingEventsListener

    Caller->>Robot: type(text)

    alt Robot succeeds
        Robot-->>Caller: Text typed via AWT Robot
    else Robot fails (no Accessibility permission)
        Robot-->>Caller: throws Exception
        Caller->>Clipboard: type(text)

        alt Clipboard succeeds
            Clipboard-->>Caller: Text pasted via clipboard
        else Clipboard fails
            Clipboard-->>Caller: throws Exception
            Caller->>Notify: type(text)

            alt NotifyOnly succeeds
                Notify-->>Caller: User notified (no typing)
            else NotifyOnly fails
                Notify-->>Caller: throws Exception
                Caller->>Bus: publishes AllTypingFallbacksFailedEvent
                Bus->>Listener: delivers AllTypingFallbacksFailedEvent
                Listener->>Listener: Logs error:<br/>all typing methods exhausted
            end
        end
    end
```

---

## 5. Capture Error and Audio Validation Flows

```mermaid
sequenceDiagram
    participant Mic as JavaSoundAudioCaptureService
    participant Bus as Spring Event Bus
    participant Hotkey as HotkeyRecordingAdapter
    participant RecSvc as DefaultRecordingService
    participant State as ApplicationStateTracker

    Note over Mic: IOException during mic read
    Mic->>Bus: publishes CaptureErrorEvent
    Bus->>Hotkey: delivers CaptureErrorEvent
    Hotkey->>RecSvc: cancelRecording()
    RecSvc->>State: transitionTo(IDLE)
    Note over State: State forced to IDLE,<br/>recording cancelled cleanly
```

```mermaid
sequenceDiagram
    participant User as User
    participant Hotkey as HotkeyRecordingAdapter
    participant CapOrch as DefaultCaptureOrchestrator
    participant Validator as AudioValidator
    participant State as ApplicationStateTracker

    User->>Hotkey: Taps hotkey briefly
    Hotkey->>CapOrch: processAudio(audioData)
    CapOrch->>Validator: validate(audioData)
    Validator-->>CapOrch: throws InvalidAudioException<br/>(duration < 250ms)
    CapOrch->>CapOrch: Catches InvalidAudioException<br/>Logs "audio too short"
    CapOrch->>State: state returns to IDLE
    Note over CapOrch: Returns empty result,<br/>no typing output
```

---

## 6. Invalid State Transition Recovery

```mermaid
sequenceDiagram
    participant Caller as DefaultRecordingService
    participant Tracker as ApplicationStateTracker
    participant State as Application State

    Caller->>Tracker: transitionTo(TRANSCRIBING)<br/>while currently IDLE
    Note over Tracker: Invalid: IDLE cannot<br/>transition directly<br/>to TRANSCRIBING
    Tracker-->>Caller: throws InvalidStateTransitionException<br/>(fromState=IDLE, toState=TRANSCRIBING)
    Caller->>Caller: Catches exception, logs error
    Caller->>Tracker: Force state to IDLE
    Tracker->>State: State = IDLE
    Note over Caller: System reset to safe state
```

---

## 7. Error Recovery Summary Table

| Error Scenario | Exception | Thrown By | Caught By | Recovery Action | User Impact |
|---|---|---|---|---|---|
| Vosk JNI error | `TranscriptionException` | `VoskSttEngine` | `EngineEventPublisher` | Publishes `EngineFailureEvent`; watchdog evaluates restart budget (3 per 60 min); restarts engine if budget available | Momentary delay; next transcription uses restarted engine or surviving engine |
| Whisper process timeout or bad exit code | `TranscriptionException` | `WhisperSttEngine` | `EngineEventPublisher` | Same watchdog restart flow as above | Same as above |
| Both engines fail | `TranscriptionException` | `DefaultParallelSttService` | `DefaultTranscriptionOrchestrator` | Publishes `TranscriptionCompletedEvent` with failure flag; `FallbackManager` skips typing | No text typed; user must retry |
| Single engine fails during reconciliation | `TranscriptionException` | One of `VoskSttEngine` / `WhisperSttEngine` | `DefaultReconciliationService` | Uses the successful engine's result only | Slightly reduced accuracy (single-engine result instead of reconciled) |
| Audio too short (< 250ms) | `InvalidAudioException` | `AudioValidator` | `DefaultCaptureOrchestrator` | Logs warning; returns empty result; state returns to IDLE | No text typed; user should hold hotkey longer |
| Audio wrong format or too long | `InvalidAudioException` | `AudioValidator` | `DefaultCaptureOrchestrator` | Same as audio-too-short recovery | No text typed; check audio input settings |
| STT model file missing at startup | `ModelNotFoundException` | `ModelValidationService` | Spring Context | Application fails to start (fail-fast) | App does not launch; user must install models |
| Invalid state transition (e.g., double start) | `InvalidStateTransitionException` | `ApplicationStateTracker` | `DefaultRecordingService` | Forces state to IDLE; logs error | Current operation cancelled; user can retry immediately |
| Mic read IOException during capture | `IOException` (not a `BlckvoxException`) | `JavaSoundAudioCaptureService` | `HotkeyRecordingAdapter` (via `CaptureErrorEvent`) | Calls `cancelRecording()`; state returns to IDLE | Recording lost; user must retry |
| All typing fallbacks fail | Various exceptions | `RobotAdapter`, `ClipboardAdapter`, `NotifyOnlyAdapter` | Typing service publishes `AllTypingFallbacksFailedEvent` | `TypingEventsListener` logs error | Transcription succeeded but text cannot be typed; check Accessibility permissions |
