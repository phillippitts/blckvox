# Blckvox Startup and Shutdown Lifecycle

This document describes the complete startup and shutdown lifecycle of the blckvox Spring Boot application. It covers bean creation, `@PostConstruct` validation, `SmartLifecycle` phase ordering, and graceful shutdown teardown. All diagrams use Mermaid syntax and are derived directly from source code.

Key source files:

| Component | Source |
|---|---|
| `AudioFormatConfig` | `config/AudioFormatConfig.java` |
| `HotkeyConfigurationValidator` | `config/hotkey/HotkeyConfigurationValidator.java` |
| `JavaSoundAudioCaptureService` | `service/audio/capture/JavaSoundAudioCaptureService.java` |
| `ModelValidationService` | `config/stt/ModelValidationService.java` |
| `SttEngineWatchdog` | `service/stt/watchdog/SttEngineWatchdog.java` |
| `HotkeyManager` | `service/hotkey/HotkeyManager.java` |
| `JavaFxLifecycle` | `service/livecaption/JavaFxLifecycle.java` |
| `SystemTrayManager` | `service/tray/SystemTrayManager.java` |

---

## 1. Full Startup Sequence

This sequence diagram shows the complete startup order from `SpringApplication.run()` through `@PostConstruct` validations to `SmartLifecycle.start()` calls. Conditional beans are marked with `opt` fragments.

```mermaid
sequenceDiagram
    participant App as SpringApplication.run()
    participant Ctx as ApplicationContext
    participant AFC as AudioFormatConfig
    participant HCV as HotkeyConfigurationValidator
    participant JSACS as JavaSoundAudioCaptureService
    participant MVS as ModelValidationService
    participant SEW as SttEngineWatchdog
    participant HM as HotkeyManager
    participant JFXL as JavaFxLifecycle
    participant STM as SystemTrayManager

    App->>Ctx: Bootstrap context and create all beans

    Note over Ctx: Phase 1 — @PostConstruct<br/>(runs after all beans are instantiated,<br/>order is undefined)

    Ctx->>AFC: @PostConstruct validateAudioFormatConstants()
    AFC-->>Ctx: OK (or throws IllegalStateException)

    Ctx->>HCV: @PostConstruct validate()
    HCV-->>Ctx: OK (or throws IllegalArgumentException)

    Ctx->>JSACS: @PostConstruct logSystemInfo()
    JSACS-->>Ctx: Logs OS, arch, device, mixer count

    opt stt.validation.enabled = true (default: true)
        Ctx->>MVS: @PostConstruct validateAllOnStartup()
        MVS->>MVS: validateVosk() — check model dir structure (am/, conf/)
        MVS->>MVS: validateVosk() — JNI smoke test (create/close recognizer)
        MVS->>MVS: validateWhisper() — check model file exists and size
        MVS->>MVS: validateWhisper() — check binary exists and is executable
        MVS-->>Ctx: OK (or throws ModelNotFoundException)
    end

    opt stt.watchdog.enabled = true (default: true)
        Ctx->>SEW: @PostConstruct initializeEngines()
        SEW->>SEW: Determine primary engine from orchestrationProperties.getPrimaryEngine()
        SEW->>SEW: primaryEngine.initialize()
        Note right of SEW: On exception: mark primary engine<br/>DISABLED (graceful degradation)
        loop For each remaining (non-primary) SttEngine bean
            SEW->>SEW: Log "Engine {} deferred for lazy initialization"
            Note right of SEW: Non-primary engines are NOT<br/>eagerly initialized
        end
        SEW-->>Ctx: OK (primary engine HEALTHY or DISABLED; others deferred)
    end

    Note over Ctx: Phase 2 — SmartLifecycle.start()<br/>(ascending phase order:<br/>lower phase starts first)

    opt live-caption.enabled = true
        Ctx->>JFXL: start() [phase = Integer.MAX_VALUE - 1]
        JFXL->>JFXL: Platform.startup()
        JFXL-->>Ctx: Running
    end

    Ctx->>HM: start() [phase = Integer.MAX_VALUE]
    HM->>HM: hook.register() — JNativeHook global key listener
    Note right of HM: On SecurityException:<br/>publishes HotkeyPermissionDeniedEvent<br/>(does not crash app)
    HM-->>Ctx: Running

    opt tray.enabled = true (default: true)
        Ctx->>STM: start() [phase = Integer.MAX_VALUE]
        STM->>STM: SwingUtilities.invokeLater(initializeTray)
        STM->>STM: Create AWT SystemTray icon with PopupMenu
        STM-->>Ctx: Running
    end

    Note over Ctx: Application ready
```

