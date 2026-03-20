# Developer Guide

This guide helps contributors understand the architecture, patterns, testing strategy, and contribution flow for blckvox.

## Architecture Overview
- 2‑tier layering: Presentation → Service (no reverse deps; database persistence was evaluated but rejected per ADR-002)
- Event-driven orchestration (Spring ApplicationEvents)
- Strategy/Factory/Adapter patterns:
  - Strategy: TranscriptReconciler, HotkeyTrigger, TypingAdapter
  - Factory: HotkeyTriggerFactory
  - Adapter: VoskSttEngine (JNI), WhisperSttEngine (process)
  - Observer: Hotkey/Typing/Error/Watchdog events

See diagrams: `docs/diagrams/architecture-overview.md`.

## Key Components
- Hotkeys: `service.hotkey.*`
- Capture: `service.audio.capture.*`
- Engines: `service.stt.*` (vosk/whisper)
- Parallel execution: `service.stt.parallel.*`
- Reconciliation: `service.reconcile.*`
- Orchestrator: `service.orchestration.HotkeyRecordingAdapter` → `RecordingService` → `CaptureOrchestrator` + `TranscriptionOrchestrator`
- Fallback typing: `service.fallback.*`
- Watchdog: `service.stt.watchdog.*`
- Live caption: `service.livecaption.*` (JavaFX overlay with oscilloscope + streaming Vosk captions)
- System tray: `service.tray.SystemTrayManager`

## Configuration Properties (typed)
- `config.properties.AudioCaptureProperties`
- `config.properties.AudioValidationProperties`
- `config.properties.HealthProperties`
- `config.properties.HotkeyProperties` (+ `TriggerType`)
- `config.properties.LiveCaptionProperties` (live-caption.enabled, window dimensions, opacity)
- `config.properties.OrchestrationProperties`
- `config.properties.ReconciliationProperties`
- `config.properties.SttConcurrencyProperties`
- `config.properties.SttWatchdogProperties`
- `config.properties.ThreadPoolProperties`
- `config.properties.TrayProperties`
- `config.properties.TypingProperties`
- `config.stt.VoskConfig`
- `config.stt.WhisperConfig`

## Event Threading & Responsiveness

The application uses Spring's event-driven architecture to keep the UI responsive during long-running operations.

**Key Pattern: Async Event Handling**
- Hotkey events are handled synchronously for immediate capture start/stop
- Transcription processing is offloaded to background threads using `@Async` and a configured executor
- This prevents blocking the hotkey listener thread during STT engine calls (which can take seconds)

**Implementation:**
```java
public class HotkeyRecordingAdapter {

    @EventListener
    @Async("eventExecutor")
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        if (hotkeyProps.isToggleMode()) {
            // Atomic toggle: starts if idle, stops if recording — no TOCTOU gap
            if (recordingService.toggleRecording()) {
                LOG.info("Toggle mode: toggled capture at {}", evt.at());
            } else {
                LOG.warn("Toggle recording failed");
            }
            return;
        }
        // Push-to-talk: press starts
        if (recordingService.startRecording()) {
            LOG.info("Capture session started at {}", evt.at());
        } else {
            LOG.debug("Capture already active, ignoring press");
        }
    }

    @EventListener
    @Async("eventExecutor")
    public void onHotkeyReleased(HotkeyReleasedEvent evt) {
        if (hotkeyProps.isToggleMode()) {
            LOG.debug("Toggle mode: ignoring release event");
            return;
        }
        recordingService.stopRecording();
    }
}
```

**Thread Pool Configuration:**
- `sttExecutor` and `eventExecutor` thread pools are configured in `ThreadPoolConfig`
- Pool size matches STT engine concurrency limits to prevent resource exhaustion
- Callers should never block event listener threads with long-running STT operations

## Live Caption System

The live caption feature provides real-time visual feedback during recording via a JavaFX overlay window.

**Architecture:**
- `JavaSoundAudioCaptureService` publishes `PcmChunkCapturedEvent` after each 40ms PCM chunk
- `VoskStreamingService` feeds chunks to a streaming Vosk recognizer and publishes `VoskPartialResultEvent`
- `LiveCaptionManager` bridges Spring events to the JavaFX Application Thread via `Platform.runLater()`
- `LiveCaptionWindow` renders an oscilloscope waveform (Canvas) and caption text (Label)

