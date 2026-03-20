# Thread Model and Concurrency

## Thread Pool Architecture

```mermaid
graph TB
    subgraph MainThread["Main Thread"]
        Spring[Spring Boot Main]
        EventBus[ApplicationEventPublisher]
    end

    subgraph HotkeyThread["JNativeHook Thread"]
        NativeHook[JNativeHookGlobalKeyHook]
        KeyListener[NativeKeyListener]
    end

    subgraph EventExecutor["eventExecutor Thread Pool"]
        direction LR
        EventWorker1[Event Worker 1<br/>HotkeyRecordingAdapter]
        EventWorker2[Event Worker 2<br/>VoskStreamingService]
    end

    subgraph SttExecutor["sttExecutor Thread Pool"]
        direction LR
        Thread1[Worker Thread 1<br/>Vosk Engine]
        Thread2[Worker Thread 2<br/>Whisper Engine]
    end

    subgraph AsyncLogging["Async Logging Threads"]
        LogThread1[AsyncAppender Thread 1]
        LogThread2[AsyncAppender Thread 2]
    end

    subgraph AudioThread["Audio Capture Thread"]
        JavaSound[Java Sound TargetDataLine]
        RingBuffer[PcmRingBuffer]
        PcmEvent[PcmChunkCapturedEvent]
    end

    subgraph JavaFXThread["JavaFX Application Thread"]
        LiveWindow[LiveCaptionWindow]
        Waveform[Canvas: Oscilloscope]
        Captions[Label: Caption Text]
    end

    HotkeyThread -->|HotkeyPressed Event| EventBus
    EventBus -->|@Async eventExecutor| EventExecutor
    EventExecutor -->|submit to sttExecutor| SttExecutor
    AudioThread -->|Write PCM| RingBuffer
    MainThread -->|Read PCM| RingBuffer
    AudioThread -->|Publish| PcmEvent
    PcmEvent -->|@Async eventExecutor| EventExecutor
    PcmEvent -->|Platform.runLater| JavaFXThread

    Thread1 -.->|Log Events| AsyncLogging
    Thread2 -.->|Log Events| AsyncLogging

    style MainThread fill:#e1f5ff
    style HotkeyThread fill:#fff4e1
    style EventExecutor fill:#fff4e1
    style SttExecutor fill:#c8e6c9
    style AsyncLogging fill:#f3e5f5
    style AudioThread fill:#ffe0b2
    style JavaFXThread fill:#c8e6c9
```

## Parallel STT Execution Flow

```mermaid
sequenceDiagram
    participant Main as Main Thread
    participant Executor as sttExecutor Pool
    participant VoskWorker as Vosk Worker Thread
    participant WhisperWorker as Whisper Worker Thread
    participant Lock as Synchronized Lock

    Main->>Main: transcribeBoth(pcm)
    Main->>Executor: submit(voskTask)
    Main->>Executor: submit(whisperTask)

    par Parallel Execution
        Executor->>VoskWorker: run voskTask
        VoskWorker->>VoskWorker: acquire semaphore (max 4)
        VoskWorker->>Lock: lock.lock()
        Lock-->>VoskWorker: grant lock
        VoskWorker->>VoskWorker: getModelForTranscription()
        VoskWorker->>Lock: lock.unlock()
        VoskWorker->>VoskWorker: transcribe (100ms)
        VoskWorker->>VoskWorker: release semaphore
        VoskWorker-->>Main: EngineResult (Vosk)
    and
        Executor->>WhisperWorker: run whisperTask
        WhisperWorker->>WhisperWorker: acquire semaphore (max 2)
        WhisperWorker->>WhisperWorker: create temp WAV
        WhisperWorker->>WhisperWorker: spawn process (1-2s)
        WhisperWorker->>WhisperWorker: parse stdout result
        WhisperWorker->>WhisperWorker: release semaphore
        WhisperWorker-->>Main: EngineResult (Whisper)
    end

    Main->>Main: allOf.get(timeoutMs)
    Main->>Main: reconcile results
```

## MDC Propagation Across Threads

```mermaid
flowchart TB
    Event[Spring Event<br/>e.g. HotkeyPressedEvent] --> Listener[Event Listener Thread<br/>MDC: requestId=abc123]

    Listener -->|Submit to Executor| Decorator[TaskDecorator<br/>in ThreadPoolConfig]
    Decorator -->|Copy MDC| Task[Runnable Task]

    Task -->|Execute in| Worker1[Worker Thread 1<br/>MDC: requestId=abc123]
    Task -->|Execute in| Worker2[Worker Thread 2<br/>MDC: requestId=abc123]

    Worker1 -->|Log with MDC| Logger1[Log: requestId=abc123]
    Worker2 -->|Log with MDC| Logger2[Log: requestId=abc123]

    Worker1 -->|Finally| Clear1[MDC.clear]
    Worker2 -->|Finally| Clear2[MDC.clear]

    style Event fill:#e1f5ff
    style Decorator fill:#fff4e1
    style Worker1 fill:#c8e6c9
    style Worker2 fill:#c8e6c9
    style Logger1 fill:#f3e5f5
    style Logger2 fill:#f3e5f5
```

