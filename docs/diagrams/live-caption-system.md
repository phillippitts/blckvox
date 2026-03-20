# Live Caption System

Real-time oscilloscope waveform and streaming Vosk captions displayed in a JavaFX overlay window during recording.

## Architecture Overview

```mermaid
graph TB
    subgraph Capture["Audio Capture (existing)"]
        Mic[Microphone] --> JSACS[JavaSoundAudioCaptureService]
        JSACS --> Buffer[PcmRingBuffer]
    end

    subgraph Events["Spring Event Bus"]
        PCMEvent[PcmChunkCapturedEvent]
        StateEvent[ApplicationStateChangedEvent]
        PartialEvent[VoskPartialResultEvent]
    end

    subgraph Streaming["Vosk Streaming (new)"]
        VSS[VoskStreamingService]
    end

    subgraph LiveCaption["Live Caption UI (new)"]
        LCM[LiveCaptionManager]
        LCW[LiveCaptionWindow]
        Canvas[Canvas<br/>Oscilloscope]
        Label[Label<br/>Caption Text]
    end

    subgraph Tray["System Tray (modified)"]
        STM[SystemTrayManager]
        Checkbox[CheckboxMenuItem<br/>Live Caption]
    end

    JSACS -->|publish| PCMEvent
    PCMEvent --> VSS
    PCMEvent --> LCM

    StateEvent --> VSS
    StateEvent --> LCM

    VSS -->|publish| PartialEvent
    PartialEvent --> LCM

    LCM -->|Platform.runLater| LCW
    LCW --> Canvas
    LCW --> Label

    Checkbox -->|toggle| LCM

    style Capture fill:#e1f5ff
    style Events fill:#fff9c4
    style Streaming fill:#fff4e1
    style LiveCaption fill:#c8e6c9
    style Tray fill:#f3e5f5
```

## Event Flow Sequence

```mermaid
sequenceDiagram
    actor User
    participant Tray as SystemTrayManager
    participant LCM as LiveCaptionManager
    participant JSACS as JavaSoundAudioCaptureService
    participant VSS as VoskStreamingService
    participant LCW as LiveCaptionWindow

    Note over User,LCW: Setup: User enables Live Caption via tray menu

    User->>Tray: Check "Live Caption"
    Tray->>LCM: setEnabled(true)

    Note over User,LCW: Recording begins

    User->>User: Press hotkey
    activate JSACS
    Note over JSACS: ApplicationStateChangedEvent(RECORDING)

    LCM->>LCW: show() (Platform.runLater)
    VSS->>VSS: openRecognizer()

    loop Every 40ms chunk
        JSACS->>JSACS: Capture PCM chunk
        JSACS-->>LCM: PcmChunkCapturedEvent
        JSACS-->>VSS: PcmChunkCapturedEvent

        LCM->>LCW: updateWaveform(samples)
        VSS->>VSS: recognizer.acceptWaveForm()

        alt Partial result
            VSS-->>LCM: VoskPartialResultEvent(text, false)
            LCM->>LCW: updateCaption(text, false)
            Note over LCW: Gray text
        else Final result
            VSS-->>LCM: VoskPartialResultEvent(text, true)
            LCM->>LCW: updateCaption(text, true)
            Note over LCW: White text
        end
    end

    User->>User: Release hotkey
    deactivate JSACS
    Note over JSACS: ApplicationStateChangedEvent(IDLE)

    VSS->>VSS: closeRecognizer()
    LCM->>LCW: hide()
```

## Component Data Flow

```mermaid
flowchart LR
    subgraph Input["Audio Input"]
        Mic[Microphone<br/>16kHz PCM]
    end

    subgraph Capture["Capture Service"]
        Buf[byte[] buf]
        Copy[Defensive copy<br/>new byte[n]]
    end

    subgraph EventBus["Spring Events"]
        PCM[PcmChunkCapturedEvent<br/>byte[] pcmData<br/>int length<br/>UUID sessionId]
    end

    subgraph WaveformPath["Waveform Path"]
        Conv[PcmSampleConverter<br/>toSamples]
        Samples[short[] samples]
    end

    subgraph CaptionPath["Caption Path"]
        Vosk[VoskStreamingService<br/>acceptWaveForm]
        Partial[VoskPartialResultEvent<br/>String text<br/>boolean isFinal]
    end

    subgraph Display["JavaFX Window"]
        Canvas[Canvas<br/>Oscilloscope line]
        Label[Label<br/>Caption text]
    end

    Mic --> Buf
    Buf --> Copy
    Copy --> PCM

    PCM --> Conv
    Conv --> Samples
    Samples -->|Platform.runLater| Canvas

    PCM --> Vosk
    Vosk --> Partial
    Partial -->|Platform.runLater| Label

    style Input fill:#e1f5ff
    style Capture fill:#fff4e1
    style EventBus fill:#fff9c4
    style WaveformPath fill:#c8e6c9
    style CaptionPath fill:#c8e6c9
    style Display fill:#f3e5f5
```

## Class Diagram

