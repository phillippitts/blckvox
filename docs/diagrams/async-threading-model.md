# Async Event Processing and Threading Model

Detailed documentation of the blckvox asynchronous event processing architecture,
thread pool configurations, and thread handoff patterns. Derived from verified source
code in `ThreadPoolConfig.java`, `HotkeyRecordingAdapter.java`, `DefaultParallelSttService.java`,
`LiveCaptionManager.java`, `VoskStreamingService.java`, and all event listener classes.

---

## 1. Thread Architecture Overview

All seven thread types in the application shown as swim lanes. Arrows indicate how work
moves between threads and are labeled with the specific handoff mechanism.

```mermaid
graph TB
    subgraph MainThread["Main Thread (Spring Boot)"]
        direction TB
        SpringBoot[Spring Boot Startup]
        EventPublisher["ApplicationEventPublisher<br/>(synchronous dispatch)"]
        StateTracker[ApplicationStateTracker]
    end

    subgraph JNativeHookThread["JNativeHook Thread (OS-level)"]
        direction TB
        GlobalKeyHook[NativeKeyListener]
        HotkeyPressed["publish HotkeyPressedEvent"]
        HotkeyReleased["publish HotkeyReleasedEvent"]
    end

    subgraph AudioCaptureThread["Audio Capture Thread"]
        direction TB
        TargetDataLine["TargetDataLine.read() loop"]
        PcmChunkEvent["publish PcmChunkCapturedEvent"]
        CaptureError["publish CaptureErrorEvent"]
    end

    subgraph EventPool["event-pool-N (eventExecutor)"]
        direction TB
        EP1["event-pool-1"]
        EP2["event-pool-2"]
        EP3["event-pool-3"]
        EP4["event-pool-4"]
    end

    subgraph SttPool["stt-pool-N (sttExecutor)"]
        direction TB
        SP1["stt-pool-1<br/>Vosk Engine"]
        SP2["stt-pool-2<br/>Whisper Engine"]
        SP3["stt-pool-3"]
        SP4["stt-pool-4"]
    end

    subgraph JavaFXThread["JavaFX Application Thread"]
        direction TB
        LiveCaptionWindow["LiveCaptionWindow"]
        Oscilloscope["Canvas: Waveform"]
        CaptionLabel["Label: Caption Text"]
    end

    subgraph LogThread["Log4j2 AsyncAppender Threads"]
        direction TB
        RingBuffer["LMAX Disruptor<br/>Ring Buffer"]
        AsyncWriter["AsyncAppender Thread"]
    end

    %% JNativeHook -> eventExecutor (via @Async)
    JNativeHookThread -->|"Spring Event +<br/>@Async('eventExecutor')"| EventPool

    %% Audio capture -> sync listeners (same thread)
    AudioCaptureThread -->|"Spring Event<br/>(sync on capture thread)"| EventPublisher

    %% Sync listeners on caller thread -> Platform.runLater -> JavaFX
    EventPublisher -->|"Platform.runLater()"| JavaFXThread

    %% eventExecutor -> sttExecutor (CompletableFuture.supplyAsync)
    EventPool -->|"CompletableFuture<br/>.supplyAsync(task, sttExecutor)"| SttPool

    %% sttExecutor -> back to eventExecutor (CompletableFuture.allOf.get)
    SttPool -->|"CompletableFuture<br/>.allOf().get(timeout)"| EventPool

    %% Any thread -> Log4j2
    EventPool -.->|"Log4j2 append"| LogThread
    SttPool -.->|"Log4j2 append"| LogThread
    AudioCaptureThread -.->|"Log4j2 append"| LogThread
    MainThread -.->|"Log4j2 append"| LogThread

    style MainThread fill:#e1f5ff
    style JNativeHookThread fill:#fff4e1
    style AudioCaptureThread fill:#ffe0b2
    style EventPool fill:#f3e5f5
    style SttPool fill:#c8e6c9
    style JavaFXThread fill:#c8e6c9
    style LogThread fill:#fff9c4
```