## Synchronization Points

### 1. VoskSttEngine Thread Safety

```mermaid
stateDiagram-v2
    [*] --> Uninitialized

    state "Synchronized Block" as SyncInit {
        Uninitialized --> CheckInit: initialize()
        CheckInit --> AlreadyInit: initialized == true
        CheckInit --> DoInit: initialized == false
        DoInit --> InitModel: load model (lock held)
        InitModel --> SetFlag: initialized = true
        SetFlag --> [*]
        AlreadyInit --> [*]
    }

    state "Per-Call Recognizer" as PerCall {
        [*] --> AcquireSemaphore: transcribe()
        AcquireSemaphore --> GetModel: acquire permits
        GetModel --> CreateRecognizer: model reference
        CreateRecognizer --> Process: new Recognizer(model)
        Process --> ReleaseSemaphore: release permits
        ReleaseSemaphore --> [*]
    }

    note right of SyncInit
        Lock: ReentrantLock (lock.lock() / lock.unlock())
        Protects: initialized, closed, model
    end note

    note right of PerCall
        Lock: Semaphore (4 permits)
        No shared recognizer
        Thread-safe by isolation
    end note
```

### 2. WhisperSttEngine Thread Safety

```mermaid
flowchart TB
    Call1[Thread 1: transcribe] -->|Acquire| Sem[Semaphore<br/>max 2 permits]
    Call2[Thread 2: transcribe] -->|Acquire| Sem

    Sem -->|Permit 1| Temp1[Create temp-1.wav]
    Sem -->|Permit 2| Temp2[Create temp-2.wav]

    Temp1 --> Proc1[Spawn whisper<br/>process 1]
    Temp2 --> Proc2[Spawn whisper<br/>process 2]

    Proc1 --> Parse1[Parse stdout]
    Proc2 --> Parse2[Parse stdout]

    Parse1 --> Result1[Build EngineResult]
    Parse2 --> Result2[Build EngineResult]

    Result1 --> Release1[Release permit 1]
    Result2 --> Release2[Release permit 2]

    style Sem fill:#fff4e1
```

### 3. CaptureStateMachine Thread Safety

```mermaid
stateDiagram-v2
    [*] --> Idle

    state "Thread 1 (Hotkey)" as T1 {
        Idle --> AcquireLock1: startCapture()
        AcquireLock1 --> CheckState1: lock.lock()
        CheckState1 --> CreateSession: activeSession == null
        CheckState1 --> RejectStart: activeSession != null
        CreateSession --> SetUUID: activeSession = newUUID
        SetUUID --> ReleaseLock1: lock.unlock()
        ReleaseLock1 --> [*]
        RejectStart --> ReleaseLock1
    }

    state "Thread 2 (Hotkey Release)" as T2 {
        Idle --> AcquireLock2: stopCapture(uuid)
        AcquireLock2 --> CheckState2: lock.lock()
        CheckState2 --> ValidateUUID: activeSession == uuid
        CheckState2 --> RejectStop: activeSession != uuid
        ValidateUUID --> ClearSession: activeSession = null
        ClearSession --> ReleaseLock2: lock.unlock()
        ReleaseLock2 --> [*]
        RejectStop --> ReleaseLock2
    }

    note right of T1
        Lock: ReentrantLock
        Prevents: Double-start
    end note

    note right of T2
        Lock: ReentrantLock
        Prevents: Wrong session stop
    end note
```

### 4. PcmRingBuffer Thread Safety

```mermaid
sequenceDiagram
    participant Producer as Audio Capture Thread
    participant Buffer as PcmRingBuffer
    participant Consumer as Main Thread

    Note over Buffer: All methods synchronized

    Producer->>Buffer: write(chunk1)
    activate Buffer
    Buffer->>Buffer: synchronized method
    Buffer->>Buffer: append to ring
    deactivate Buffer

    Producer->>Buffer: write(chunk2)
    activate Buffer
    Buffer->>Buffer: synchronized method
    Buffer->>Buffer: append to ring
    deactivate Buffer

    Consumer->>Buffer: readAll()
    activate Buffer
    Buffer->>Buffer: synchronized method
    Buffer->>Buffer: copy all bytes
    Buffer->>Buffer: clear buffer
    deactivate Buffer
    Buffer-->>Consumer: byte[] snapshot

    Note over Buffer: Safe for single producer<br/>single consumer
```