**Threading:**
- Audio capture thread → publishes events on Spring event bus
- Spring event listeners process on the publishing thread (synchronous)
- All JavaFX UI mutations go through `Platform.runLater()` for thread safety
- `VoskStreamingService` uses `synchronized(recognizerLock)` to protect its recognizer

**Feature Toggle:**
- `@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")` on all live caption beans
- When disabled: no JavaFX initialization, no streaming recognizer, no tray checkbox — zero overhead
- `SystemTrayManager` accepts `Optional<LiveCaptionManager>` so it works with or without the feature

See diagrams: `docs/diagrams/live-caption-system.md`.

## Paragraph Break Semantics

The application automatically inserts paragraph breaks (newlines) within transcriptions when silence gaps exceed the configured threshold.

**Architecture:**
- Pause detection happens **within each STT engine** by analyzing the audio/transcription data
- **Vosk:** Uses Voice Activity Detection (VAD) with RMS amplitude analysis on PCM audio to detect silence periods
- **Whisper:** Uses segment timestamps from JSON output to identify silence gaps between spoken segments
- Both engines insert newlines (`"\n"`) directly into the transcription text when silence exceeds the threshold
- Configuration: `stt.orchestration.silence-gap-ms` (default: 1000ms / 1 second)

**Implementation Details:**
- **VoskSttEngine:** `AudioSilenceDetector` analyzes PCM amplitude; when consecutive silence frames exceed threshold, a newline is prepended to subsequent text
- **WhisperSttEngine:** `WhisperJsonParser.extractTextWithPauseDetection()` calculates time gaps between segment timestamps; newlines inserted at pause boundaries
- Whisper JSON mode must be enabled (`stt.whisper.output=json`) for pause detection to work

**Consumer Expectations:**
- Downstream consumers (typing adapters, UI displays) receive `TranscriptionResult` with **embedded newlines** when pauses are detected
- Consumers must handle newlines gracefully (e.g., `RobotTypingAdapter` types newlines as keystrokes, creating paragraph breaks)
- No post-processing or stripping of newlines should occur - they are intentional formatting from the STT engines

**Configuration:**
```properties
# Silence gap threshold for automatic paragraph breaks (milliseconds)
# Vosk: Uses Voice Activity Detection (VAD) to detect silence in PCM audio
# Whisper: Uses segment timestamps from JSON output (requires stt.whisper.output=json)
# Set to 0 to disable. Default: 1000 (1 second)
stt.orchestration.silence-gap-ms=1000
```

**Example Flow:**
1. User dictates "Hello world" → pauses 1.5 seconds → "New paragraph"
2. Vosk/Whisper detects the 1.5-second silence gap (exceeds 1000ms threshold)
3. Engine returns transcription text: `"Hello world\nNew paragraph"` (embedded newline)
4. `TranscriptionResult` published with text containing the newline
5. Typing adapter outputs the text with newline, creating a paragraph break in the document

## Testing Strategy

### Philosophy
- **No Mockito** in most tests — use fakes, stubs, and lambdas instead
- Hermetic by default: all OS / native integrations behind seams
- Tests should be fast, deterministic, and runnable without external dependencies

### Test Doubles Location
All shared test doubles live in `OrchestrationTestDoubles`:
- `FakeEngine` — configurable transcription result, tracks calls
- `SlowEngine` — introduces configurable delay
- `FailingEngine` — throws on transcribe()

Additional test doubles: `StubProcessFactory` and `TestProcess` live in `WhisperTestDoubles.java`, a shared test doubles file. `RecordingEngine` is defined within its test class.

### Seam Architecture
| External Dependency | Seam Interface | Test Double |
|---------------------|---------------|-------------|
| JNativeHook (hotkeys) | `GlobalKeyHook` | Fake implementation injected |
| Java Sound (audio) | `JavaSoundAudioCaptureService.DataLineProvider` | Fake `TargetDataLine` |
| whisper.cpp (process) | `ProcessFactory` | `StubProcessFactory` with `TestProcess` |
| Robot API (typing) | `RobotTypingAdapter.RobotFacade` | Fake in unit tests |
| Clipboard (typing) | `ClipboardTypingAdapter.ClipboardFacade` | Fake in unit tests |
| Notification (typing) | `NotifyOnlyAdapter` | Third typing tier (notify-only fallback) |