### Thread Handoff Mechanisms Summary

| Handoff | Mechanism | Blocking? |
|---------|-----------|-----------|
| JNativeHook -> eventExecutor | `@Async("eventExecutor")` on `@EventListener` | No (fire-and-forget) |
| Audio capture -> sync listeners | `ApplicationEventPublisher.publishEvent()` | Yes (runs on capture thread) |
| Sync listener -> JavaFX | `Platform.runLater(Runnable)` | No (posts to FX queue) |
| eventExecutor -> sttExecutor | `CompletableFuture.supplyAsync(task, sttExecutor)` | No (async submit) |
| sttExecutor -> eventExecutor | `CompletableFuture.allOf().get(timeoutMs)` | Yes (blocks event-pool thread until results) |
| Any thread -> Log4j2 | LMAX Disruptor ring buffer append | No (lock-free ring buffer) |

---

## 2. @Async vs Sync Listener Classification

Every `@EventListener` in the application, grouped by whether it runs asynchronously on a
thread pool or synchronously on the publisher's thread.

```mermaid
graph TB
    subgraph AsyncListeners["ASYNC Listeners<br/>(@Async)"]
        direction TB
        A1["HotkeyRecordingAdapter<br/>.onHotkeyPressed()<br/>(@Async eventExecutor)"]
        A2["HotkeyRecordingAdapter<br/>.onHotkeyReleased()<br/>(@Async eventExecutor)"]
        A3["HotkeyRecordingAdapter<br/>.onCaptureError()<br/>(@Async eventExecutor)"]
        A4["FallbackManager<br/>.onTranscription()<br/>(@Async eventExecutor)"]
        A5["VoskStreamingService<br/>.onPcmChunk()<br/>(@Async eventExecutor)"]
        A6["SttEngineWatchdog<br/>.onFailure()<br/>(@Async sttExecutor)"]
    end

    subgraph SyncListeners["SYNC Listeners<br/>(run on publisher's thread)"]
        direction TB

        subgraph RunsOnHotkeyThread["Runs on HotkeyManager Thread"]
            S1["ErrorEventsListener<br/>.onHotkeyPermissionDenied()"]
            S2["ErrorEventsListener<br/>.onHotkeyConflict()"]
        end

        subgraph RunsOnAudioThread["Runs on Audio Capture Thread"]
            S3["ErrorEventsListener<br/>.onCaptureError()"]
            S4["LiveCaptionManager<br/>.onPcmChunk()"]
        end

        subgraph RunsOnCallerThread["Runs on Caller's Thread (varies)"]
            S6["LiveCaptionManager<br/>.onStateChanged()"]
            S7["LiveCaptionManager<br/>.onVoskPartialResult()"]
            S8["VoskStreamingService<br/>.onStateChanged()"]
            S9["SystemTrayManager<br/>.onStateChanged()"]
            S10["SystemTrayManager<br/>.onBufferOverflow()"]
            S12["SttEngineWatchdog<br/>.onTranscriptionCompleted()"]
            S14["SttEngineWatchdog<br/>.onRecovered()"]
            S15["TypingEventsListener<br/>.onFallback()"]
            S16["TypingEventsListener<br/>.onAllFailed()"]
            S17["DefaultRecordingService<br/>.onTranscriptionCompleted()"]
        end
    end

    EventPool["eventExecutor<br/>Core: 2 | Max: 4<br/>Queue: 10"] --> AsyncListeners

    AsyncListeners -->|"Offloaded to<br/>event-pool-N"| Note1["Prevents blocking<br/>JNativeHook thread<br/>and Spring event bus"]

    SyncListeners -->|"Runs inline on<br/>publisher thread"| Note2["Zero overhead,<br/>but blocks publisher<br/>until listener returns"]

    style AsyncListeners fill:#c8e6c9
    style SyncListeners fill:#ffe0b2
    style RunsOnHotkeyThread fill:#fff4e1
    style RunsOnAudioThread fill:#e1f5ff
    style RunsOnCallerThread fill:#f3e5f5
    style EventPool fill:#c8e6c9
```