## Concurrency Control Mechanisms

### ConcurrencyGuard Pattern

```mermaid
flowchart TB
    Request1[Request 1] --> Guard[ConcurrencyGuard]
    Request2[Request 2] --> Guard
    Request3[Request 3] --> Guard
    Request4[Request 4] --> Guard
    Request5[Request 5] --> Guard

    Guard -->|tryAcquire<br/>timeout 1s| Sem[Semaphore<br/>permits = 4]

    Sem -->|Permit 1| Work1[Vosk Transcribe 1]
    Sem -->|Permit 2| Work2[Vosk Transcribe 2]
    Sem -->|Permit 3| Work3[Vosk Transcribe 3]
    Sem -->|Permit 4| Work4[Vosk Transcribe 4]
    Sem -->|Timeout| Reject[TranscriptionException<br/>'Resource busy']

    Work1 --> Release1[release]
    Work2 --> Release2[release]
    Work3 --> Release3[release]
    Work4 --> Release4[release]

    Release1 --> Sem
    Release2 --> Sem
    Release3 --> Sem
    Release4 --> Sem

    style Sem fill:#fff4e1
    style Reject fill:#ffcdd2
    style Guard fill:#c8e6c9
```

## Thread Pool Configuration

```mermaid
graph LR
    subgraph Config["@Bean Configuration"]
        Bean[sttExecutor<br/>ThreadPoolTaskExecutor]
    end

    subgraph Pool["Thread Pool Properties"]
        Core[corePoolSize: 2<br/>Vosk + Whisper]
        Max[maxPoolSize: 4<br/>Burst capacity]
        Queue[queueCapacity: 10<br/>Bounded queue]
        Prefix[threadNamePrefix: stt-pool-]
    end

    subgraph Decorator["TaskDecorator"]
        MDC[Copy MDC context<br/>requestId, userId]
    end

    Config --> Pool
    Config --> Decorator

    Pool -->|Threads created| Worker1[stt-1]
    Pool -->|Threads created| Worker2[stt-2]
    Pool -->|Threads created| Worker3[stt-3]
    Pool -->|Threads created| Worker4[stt-4]

    Decorator -->|Wraps| Worker1
    Decorator -->|Wraps| Worker2
    Decorator -->|Wraps| Worker3
    Decorator -->|Wraps| Worker4

    style Config fill:#e1f5ff
    style Decorator fill:#fff4e1
```

## Race Condition Prevention

### Critical Section: Engine Initialization

```mermaid
flowchart TB
    T1[Thread 1: initialize] --> Check1{synchronized<br/>check initialized}
    T2[Thread 2: initialize] --> Wait[Wait for lock]

    Check1 -->|false| Init[doInitialize]
    Check1 -->|true| Skip1[Skip - already init]

    Init --> Set[initialized = true]
    Set --> Unlock1[Release lock]

    Unlock1 --> Wait
    Wait --> Check2{synchronized<br/>check initialized}
    Check2 -->|true| Skip2[Skip - already init]

    Skip1 --> Return1[Return]
    Skip2 --> Return2[Return]

    style Check1 fill:#ffcdd2
    style Check2 fill:#ffcdd2
    style Init fill:#c8e6c9
```

### Critical Section: Session Management

```mermaid
flowchart TB
    Start1[Thread 1: startCapture] --> Lock1[Acquire lock]
    Start2[Thread 2: startCapture] --> Blocked[Blocked on lock]

    Lock1 --> Check1{activeSession<br/>== null?}
    Check1 -->|Yes| Create[activeSession = UUID]
    Check1 -->|No| Reject1[Return false]

    Create --> Unlock1[Release lock]
    Reject1 --> Unlock1

    Unlock1 --> Blocked
    Blocked --> Check2{activeSession<br/>== null?}
    Check2 -->|No| Reject2[Return false<br/>Session exists]

    style Check1 fill:#ffcdd2
    style Check2 fill:#ffcdd2
    style Create fill:#c8e6c9
    style Reject2 fill:#ffe0b2
```

## Deadlock Prevention Strategy

### Lock Ordering Hierarchy

```mermaid
graph TB
    Level1[Level 1: CaptureStateMachine.lock<br/>ReentrantLock]
    Level2[Level 2: AbstractSttEngine.lock<br/>ReentrantLock + transcriptionLock<br/>ReentrantReadWriteLock]
    Level3[Level 3: VoskStreamingService.recognizerLock<br/>synchronized blocks — not explicit Lock]
    Level4[Level 4: PcmRingBuffer methods<br/>synchronized blocks — not explicit Lock]

    Level1 --> Level2
    Level2 --> Level3
    Level3 --> Level4

    Note1[Always acquire in order:<br/>1 → 2 → 3 → 4]
    Note2[Never hold multiple locks<br/>from different levels]

    style Level1 fill:#e1f5ff
    style Level2 fill:#fff4e1
    style Level3 fill:#ffe0b2
    style Level4 fill:#c8e6c9
```