---

## 2. Startup Validation Chain

This flowchart shows each validation step as a decision diamond with pass/fail paths. Fail-fast validations crash the application. Graceful validations log warnings and continue.

```mermaid
flowchart TD
    START([SpringApplication.run]) --> BEANS[Create all beans]

    BEANS --> AF{AudioFormatConfig<br/>validateAudioFormatConstants<br/>16kHz, 16-bit, mono, LE?}
    AF -- Pass --> HK
    AF -- Fail --> AF_FAIL[/IllegalStateException/<br/>App exits immediately]

    HK{HotkeyConfigurationValidator<br/>validate<br/>type, key, modifiers, threshold?}
    HK -- Pass --> JSACS
    HK -- Fail --> HK_FAIL[/IllegalArgumentException/<br/>App exits immediately]

    JSACS[JavaSoundAudioCaptureService<br/>logSystemInfo<br/>Logs OS, arch, device info]
    JSACS --> MVS_CHECK

    MVS_CHECK{stt.validation.enabled<br/>= true?}
    MVS_CHECK -- "No (disabled)" --> SEW_CHECK
    MVS_CHECK -- "Yes (default)" --> VOSK

    VOSK{ModelValidationService<br/>validateVosk<br/>Model dir has am/ and conf/?<br/>JNI smoke test passes?}
    VOSK -- Pass --> WHISPER
    VOSK -- Fail --> VOSK_FAIL[/ModelNotFoundException/<br/>App exits immediately]

    WHISPER{ModelValidationService<br/>validateWhisper<br/>Model file exists and sized OK?<br/>Binary exists and executable?}
    WHISPER -- Pass --> SEW_CHECK
    WHISPER -- Fail --> WHISPER_FAIL[/ModelNotFoundException/<br/>App exits immediately]

    SEW_CHECK{stt.watchdog.enabled<br/>= true?}
    SEW_CHECK -- "No (disabled)" --> LIFECYCLE
    SEW_CHECK -- "Yes (default)" --> SEW

    SEW[SttEngineWatchdog<br/>initializeEngines]
    SEW --> SEW_LOOP

    SEW_LOOP{engine.initialize<br/>succeeds?}
    SEW_LOOP -- "Yes" --> SEW_OK[Engine marked HEALTHY]
    SEW_LOOP -- "No (exception)" --> SEW_DEGRADE[Engine marked DISABLED<br/>Log error, continue]

    SEW_OK --> LIFECYCLE
    SEW_DEGRADE --> LIFECYCLE

    LIFECYCLE([Proceed to SmartLifecycle.start])

    style AF_FAIL fill:#d32f2f,color:#fff
    style HK_FAIL fill:#d32f2f,color:#fff
    style VOSK_FAIL fill:#d32f2f,color:#fff
    style WHISPER_FAIL fill:#d32f2f,color:#fff
    style SEW_DEGRADE fill:#f57c00,color:#fff
    style SEW_OK fill:#388e3c,color:#fff
    style LIFECYCLE fill:#1565c0,color:#fff
```

---

## 3. SmartLifecycle Phase Ordering

Spring SmartLifecycle starts components in ascending phase order (lower phase value starts first) and stops them in descending phase order (higher phase value stops first). This diagram shows the phase values and resulting execution order.