### Why These Six Are Async

There are six `@Async` listeners in the application. The three `HotkeyRecordingAdapter` methods use `@Async("eventExecutor")` to avoid blocking the JNativeHook OS key listener thread; the remaining three async listeners each have their own reason for offloading work:

1. **`HotkeyRecordingAdapter.onHotkeyPressed`** triggers `startRecording()` which starts audio capture -- must not block the JNativeHook OS key listener thread.
2. **`HotkeyRecordingAdapter.onHotkeyReleased`** triggers `stopRecording()` which calls `transcriptionOrchestrator.transcribe()` -- CPU-intensive work that would block the Spring event bus for seconds.
3. **`HotkeyRecordingAdapter.onCaptureError`** triggers `cancelRecording()` -- must not block the audio capture thread that publishes the error.
4. **`FallbackManager.onTranscription`** (`@Async("eventExecutor")`) processes the transcription result and invokes the typing service -- must not block the thread that published the `TranscriptionCompletedEvent`.
5. **`VoskStreamingService.onPcmChunk`** (`@Async("eventExecutor")`) feeds PCM data into the Vosk recognizer for streaming partial results -- must not block the audio capture thread that publishes `PcmChunkCapturedEvent`.
6. **`SttEngineWatchdog.onFailure`** (`@Async("sttExecutor")`) handles engine failure recovery and restart logic -- runs on the `sttExecutor` pool to avoid blocking the event bus during potentially slow engine restart operations.

---

## 3. Thread Pool Configuration

Detailed view of both thread pools, their sizing parameters, rejection policies,
and the shared MDC-propagating TaskDecorator.

```mermaid
graph TB
    subgraph SttExecutorPool["sttExecutor (ThreadPoolTaskExecutor)"]
        direction TB
        SttBean["@Bean name = 'sttExecutor'<br/>ThreadPoolConfig.java:59"]
        SttCore["corePoolSize: 2"]
        SttMax["maxPoolSize: 4"]
        SttQueue["queueCapacity: 10"]
        SttPrefix["threadNamePrefix: 'stt-pool-'"]
        SttReject["rejectionPolicy:<br/>Custom AbortPolicy (logs and throws RejectedExecutionException)"]
        SttShutdown["waitForTasksToCompleteOnShutdown: true<br/>awaitTerminationSeconds: 30"]
    end

    subgraph EventExecutorPool["eventExecutor (ThreadPoolTaskExecutor)"]
        direction TB
        EvtBean["@Bean name = 'eventExecutor'<br/>ThreadPoolConfig.java:105"]
        EvtCore["corePoolSize: 2"]
        EvtMax["maxPoolSize: 4"]
        EvtQueue["queueCapacity: 10"]
        EvtPrefix["threadNamePrefix: 'event-pool-'"]
        EvtReject["rejectionPolicy:<br/>DiscardOldestPolicy<br/>(custom lambda)"]
        EvtShutdown["waitForTasksToCompleteOnShutdown: true<br/>awaitTerminationSeconds: 30"]
    end

    subgraph MdcDecorator["Shared TaskDecorator (MDC Propagation)"]
        direction TB
        Capture["1. Capture: ThreadContext.getImmutableContext()"]
        Restore["2. Before run: ThreadContext.clearAll() + putAll(captured)"]
        Cleanup["3. Finally: ThreadContext.clearAll() + putAll(previous)"]
    end

    subgraph Users["Pool Consumers"]
        SttUser["DefaultParallelSttService<br/>@Qualifier('sttExecutor')"]
        EvtUser["HotkeyRecordingAdapter<br/>@Async('eventExecutor')"]
    end

    SttExecutorPool --> MdcDecorator
    EventExecutorPool --> MdcDecorator

    SttUser --> SttExecutorPool
    EvtUser --> EventExecutorPool

    SttReject -->|"When pool + queue full:<br/>logs warning and throws<br/>RejectedExecutionException"| BackpressureNote["Rejection effect:<br/>task is rejected immediately,<br/>caller receives exception"]
    EvtReject -->|"When pool + queue full:<br/>drop oldest queued event"| DropNote["Prevents blocking:<br/>Spring event bus never<br/>blocked by saturated pool"]

    style SttExecutorPool fill:#c8e6c9
    style EventExecutorPool fill:#f3e5f5
    style MdcDecorator fill:#fff4e1
    style Users fill:#e1f5ff
    style BackpressureNote fill:#ffe0b2
    style DropNote fill:#ffcdd2
```

