# blckvox Data Flow Diagram

## Complete Transcription Pipeline

```mermaid
sequenceDiagram
    actor User
    participant HK as HotkeyManager
    participant Orch as RecordingService
    participant Audio as AudioCaptureService
    participant Buffer as PcmRingBuffer
    participant PSS as ParallelSttService
    participant Vosk as VoskSttEngine
    participant Whisper as WhisperSttEngine
    participant Rec as TranscriptReconciler
    participant FB as FallbackManager
    participant Type as TypingService
    
    User->>HK: Press Hotkey
    HK->>Orch: HotkeyPressedEvent
    Orch->>Buffer: Clear buffer
    Orch->>Audio: Start capture
    
    loop While hotkey held
        Audio->>Buffer: Append audio chunks
    end
    
    User->>HK: Release Hotkey
    HK->>Orch: HotkeyReleasedEvent
    Orch->>Audio: Stop capture
    Orch->>Buffer: Get snapshot
    Buffer-->>Orch: Audio bytes
    
    Orch->>PSS: transcribeBoth(audio, 120000ms)
    
    par Parallel Execution
        PSS->>Vosk: Thread 1: transcribe(audio)
        Note over Vosk: 100ms latency
        Vosk-->>PSS: EngineResult(text, 100ms)
    and
        PSS->>Whisper: Thread 2: transcribe(audio)
        Note over Whisper: 1-2s latency
        Whisper-->>PSS: EngineResult(text, 1500ms)
    end
    
    PSS->>Rec: reconcile(voskResult, whisperResult)
    Note over Rec: Strategy: Simple/Confidence/Overlap
    Rec-->>PSS: TranscriptionResult(text, engine, reason)
    PSS-->>Orch: TranscriptionResult
    
    Orch-->>Orch: publish TranscriptionCompletedEvent
    Note over FB: FallbackManager listens to<br/>TranscriptionCompletedEvent

    alt Accessibility Permission Granted
        FB->>Type: Robot → Cmd+V
        Type-->>User: Text pasted
    else Robot Failed
        FB->>Type: Clipboard fallback
        FB-->>User: Notification: Manual paste required
    else Clipboard Failed
        FB-->>User: Toast notification with text
    end
```

## State Machine: Dictation Session

```mermaid
stateDiagram-v2
    [*] --> Idle
    
    Idle --> Recording : Hotkey Down
    Recording --> Transcribing : Hotkey Up
    Transcribing --> Idle : Transcription Complete + Paste

    Recording --> Idle : Error/Cancel
    Transcribing --> Idle : Timeout/Error

    note right of Recording
        Audio captured to buffer
        Visual feedback active
    end note

    note right of Transcribing
        Dual-engine processing
        Result pasted via 3-tier fallback
        (Robot → Clipboard → Notification)
    end note
```

## Parallel Processing Timeline

```mermaid
gantt
    title Dual-Engine Transcription Timeline
    dateFormat X
    axisFormat %L ms
    
    section User Action
    Press hotkey           :0, 0
    Speak (2 seconds)      :0, 2000
    Release hotkey         :2000, 2000
    
    section Audio
    Capture audio          :0, 2000
    Buffer snapshot        :2000, 50
    
    section STT Processing
    Vosk transcribe        :2050, 100
    Whisper transcribe     :2050, 1500
    Wait for both          :2050, 1500
    
    section Reconciliation
    Reconcile results      :3550, 50
    
    section Output
    Paste text             :3600, 100
    
    section Total Latency
    User wait time         :milestone, 3700, 0
```

## Error Handling Flow