```mermaid
flowchart LR
    subgraph STARTUP ["Startup Order (ascending phase)"]
        direction LR
        S1["1. JavaFxLifecycle<br/>phase = Integer.MAX_VALUE - 1<br/>(2,147,483,646)<br/>CONDITIONAL: live-caption.enabled=true"]
        S2["2. HotkeyManager<br/>phase = Integer.MAX_VALUE<br/>(default: no getPhase() override)"]
        S3["3. SystemTrayManager<br/>phase = Integer.MAX_VALUE<br/>(2,147,483,647)<br/>CONDITIONAL: tray.enabled=true<br/>(tied with HotkeyManager)"]
        S1 --> S2 --> S3
    end

    subgraph SHUTDOWN ["Shutdown Order (descending phase)"]
        direction LR
        D1["1. HotkeyManager<br/>phase = Integer.MAX_VALUE<br/>stops FIRST (tied with STM)"]
        D2["2. SystemTrayManager<br/>phase = Integer.MAX_VALUE<br/>stops FIRST (tied with HM)"]
        D3["3. JavaFxLifecycle<br/>phase = Integer.MAX_VALUE - 1<br/>stops LAST"]
        D1 --> D2 --> D3
    end

    STARTUP ~~~ SHUTDOWN
```

```mermaid
block-beta
    columns 4

    block:header:4
        columns 4
        h1["Component"] h2["Phase Value"] h3["Starts At Position"] h4["Stops At Position"]
    end
    block:row1:4
        columns 4
        r1a["JavaFxLifecycle"] r1b["MAX_VALUE - 1"] r1c["1st (earliest)"] r1d["3rd (latest)"]
    end
    block:row2:4
        columns 4
        r2a["HotkeyManager"] r2b["Integer.MAX_VALUE (default)"] r2c["2nd (tied with STM)"] r2d["1st (tied with STM)"]
    end
    block:row3:4
        columns 4
        r3a["SystemTrayManager"] r3b["MAX_VALUE"] r3c["2nd (tied with HM)"] r3d["1st (tied with HM)"]
    end
```

The ordering ensures:
- **Startup**: JavaFX platform initializes first (phase MAX_VALUE - 1). Then HotkeyManager and SystemTrayManager start together (both at phase MAX_VALUE, tied). The system tray icon creation may reference the JavaFX-backed live caption manager, which is already running.
- **Shutdown**: HotkeyManager and SystemTrayManager stop first (both at phase MAX_VALUE, tied). Then JavaFX exits last (phase MAX_VALUE - 1).

---

## 4. Shutdown Sequence

This sequence diagram shows the reverse teardown order. SmartLifecycle components stop in descending phase order (highest phase stops first). Then Spring destroys beans, invoking `@PreDestroy` methods.

```mermaid
sequenceDiagram
    participant Trigger as Shutdown Trigger<br/>(SIGTERM / SpringApplication.exit)
    participant Ctx as ApplicationContext
    participant STM as SystemTrayManager
    participant JFXL as JavaFxLifecycle
    participant HM as HotkeyManager
    participant JSACS as JavaSoundAudioCaptureService
    participant Spring as Spring Container

    Trigger->>Ctx: Initiate graceful shutdown

    Note over Ctx: SmartLifecycle.stop()<br/>Descending phase order<br/>(highest phase stops first)

    opt tray.enabled = true
        Ctx->>STM: stop() [phase = Integer.MAX_VALUE]
        STM->>STM: SwingUtilities.invokeAndWait()
        STM->>STM: SystemTray.remove(trayIcon)
        STM-->>Ctx: Tray icon removed
    end

    opt live-caption.enabled = true
        Ctx->>JFXL: stop() [phase = Integer.MAX_VALUE - 1]
        JFXL->>JFXL: Platform.exit()
        JFXL-->>Ctx: JavaFX platform stopped
    end

    Ctx->>HM: stop() [phase = Integer.MAX_VALUE]
    HM->>HM: hook.unregister()
    HM-->>Ctx: Native hook unregistered

    Note over Ctx: Bean destruction<br/>(@PreDestroy methods)

    Ctx->>HM: @PreDestroy shutdown()
    HM->>HM: stop() (defensive, idempotent)

    Ctx->>JSACS: @PreDestroy shutdown()
    JSACS->>JSACS: Force-close any active capture session
    JSACS->>JSACS: Interrupt and join capture thread

    Ctx->>Spring: Destroy remaining beans
    Spring->>Spring: Shut down thread pools<br/>(sttExecutor, eventExecutor)

    Note over Ctx: Application terminated
```