### Configuration Properties

Both pools are externally configurable via `ThreadPoolProperties`:

| Property | sttExecutor | eventExecutor |
|----------|-------------|---------------|
| `threadpool.stt.core-pool-size` / `threadpool.event.core-pool-size` | 2 | 2 |
| `threadpool.stt.max-pool-size` / `threadpool.event.max-pool-size` | 4 | 4 |
| `threadpool.stt.queue-capacity` / `threadpool.event.queue-capacity` | 10 | 10 |
| `threadpool.stt.thread-name-prefix` / `threadpool.event.thread-name-prefix` | `stt-pool-` | `event-pool-` |
| Rejection Policy | Custom AbortPolicy (logs and throws `RejectedExecutionException`) | `DiscardOldestPolicy` (custom) |

---

## 4. MDC Propagation Flow

Sequence diagram showing how Log4j2 MDC (ThreadContext) flows from the event source
thread through the TaskDecorator into worker threads and is cleaned up afterward.

```mermaid
sequenceDiagram
    participant Source as Source Thread<br/>(e.g. JNativeHook)
    participant Spring as ApplicationEventPublisher
    participant Decorator as TaskDecorator<br/>(ThreadPoolConfig)
    participant Queue as Executor Queue
    participant Worker as Worker Thread<br/>(event-pool-1)
    participant Log as Log4j2

    Note over Source: MDC contains:<br/>requestId=abc-123<br/>userId=user1<br/>action=hotkey

    Source->>Spring: publishEvent(HotkeyPressedEvent)
    Spring->>Spring: Detect @Async("eventExecutor")
    Spring->>Decorator: decorate(Runnable task)

    Note over Decorator: CAPTURE PHASE<br/>contextMap = ThreadContext<br/>.getImmutableContext()<br/>{requestId, userId, action}

    Decorator->>Decorator: Wrap task in lambda<br/>that carries contextMap
    Decorator->>Queue: Submit wrapped Runnable

    Note over Queue: Task waits in<br/>bounded queue (capacity 10)

    Queue->>Worker: Dequeue and execute
    Note over Worker: RESTORE PHASE<br/>1. previous = ThreadContext.getImmutableContext()<br/>2. ThreadContext.clearAll()<br/>3. ThreadContext.putAll(contextMap)

    Worker->>Worker: Execute original task<br/>(onHotkeyPressed logic)

    Worker->>Log: LOG.info("Capture started")<br/>MDC: requestId=abc-123

    Note over Worker: CLEANUP PHASE (finally)<br/>1. ThreadContext.clearAll()<br/>2. ThreadContext.putAll(previous)

    Note over Worker: Worker MDC restored<br/>to pre-task state<br/>(prevents MDC leaks)
```

### MDC Fields Propagated

| Field | Source | Purpose | Log Pattern |
|-------|--------|---------|-------------|
| `requestId` | Generated per dictation session | Correlates all log lines for one dictation | Main LOG_PATTERN |
| `userId` | Application configuration | Identifies the user in multi-user scenarios | Main LOG_PATTERN |
| `action` | Event type (hotkey, capture, transcribe) | Categorizes the operation | AuditLog pattern only |
| `audioDurationMs` | Computed from PCM buffer size | Performance tracking | Not in any pattern (application-level only) |

### Why Previous Context Is Preserved

The decorator saves and restores the worker thread's existing MDC context (`previous`)
because thread pool threads are reused. Without this:

1. Task A sets `requestId=abc` on worker thread.
2. Task A completes, but `requestId=abc` leaks into the thread.
3. Task B (unrelated) runs on the same thread and logs `requestId=abc` incorrectly.