```mermaid
flowchart TD
    Start([Hotkey Released]) --> Validate{Audio Valid?}
    
    Validate -->|No| ErrAudio[InvalidAudioException]
    ErrAudio --> Notify1[Show error notification]
    Notify1 --> End1([End])
    
    Validate -->|Yes| Parallel[Start Parallel STT]
    
    Parallel --> VoskOK{Vosk Success?}
    Parallel --> WhisperOK{Whisper Success?}
    
    VoskOK -->|Yes| VoskRes[Vosk Result]
    VoskOK -->|No| VoskFail[Vosk Failed]
    
    WhisperOK -->|Yes| WhisperRes[Whisper Result]
    WhisperOK -->|No| WhisperFail[Whisper Failed]
    
    VoskRes --> Both{Both OK?}
    WhisperRes --> Both
    VoskFail --> OnlyWhisper{Only Whisper OK?}
    WhisperFail --> OnlyVosk{Only Vosk OK?}
    
    Both -->|Yes| Reconcile[Reconcile Results]
    OnlyWhisper -->|Yes| UseWhisper[Use Whisper Only]
    OnlyVosk -->|Yes| UseVosk[Use Vosk Only]
    
    OnlyWhisper -->|No| BothFailed
    OnlyVosk -->|No| BothFailed
    
    BothFailed[Both Failed] --> ErrSTT[TranscriptionException]
    ErrSTT --> Notify2[Show error]
    Notify2 --> End2([End])
    
    Reconcile --> Paste[Attempt Paste]
    UseWhisper --> Paste
    UseVosk --> Paste
    
    Paste --> PasteOK{Paste Success?}
    
    PasteOK -->|Yes| Success([Success])
    PasteOK -->|No| Fallback[Try Fallback]
    
    Fallback --> Clipboard{Clipboard OK?}
    Clipboard -->|Yes| NotifyManual[Notify: Manual Paste]
    Clipboard -->|No| ToastText[Toast Notification]
    
    NotifyManual --> End3([End])
    ToastText --> End3
    
    style Start fill:#e1f5ff
    style Success fill:#c8e6c9
    style ErrAudio fill:#ffcdd2
    style ErrSTT fill:#ffcdd2
    style BothFailed fill:#ffcdd2
    style Reconcile fill:#fff9c4
```

## Live Caption Data Flow (During Recording)

```mermaid
sequenceDiagram
    participant Mic as Microphone
    participant JSACS as JavaSoundAudioCaptureService
    participant VSS as VoskStreamingService
    participant LCM as LiveCaptionManager
    participant JFX as JavaFX Thread
    participant LCW as LiveCaptionWindow

    Note over Mic,LCW: Every 40ms during recording

    Mic->>JSACS: PCM chunk (640 bytes)
    JSACS->>JSACS: buffer.write(buf, 0, n)
    JSACS->>JSACS: Defensive copy: new byte[n]

    par Waveform Path
        JSACS-->>LCM: PcmChunkCapturedEvent
        LCM->>LCM: PcmSampleConverter.toSamples()
        LCM->>JFX: Platform.runLater(updateWaveform)
        JFX->>LCW: Draw oscilloscope line
    and Caption Path
        JSACS-->>VSS: PcmChunkCapturedEvent
        VSS->>VSS: recognizer.acceptWaveForm()
        alt Partial result
            VSS-->>LCM: VoskPartialResultEvent(text, false)
            LCM->>JFX: Platform.runLater(updateCaption)
            JFX->>LCW: Set gray text
        else Final result
            VSS-->>LCM: VoskPartialResultEvent(text, true)
            LCM->>JFX: Platform.runLater(updateCaption)
            JFX->>LCW: Set white text
        end
    end
```

## Audio Format Validation Pipeline

```mermaid
flowchart LR
    Input[Audio Input] --> Check1{Size Check}
    
    Check1 -->|< 1KB| Reject1[Too Short]
    Check1 -->|> 10MB| Reject2[Too Large]
    Check1 -->|Valid| Check2{WAV Header}
    
    Check2 -->|Missing RIFF| Reject3[Invalid Format]
    Check2 -->|Missing WAVE| Reject4[Invalid Format]
    Check2 -->|Valid| Check3{Sample Rate}
    
    Check3 -->|!= 16kHz| Reject5[Wrong Sample Rate<br/>Expected: 16000 Hz]
    Check3 -->|16kHz| Check4{Channels}
    
    Check4 -->|!= 1| Reject6[Not Mono<br/>Expected: 1 channel]
    Check4 -->|Mono| Check5{Bit Depth}
    
    Check5 -->|!= 16-bit| Reject7[Wrong Bit Depth<br/>Expected: 16-bit PCM]
    Check5 -->|16-bit| Accept[Valid Audio<br/>Ready for STT]
    
    Reject1 --> End([Throw InvalidAudioException])
    Reject2 --> End
    Reject3 --> End
    Reject4 --> End
    Reject5 --> End
    Reject6 --> End
    Reject7 --> End
    
    Accept --> Process[Send to STT Engines]
    
    style Accept fill:#c8e6c9
    style Reject1 fill:#ffcdd2
    style Reject2 fill:#ffcdd2
    style Reject3 fill:#ffcdd2
    style Reject4 fill:#ffcdd2
    style Reject5 fill:#ffcdd2
    style Reject6 fill:#ffcdd2
    style Reject7 fill:#ffcdd2
```