---

## 5. Fail-Fast Decision Tree

This flowchart answers the question: "Will the application start?" It traces each validation outcome to determine whether the app boots successfully, exits immediately, or starts in a degraded state.

```mermaid
flowchart TD
    Q1{Audio format constants<br/>match 16kHz / 16-bit /<br/>mono / little-endian?}
    Q1 -- No --> CRASH1([CRASH<br/>IllegalStateException<br/>App will NOT start])
    Q1 -- Yes --> Q2

    Q2{Hotkey config valid?<br/>type not null,<br/>key in allow-list,<br/>modifiers in allow-list,<br/>threshold 100-1000ms<br/>if double-tap?}
    Q2 -- No --> CRASH2([CRASH<br/>IllegalArgumentException<br/>App will NOT start])
    Q2 -- Yes --> Q3

    Q3{stt.validation.enabled<br/>= true?}
    Q3 -- No --> Q5
    Q3 -- Yes --> Q4A

    Q4A{Vosk model dir exists<br/>with am/ and conf/?<br/>JNI smoke test passes?}
    Q4A -- No --> CRASH3([CRASH<br/>ModelNotFoundException<br/>App will NOT start])
    Q4A -- Yes --> Q4B

    Q4B{Whisper model file<br/>exists and large enough?<br/>Whisper binary exists<br/>and is executable?}
    Q4B -- No --> CRASH4([CRASH<br/>ModelNotFoundException<br/>App will NOT start])
    Q4B -- Yes --> Q5

    Q5{stt.watchdog.enabled<br/>= true?}
    Q5 -- No --> OK
    Q5 -- Yes --> Q6

    Q6{All STT engines<br/>initialize successfully?}
    Q6 -- Yes --> OK([APP STARTS<br/>All engines HEALTHY])
    Q6 -- "Some fail" --> DEGRADED([APP STARTS<br/>Failed engines marked DISABLED<br/>Graceful degradation])
    Q6 -- "All fail" --> DEGRADED2([APP STARTS<br/>All engines DISABLED<br/>Reduced functionality])

    style CRASH1 fill:#d32f2f,color:#fff
    style CRASH2 fill:#d32f2f,color:#fff
    style CRASH3 fill:#d32f2f,color:#fff
    style CRASH4 fill:#d32f2f,color:#fff
    style OK fill:#388e3c,color:#fff
    style DEGRADED fill:#f57c00,color:#fff
    style DEGRADED2 fill:#f57c00,color:#fff
```

### Summary of Fail-Fast vs Graceful Behavior

| Validation | Behavior on Failure | Exception Type |
|---|---|---|
| `AudioFormatConfig.validateAudioFormatConstants()` | **Fail-fast** -- app will not start | `IllegalStateException` |
| `HotkeyConfigurationValidator.validate()` | **Fail-fast** -- app will not start | `IllegalArgumentException` |
| `ModelValidationService.validateVosk()` | **Fail-fast** -- app will not start | `ModelNotFoundException` |
| `ModelValidationService.validateWhisper()` | **Fail-fast** -- app will not start | `ModelNotFoundException` |
| `SttEngineWatchdog.initializeEngines()` | **Graceful** -- engine marked `DISABLED`, app continues | Exception caught and logged |
| `HotkeyManager.start()` | **Graceful** -- publishes `HotkeyPermissionDeniedEvent`, app continues | `SecurityException` caught |