### Parameterized Tests
Use `@ParameterizedTest` with `@CsvSource` for boundary value testing:
```java
@ParameterizedTest(name = "SttPoolProperties: corePoolSize={0}, maxPoolSize={1}, queueCapacity={2} should be valid")
@CsvSource({"1, 1, 1", "1, 8, 50", "4, 4, 1", "16, 32, 100"})
void sttPoolPropertiesBoundaryValid(int core, int max, int queue) { ... }
```
See `ThreadPoolPropertiesTest` and `WhisperJsonParserTest` for examples.

### Test Tags
| Tag | Purpose | Command |
|-----|---------|---------|
| (none) | Unit tests | `./gradlew test` |
| `@Tag("integration")` | Integration tests | `./gradlew integrationTest` |
| `@Tag("real-binary")` | Requires real whisper.cpp | `./gradlew realBinaryTest` |
| `@Tag("requiresVoskModel")` | Requires Vosk model | `./gradlew voskIntegrationTest` |

### Coverage Expectations
- **Minimum gates:** 90% instruction / 80% branch (enforced by `jacocoTestCoverageVerification`)
- **Current:** 99.6% instruction / 98.1% branch
- JaCoCo excludes genuinely untestable classes (JNI, JavaFX, AWT, hardware)
- Run `./gradlew test jacocoTestReport` to generate report at `build/reports/jacoco/test/html/`

### Debugging Setup
- Add `-XX:+EnableDynamicAgentLoading` JVM arg (already configured in `build.gradle`)
- Use `@TempDir` for file-based tests (model validation, audio)
- Sparse files via `RandomAccessFile.setLength()` for large model stubs (>100MB)
- For async tests, use Awaitility: `await().atMost(5, SECONDS).until(...)`

### Key Test Files
- `HotkeyTriggerTests`, `HotkeyManagerTest`
- `JavaSoundAudioCaptureServiceTest`, `PcmRingBufferTest`
- `DefaultParallelSttServiceTest`, `DefaultParallelSttServiceTimeoutTest`
- `ReconcilerStrategiesTest`, `HotkeyRecordingAdapterReconciledTest`
- `WhisperJsonParserTest`, `WhisperProcessManagerJsonTest`, `WhisperSttEngineJsonModeTest`
- `SttEngineWatchdogTest` (watchdog lifecycle, confidence monitoring, health events)
- Fallback: `StrategyChainTypingService*`, `ClipboardTypingAdapterTest`

## Coding Standards
- Java 21, Spring Boot 3.5.x
- Clean Code principles enforced via Checkstyle (build fails on warnings)
- Constructor injection (no field injection)
- Public API Javadoc (interfaces and widely used components)
- Privacy: never log full transcripts at INFO; use `LogSanitizer.truncate()` for previews at DEBUG

## Build & Run

### Dependencies
The project uses the following key dependencies:

**Logging:**
- Log4j2 (`spring-boot-starter-log4j2`) replaces the default Logback
- LMAX Disruptor (`com.lmax:disruptor:3.4.4`) for async logging performance
- Configuration excludes `spring-boot-starter-logging` in favor of Log4j2

**STT Engines:**
- Vosk (`com.alphacephei:vosk:0.3.38`) - Offline speech-to-text engine
- JNA (`net.java.dev.jna:jna:5.16.0`) - Required by Vosk for native library access
- Note: Vosk 0.3.45 has native library issues on macOS; 0.3.38 is stable

**Code Quality:**
- Checkstyle plugin (`checkstyle`) enforces coding standards
- Configuration: `config/checkstyle/checkstyle.xml`
- Build fails on any violations (`maxWarnings = 0`)
- Checkstyle version: 10.21.3

**UI:**
- JavaFX 21 (`org.openjfx.javafxplugin`) - Live caption overlay window (oscilloscope + captions)