### Lock-Free Zones (Stateless Operations)

```mermaid
graph LR
    subgraph NoLocks["No Synchronization Needed"]
        Reconcilers[All TranscriptReconciler<br/>implementations]
        Fallback[FallbackManager]
        Typing[TypingService]
        Validators[AudioValidator<br/>WavFormat]
    end

    subgraph Reason["Why Lock-Free?"]
        Stateless[No shared mutable state<br/>Pure functions<br/>Thread-local variables only]
    end

    NoLocks --> Reason

    style NoLocks fill:#c8e6c9
    style Reason fill:#fff9c4
```

## Timeout Management

```mermaid
flowchart TB
    Start[Submit Task] --> Future[CompletableFuture]
    Future --> Timeout{allOf.get<br/>timeoutMs}

    Timeout -->|Before timeout| Success[Both results ready]
    Timeout -->|After timeout| Expire[TimeoutException]

    Expire --> Cancel[Cancel remaining futures]
    Cancel --> Partial{Any result<br/>available?}

    Partial -->|Yes| UsePartial[Return partial EnginePair]
    Partial -->|No| Throw[TranscriptionException<br/>'Both failed or timed out']

    Success --> Return[Return EnginePair]

    style Timeout fill:#fff4e1
    style Expire fill:#ffcdd2
    style UsePartial fill:#ffe0b2
    style Success fill:#c8e6c9
```

## Memory Visibility Guarantees

| Mechanism | Usage | Visibility Guarantee |
|-----------|-------|---------------------|
| `synchronized` | AbstractSttEngine, CaptureStateMachine | Happens-before relationship established |
| `volatile` | ApplicationStateTracker.state, HotkeyManager.running, LiveCaptionManager.window, SystemTrayManager fields, JavaSoundAudioCaptureService.CaptureSession fields | Safe publication and visibility across threads |
| `final` fields | All immutable records, AbstractSttEngine.lock | Safe publication guaranteed |
| `volatile` | HotkeyManager.running | Safe publication of running state |
| `CompletableFuture` | ParallelSttService | Result visibility via ForkJoinPool |
| `Semaphore` | ConcurrencyGuard | Memory barrier on acquire/release |
| `Platform.runLater` | LiveCaptionManager → JavaFX | Thread-safe handoff to JavaFX Application Thread |
| `AtomicBoolean` | LiveCaptionManager.enabled | Lock-free toggle for feature enable/disable |

## Async Logging Thread Isolation

```mermaid
flowchart LR
    Worker1[Worker Thread 1] -->|Append| Ring1[RingBuffer 1]
    Worker2[Worker Thread 2] -->|Append| Ring2[RingBuffer 2]
    Main[Main Thread] -->|Append| Ring3[RingBuffer 3]

    Ring1 -->|Batched| Async1[AsyncAppender<br/>Thread 1]
    Ring2 -->|Batched| Async2[AsyncAppender<br/>Thread 2]
    Ring3 -->|Batched| Async1

    Async1 -->|Write| File[blckvox.log]
    Async2 -->|Write| Console[stdout]

    style Ring1 fill:#f3e5f5
    style Ring2 fill:#f3e5f5
    style Ring3 fill:#f3e5f5
    style Async1 fill:#c8e6c9
    style Async2 fill:#c8e6c9
```

## Key Concurrency Invariants

1. **Single Active Session**: `CaptureStateMachine` ensures only one audio session active at a time
2. **Bounded Parallelism**: Semaphores limit concurrent STT operations (Vosk: 4, Whisper: 2)
3. **Per-Call Isolation**: Each transcription uses isolated resources (recognizer, temp files)
4. **Lock Ordering**: Strict hierarchy prevents deadlocks
5. **MDC Propagation**: Request context preserved across thread boundaries
6. **Idempotent Initialization**: Multiple `initialize()` calls are safe (synchronized check)
7. **Graceful Timeout**: Partial results returned if one engine completes before timeout
8. **JavaFX Thread Safety**: All `LiveCaptionWindow` mutations go through `Platform.runLater()` — never called from Spring event threads directly
9. **Streaming Recognizer Isolation**: `VoskStreamingService` shares the `VoskModelProvider` `Model` with `VoskSttEngine` but manages its own `Recognizer` per streaming session