The `clearAll() + putAll(previous)` pattern in the `finally` block prevents this leak.

---

## 5. Rejection Policy Scenarios

Flowchart showing what happens when each pool reaches capacity.

### sttExecutor: Custom AbortPolicy (Reject on Saturation)

```mermaid
flowchart TB
    Submit["event-pool-1 calls:<br/>CompletableFuture.supplyAsync(<br/>task, sttExecutor)"]

    Submit --> CheckCore{"Core threads<br/>busy?<br/>(2 threads)"}

    CheckCore -->|"No"| RunCore["Task runs on<br/>idle core thread"]

    CheckCore -->|"Yes"| CheckQueue{"Queue full?<br/>(10 capacity)"}

    CheckQueue -->|"No"| Enqueue["Task added to queue<br/>(waits for core thread)"]

    CheckQueue -->|"Yes"| CheckMax{"Max threads<br/>reached?<br/>(4 threads)"}

    CheckMax -->|"No"| SpawnThread["Spawn new thread<br/>(up to max 4)"]

    CheckMax -->|"Yes"| AbortReject["Custom AbortPolicy:<br/>logs warning and throws<br/>RejectedExecutionException"]

    AbortReject --> RejectEffect["EFFECT: Task is rejected<br/>immediately, caller receives<br/>RejectedExecutionException"]

    RejectEffect --> Impact["IMPACT: Caller must handle<br/>the rejection (e.g. log error,<br/>skip transcription)"]

    RunCore --> Done["Task completes on<br/>stt-pool-N thread"]
    Enqueue --> Done
    SpawnThread --> Done

    style Submit fill:#e1f5ff
    style AbortReject fill:#ffcdd2
    style Backpressure fill:#ffe0b2
    style Impact fill:#ffe0b2
    style Done fill:#c8e6c9
```

### eventExecutor: DiscardOldestPolicy (Drop Old Events)

```mermaid
flowchart TB
    Submit["JNativeHook thread publishes<br/>HotkeyPressedEvent<br/>(@Async dispatches to eventExecutor)"]

    Submit --> CheckCore{"Core threads<br/>busy?<br/>(2 threads)"}

    CheckCore -->|"No"| RunCore["Task runs on<br/>idle core thread"]

    CheckCore -->|"Yes"| CheckQueue{"Queue full?<br/>(10 capacity)"}

    CheckQueue -->|"No"| Enqueue["Task added to queue"]

    CheckQueue -->|"Yes"| CheckMax{"Max threads<br/>reached?<br/>(4 threads)"}

    CheckMax -->|"No"| SpawnThread["Spawn new thread<br/>(up to max 4)"]

    CheckMax -->|"Yes"| DiscardOldest["DiscardOldestPolicy:<br/>queue.poll() removes<br/>OLDEST queued event"]

    DiscardOldest --> LogWarn["LOG.warn('Event executor<br/>saturated, discarding<br/>oldest queued task')"]

    LogWarn --> ReSubmit["New event submitted<br/>to now-available<br/>queue slot"]

    ReSubmit --> EventualRun["Task eventually runs<br/>when pool thread frees"]

    RunCore --> Done["Task completes on<br/>event-pool-N thread"]
    Enqueue --> Done
    SpawnThread --> Done
    EventualRun --> Done

    DiscardOldest --> WhyDiscard["WHY: Prevents JNativeHook<br/>thread from blocking.<br/>Old events are stale --<br/>new hotkey press matters more."]

    style Submit fill:#e1f5ff
    style DiscardOldest fill:#ffcdd2
    style LogWarn fill:#ffe0b2
    style WhyDiscard fill:#fff9c4
    style Done fill:#c8e6c9
```

### Comparison: Why Different Policies?