```mermaid
classDiagram
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

    class VoskStreamingService {
        -VoskConfig config
        -ApplicationEventPublisher publisher
        -Object recognizerLock
        -VoskModelProvider modelProvider
        -Recognizer recognizer
        +onStateChanged(ApplicationStateChangedEvent)
        +onPcmChunk(PcmChunkCapturedEvent)
        -openRecognizer()
        -closeRecognizer()
        -parseTextField(String, String) String
    }

    class LiveCaptionProperties {
        -boolean enabled
        -int windowWidth
        -int windowHeight
        -double windowOpacity
        +isEnabled() boolean
        +getWindowWidth() int
        +getWindowHeight() int
        +getWindowOpacity() double
    }

    class JavaFxLifecycle {
        -AtomicBoolean running
        +start()
        +stop()
        +isRunning() boolean
        +getPhase() int
    }

    class PcmSampleConverter {
        +toSamples(byte[], int) short[]$
    }

    class LiveCaptionWindow {
        -Stage stage
        -Canvas canvas
        -Label captionLabel
        -double canvasWidth
        +show()
        +hide()
        +updateWaveform(short[])
        +updateCaption(String, boolean)
    }

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

    LiveCaptionManager --> LiveCaptionWindow : creates lazily
    LiveCaptionManager --> LiveCaptionProperties : reads config
    LiveCaptionManager --> PcmSampleConverter : converts PCM
    LiveCaptionManager ..> PcmChunkCapturedEvent : listens
    LiveCaptionManager ..> VoskPartialResultEvent : listens
    VoskStreamingService ..> PcmChunkCapturedEvent : listens
    VoskStreamingService ..> VoskPartialResultEvent : publishes
    JavaFxLifecycle ..|> SmartLifecycle : implements
```

## State Machine: Live Caption Visibility

```mermaid
stateDiagram-v2
    [*] --> Hidden

    Hidden --> Visible : RECORDING + enabled
    Visible --> Hidden : IDLE
    Visible --> Hidden : setEnabled(false)
    Hidden --> Hidden : IDLE (no-op)
    Hidden --> Hidden : RECORDING + disabled

    state Visible {
        [*] --> Flat
        Flat --> Waveform : PcmChunkCapturedEvent
        Waveform --> Waveform : PcmChunkCapturedEvent
        Waveform --> PartialCaption : VoskPartialResultEvent(false)
        PartialCaption --> PartialCaption : VoskPartialResultEvent(false)
        PartialCaption --> FinalCaption : VoskPartialResultEvent(true)
        FinalCaption --> PartialCaption : VoskPartialResultEvent(false)
    }

    note right of Hidden
        Window not shown.
        No CPU/GPU cost.
    end note

    note right of Visible
        Oscilloscope + captions
        updating in real-time.
    end note
```

## Thread Model

```mermaid
graph TB
    subgraph AudioThread["Audio Capture Thread"]
        Capture[doCapture loop]
        Publish[publishEvent<br/>PcmChunkCapturedEvent]
    end

    subgraph SpringThread["Spring Event Thread"]
        VoskListener[VoskStreamingService<br/>onPcmChunk]
        LCMListener[LiveCaptionManager<br/>onPcmChunk / onPartial]
    end

    subgraph JavaFXThread["JavaFX Application Thread"]
        UpdateWave[updateWaveform]
        UpdateCap[updateCaption]
        ShowHide[show / hide]
    end

    Capture --> Publish
    Publish --> VoskListener
    Publish --> LCMListener
    VoskListener -->|publishEvent| LCMListener

    LCMListener -->|Platform.runLater| UpdateWave
    LCMListener -->|Platform.runLater| UpdateCap
    LCMListener -->|Platform.runLater| ShowHide

    style AudioThread fill:#ffe0b2
    style SpringThread fill:#e1f5ff
    style JavaFXThread fill:#c8e6c9
```

## Feature Toggle: Conditional Bean Loading

```mermaid
flowchart TD
    Prop{live-caption.enabled<br/>= true?}

    Prop -->|Yes| Load[Spring creates beans]
    Prop -->|No| Skip[No beans created<br/>Zero overhead]

    Load --> Bean1[JavaFxLifecycle]
    Load --> Bean2[VoskStreamingService]
    Load --> Bean3[LiveCaptionManager]

    Bean1 -->|SmartLifecycle| JFX[JavaFX Platform.startup]
    Bean3 -->|Optional injection| Tray[SystemTrayManager<br/>shows checkbox]

    Skip --> NoTray[SystemTrayManager<br/>no checkbox]

    style Load fill:#c8e6c9
    style Skip fill:#fff4e1
    style Prop fill:#e1f5ff
```

## PCM Sample Conversion

```mermaid
flowchart LR
    subgraph Input["PCM Bytes (little-endian)"]
        B0["byte[0]: low"]
        B1["byte[1]: high"]
        B2["byte[2]: low"]
        B3["byte[3]: high"]
    end

    subgraph Convert["PcmSampleConverter.toSamples()"]
        Op1["(byte[1] << 8) | (byte[0] & 0xFF)"]
        Op2["(byte[3] << 8) | (byte[2] & 0xFF)"]
    end

    subgraph Output["short[] samples"]
        S0["sample[0]: signed 16-bit"]
        S1["sample[1]: signed 16-bit"]
    end

    B0 --> Op1
    B1 --> Op1
    B2 --> Op2
    B3 --> Op2

    Op1 --> S0
    Op2 --> S1

    style Input fill:#e1f5ff
    style Convert fill:#fff4e1
    style Output fill:#c8e6c9
```

## Window Layout

```
+------------------------------------------+
|  Live Caption Window (600x250)           |
|  ┌──────────────────────────────────┐    |
|  │  Canvas (580x100)                │    |
|  │                                  │    |
|  │  ~~~~~/\~~~~~/\~~/\~~~~~~        │    |
|  │  ─────────────────────── midline │    |
|  │  ~~~~~\/~~~~~\/~~\/~~~~~~        │    |
|  │                                  │    |
|  └──────────────────────────────────┘    |
|                                          |
|  "the quick brown fox jumps over the..." |
|  (white = final, gray = partial)         |
|                                          |
+------------------------------------------+
  ↑ Positioned bottom-center of screen
  ↑ Always-on-top, transparent background
  ↑ Rounded corners, 85% opacity
```