**Other Dependencies:**
- JNativeHook (`com.github.kwhat:jnativehook:2.2.2`) - Global hotkey support
- org.json (`org.json:json:20250107`) - JSON parsing for Vosk responses
- Awaitility (`org.awaitility:awaitility:4.2.2`) - Async testing support

### Build Commands
```bash
./gradlew clean build           # Full build with tests and Checkstyle
./gradlew check                 # Run tests + Checkstyle + SpotBugs + integration tests + coverage gates
./gradlew test                  # Unit tests only (excludes integration/real-binary tests)
./gradlew integrationTest       # Integration tests only
./gradlew voskIntegrationTest   # Vosk model integration tests
./gradlew realBinaryTest        # Tests requiring real binaries/hardware
./gradlew bootRun               # Run the application
```

### Code Quality & Analysis Tools

**Checkstyle** (already included in `check`):
- Version 10.21.3, configuration at `config/checkstyle/checkstyle.xml`
- Build fails on any violations (`maxWarnings = 0`)

**SpotBugs** (already included in `check`):
- Tool version 4.9.3, plugin version 6.1.7
- Runs at MAX effort with MEDIUM report level
- Exclude filter at `config/spotbugs/exclude-filter.xml`
- Reports at `build/reports/spotbugs/`

**Mutation Testing (PIT):**
```bash
./gradlew pitest                # Run PIT mutation testing
```
- Uses STRONGER mutators with 80% mutation score threshold
- Incremental analysis caching for faster re-runs
- HTML + XML reports at `build/reports/pitest/`

**OWASP Dependency Check:**
```bash
./gradlew dependencyCheckAnalyze   # Scan dependencies for known CVEs
```
- Fails the build on CVSS >= 7.0
- Suppression file at `config/owasp/suppression.xml`

**SBOM Generation (CycloneDX):**
```bash
./gradlew cyclonedxBom             # Generate Software Bill of Materials (JSON)
```

**ArchUnit (architectural fitness tests):**
- Dependency: `com.tngtech.archunit:archunit-junit5:1.4.1`
- 34 rules across 3 test files:
  - `ArchitectureRulesTest` — 24 rules (layering, dependency direction, naming conventions)
  - `CodeHygieneRulesTest` — 4 rules (field injection bans, logging standards)
  - `BugPreventionRulesTest` — 6 rules (exception handling, thread safety)
- Run with `./gradlew test` (included in the unit test suite)

### Running the Application
```bash
./gradlew bootRun
```
- Application runs with `-Djava.awt.headless=false` for Robot/Clipboard API support
- Observability via structured Log4j 2 logging with MDC correlation

## Contribution Flow
1. Create a small, independently testable task (feature or doc).
2. Keep changes minimal and hermetic; add unit tests.
3. Run `./gradlew check` locally (Checkstyle + SpotBugs + tests + coverage gates).
4. Update docs as needed (README + relevant guide).
5. Submit PR with succinct description and references to plan tasks.

## Troubleshooting for Developers
- JNI/Whisper issues: run `./setup-models.sh` and `./build-whisper.sh`; ensure macOS quarantine removed (`xattr -dr com.apple.quarantine tools/whisper.cpp/main`).
- Hotkeys on macOS: verify Accessibility permission; avoid OS-reserved combos.
- Audio capture: confirm microphone permission; device selection via `audio.capture.device-name` when needed.

## Roadmap / Future Work

The following features are planned but not yet implemented:

**Phase 6 - Production Hardening:**
- **Database persistence:** PostgreSQL integration for transcription history (currently no database)
- **Security hardening:** Structured observability metrics, TLS, OWASP scans, PII redaction
- **GDPR compliance:** Data retention policies, automated deletion, backup/restore procedures
- **Modulith architecture:** Refactor to Spring Modulith for better bounded contexts
- **Advanced monitoring:** Distributed tracing with OpenTelemetry/Jaeger
- **Resilience:** Circuit breakers with Resilience4j
- **Dependency management:** Dependency locking, Dependabot/Renovate
- **Streaming dictation:** Real-time transcription beyond whole-buffer MVP

See `docs/IMPLEMENTATION_PLAN.md` Phase 6 for detailed task breakdown.