| Concern | sttExecutor (Custom AbortPolicy) | eventExecutor (DiscardOldestPolicy) |
|---------|-------------------------------|-------------------------------------|
| **Priority** | Reject overflow tasks with clear error | Recent events more important than old |
| **Caller** | event-pool thread (receives exception on rejection) | JNativeHook thread (must never block) |
| **Data loss** | Rejected tasks are lost (logged) | Oldest queued event dropped |
| **Backpressure** | Immediate rejection, no caller blocking | No backpressure on publisher |
| **Risk** | STT tasks rejected under extreme load | Stale hotkey events silently discarded |

---

## 6. Complete Dictation Threading

End-to-end sequence diagram for one complete dictation session, showing which thread
runs each step from hotkey press through text paste.

```mermaid
sequenceDiagram
    participant OS as JNativeHook<br/>Thread
    participant Spring as Spring Event Bus<br/>(Main Thread)
    participant EP as event-pool-1<br/>(eventExecutor)
    participant Audio as Audio Capture<br/>Thread
    participant FX as JavaFX App<br/>Thread
    participant SP1 as stt-pool-1<br/>(Vosk)
    participant SP2 as stt-pool-2<br/>(Whisper)
    participant Log as Log4j2 Async<br/>Thread

    Note over OS: User presses hotkey

    rect rgb(255, 244, 225)
        Note right of OS: PHASE 1: Hotkey Detection
        OS->>Spring: publishEvent(HotkeyPressedEvent)
        Spring->>Spring: Detect @Async("eventExecutor")
        Spring->>EP: Submit to event-pool
    end

    rect rgb(200, 230, 201)
        Note right of EP: PHASE 2: Start Recording
        EP->>EP: HotkeyRecordingAdapter.onHotkeyPressed()
        EP->>EP: RecordingService.startRecording()
        EP->>EP: CaptureOrchestrator.startCapture()
        EP->>EP: stateTracker.transitionTo(RECORDING)
        EP->>Spring: publishEvent(ApplicationStateChangedEvent)

        Note over Spring: Sync listeners fire on event-pool-1 thread
        Spring->>EP: SystemTrayManager.onStateChanged()
        EP->>FX: SwingUtilities.invokeLater(updateTray)
        Spring->>EP: LiveCaptionManager.onStateChanged()
        EP->>FX: Platform.runLater(showWindow)
        Spring->>EP: VoskStreamingService.onStateChanged()
        EP->>EP: openRecognizer()
    end

    rect rgb(225, 245, 255)
        Note right of Audio: PHASE 3: Audio Capture Loop
        Audio->>Audio: TargetDataLine.read(buffer)
        Audio->>Spring: publishEvent(PcmChunkCapturedEvent)

        Note over Spring: Sync listeners fire on audio capture thread

        Spring->>Audio: LiveCaptionManager.onPcmChunk()
        Audio->>FX: Platform.runLater(updateWaveform)

        Spring->>Audio: VoskStreamingService.onPcmChunk()
        Audio->>Audio: recognizer.acceptWaveForm()
        Audio->>Spring: publishEvent(VoskPartialResultEvent)
        Spring->>Audio: LiveCaptionManager.onVoskPartialResult()
        Audio->>FX: Platform.runLater(updateCaption)

        Note over Audio: ...repeats for each PCM chunk...
    end

    Note over OS: User releases hotkey

    rect rgb(255, 244, 225)
        Note right of OS: PHASE 4: Stop Recording
        OS->>Spring: publishEvent(HotkeyReleasedEvent)
        Spring->>EP: Submit to event-pool (via @Async)
    end

    rect rgb(243, 229, 245)
        Note right of EP: PHASE 5: Transcription
        EP->>EP: HotkeyRecordingAdapter.onHotkeyReleased()
        EP->>EP: RecordingService.stopRecording()
        EP->>EP: CaptureOrchestrator.stopCapture() -> byte[] pcm
        EP->>EP: stateTracker.transitionTo(TRANSCRIBING)
        EP->>Spring: publishEvent(ApplicationStateChangedEvent)

        EP->>EP: TranscriptionOrchestrator.transcribe(pcm)
        EP->>EP: DefaultParallelSttService.transcribeBoth(pcm, timeout)

        par Parallel STT Execution
            EP->>SP1: CompletableFuture.supplyAsync(voskTask, sttExecutor)
            SP1->>SP1: VoskSttEngine.transcribe(pcm)
            SP1->>Log: LOG.info("[engine-result] engine=vosk")
            SP1-->>EP: EngineResult(vosk)
        and
            EP->>SP2: CompletableFuture.supplyAsync(whisperTask, sttExecutor)
            SP2->>SP2: WhisperSttEngine.transcribe(pcm)
            SP2->>Log: LOG.info("[engine-result] engine=whisper")
            SP2-->>EP: EngineResult(whisper)
        end

        EP->>EP: CompletableFuture.allOf().get(timeoutMs)
        EP->>EP: TranscriptReconciler.reconcile(vosk, whisper)
        EP->>Spring: publishEvent(TranscriptionCompletedEvent)
    end

    rect rgb(200, 230, 201)
        Note right of EP: PHASE 6: Text Delivery
        Note over Spring: Sync listeners fire on event-pool-1 thread
        Spring->>EP: FallbackManager.onTranscription()
        EP->>EP: TypingService.paste(text)
        EP->>EP: Robot.keyPress/keyRelease (Cmd+V)
        EP->>Log: LOG.info("[paste] engine=..., chars=...")

        Spring->>EP: SttEngineWatchdog.onTranscriptionCompleted()
        EP->>EP: ConfidenceMonitor.record()

        Spring->>EP: DefaultRecordingService.onTranscriptionCompleted()
        EP->>EP: stateTracker.transitionTo(IDLE)
        EP->>Spring: publishEvent(ApplicationStateChangedEvent)

        Spring->>EP: SystemTrayManager.onStateChanged()
        EP->>FX: SwingUtilities.invokeLater(updateTray -> IDLE)
        Spring->>EP: LiveCaptionManager.onStateChanged()
        EP->>FX: Platform.runLater(hideWindow)
        Spring->>EP: VoskStreamingService.onStateChanged()
        EP->>EP: closeRecognizer()
    end

    Note over OS,Log: Dictation complete -- text pasted into active application
```

### Phase Summary: Thread Ownership

| Phase | Active Thread | Duration |
|-------|--------------|----------|
| 1. Hotkey Detection | JNativeHook thread | ~1ms |
| 2. Start Recording | event-pool-1 | ~10ms |
| 3. Audio Capture | Audio Capture thread (loop) | 1-30 seconds (user-controlled) |
| 4. Stop Hotkey | JNativeHook thread -> event-pool | ~1ms handoff |
| 5. Transcription | event-pool-1 (orchestration) + stt-pool-1/2 (engines) | 100ms-10s |
| 6. Text Delivery | event-pool-1 | ~50ms |

### Critical Observation: event-pool-1 Is the Workhorse

During phases 2, 5, and 6, a single `event-pool-N` thread orchestrates the entire
recording lifecycle. This is why the eventExecutor uses `DiscardOldestPolicy`: if
a second hotkey press arrives while event-pool-1 is still transcribing, the pool must
not block the JNativeHook thread. Old queued events are dropped because only the most
recent user action matters.

---

## Appendix: Thread Type Reference

| Thread | Created By | Lifecycle | Count |
|--------|-----------|-----------|-------|
| Main Thread | JVM / Spring Boot | Application lifetime | 1 |
| JNativeHook Thread | `GlobalScreen.registerNativeHook()` | Application lifetime | 1 |
| Audio Capture Thread | `JavaSoundAudioCaptureService` | Per recording session | 1 |
| stt-pool-N | `ThreadPoolTaskExecutor` (sttExecutor) | Core: always alive; max: idle timeout | 2-4 |
| event-pool-N | `ThreadPoolTaskExecutor` (eventExecutor) | Core: always alive; max: idle timeout | 2-4 |
| JavaFX Application Thread | `Platform.startup()` / JavaFX runtime | Application lifetime | 1 |
| Log4j2 AsyncAppender | LMAX Disruptor | Application lifetime | 1-2 |
