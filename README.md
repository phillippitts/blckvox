# blckvox

Privacy-first voice dictation for macOS using dual-engine speech-to-text (Vosk + Whisper).

## What Is This?

blckvox lets you dictate text into any macOS application using a configurable hotkey. Unlike cloud-based solutions (Dragon, Google), all transcription happens **locally on your Mac** - your voice data never leaves your device.

## Status: 🚧 In Development

Current phase: **Phases 0–5 complete** (Environment, Core Abstractions, STT Engines, Parallel + Reconciliation, Documentation) ✅
Next: Phase 6 – Production Hardening (Monitoring, Security, Performance)
See: [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)

Current capabilities (implemented):
- ✅ Log4j2 structured logging with MDC propagation (async console/file + audit log)
- ✅ Audio format validation (16kHz, 16-bit PCM, mono) with configurable min/max durations
- ✅ Domain model: TranscriptionResult
- ✅ Exception hierarchy (BlckvoxException, TranscriptionException, InvalidAudioException, ModelNotFoundException)
- ✅ SttEngine interface (Adapter pattern target)
- ✅ Typed configuration properties (VoskConfig, WhisperConfig, Audio/Hotkey/Typing/Orchestration/Reconciliation, Concurrency, Watchdog)
- ✅ Thread pool configuration with MDC task decoration
- ✅ Vosk STT engine (JNI) with per-call recognizer (thread-safe)
- ✅ Whisper STT engine via whisper.cpp (temp WAV + robust process manager with timeouts and stdout caps)
- ✅ Parallel execution (Vosk + Whisper) with reconciled path behind a flag
- ✅ Smart reconciliation: conditional dual-engine based on Vosk confidence threshold (70-80% resource savings)
- ✅ Reconciliation strategies: simple, confidence, overlap (configurable)
- ✅ Whisper JSON mode (opt-in) with token extraction for better overlap
- ✅ Audio Capture Service (Java Sound, PCM16LE mono @16kHz) with ring buffer, validation, and hermetic tests
- ✅ Hotkey detection (single-key, double-tap, modifier-combo) with reserved-shortcut detection and permission events
- ✅ Fallback typing chain (Robot → Clipboard → Notify), chunked paste, privacy-safe logging
- ✅ Event-driven watchdog with bounded auto-restart and cooldown
- ✅ Metrics: JMX metrics via Micrometer (engine latency, success/failure, reconciliation strategy)
- ✅ Live Caption overlay: real-time oscilloscope waveform + streaming Vosk captions (JavaFX, toggleable from tray menu)

Planned (later phases):
- ❌ Database persistence and search
- ❌ Security hardening (TLS pinning, runtime security)
- ❌ GDPR compliance features (90‑day retention, right to erasure, IP anonymization)

Already in place (build-time security):
- ✅ OWASP dependency-check (CVSS >= 7.0 fails build)
- ✅ SpotBugs bytecode analysis (effort=MAX, reportLevel=MEDIUM)

## Key Features

- **Push-to-Talk Dictation:** Press/hold hotkey → speak → release → text appears
- **Smart Dual-Engine Transcription:** Starts with Vosk (fast), automatically upgrades to Whisper verification when confidence is low - saves 70-80% resources while maintaining accuracy
- **100% Local:** No cloud APIs, no internet required after setup
- **Configurable Hotkeys:** Configurable via Spring Boot properties (application.properties)
- **Live Caption Overlay:** Real-time oscilloscope waveform and streaming Vosk captions during recording
- **Graceful Fallback:** Works even if Accessibility permission denied

## Key Terms & Acronyms

### Core Technologies
- **STT** - Speech-to-Text: Technology that converts spoken audio into written text
- **JNI** - Java Native Interface: Bridge between Java and native C/C++ libraries (used by Vosk)
- **PCM** - Pulse Code Modulation: Uncompressed audio format (16kHz, 16-bit, mono)
- **MDC** - Mapped Diagnostic Context: Thread-local logging context for request correlation

### STT Engines
- **Vosk** - Fast, offline STT engine (~100ms latency, Kaldi-based)
- **Whisper** - Accurate STT engine from OpenAI (~1-2s latency, better punctuation)

### Audio Specifications
- **16kHz** - Sample rate required by both STT engines (models trained on this rate)
- **16-bit** - Bit depth for PCM audio (signed integers)
- **Mono** - Single audio channel (stereo not supported)
- **WAV** - Container format (RIFF/WAVE headers)

### Architecture & Patterns
- **ADR** - Architectural Decision Record: Document capturing key design decisions
- **DTO** - Data Transfer Object: Object for passing data between layers
- **SLA** - Service Level Agreement: Performance guarantees (e.g., p95 latency < 2s)
- **SLO** - Service Level Objective: Target reliability metrics (e.g., 99.9% uptime)
- **SLI** - Service Level Indicator: Measured metric (error rate, latency)

### Development & Operations
- **CI/CD** - Continuous Integration/Continuous Deployment: Automated build and deploy pipeline
- **OWASP** - Open Web Application Security Project: Security standards and tools
- **CVE** - Common Vulnerabilities and Exposures: Security vulnerability identifier
- **GDPR** - General Data Protection Regulation: EU privacy law (90-day retention, right to erasure)
- **HIPAA** - Health Insurance Portability and Accountability Act: US healthcare privacy law

### Testing
- **TDD** - Test-Driven Development: Write tests before implementation
- **UAT** - User Acceptance Testing: End-user validation of features
- **JMH** - Java Microbenchmark Harness: Performance benchmarking framework

### Metrics & Monitoring
- **p50/p95/p99** - Percentile latency: 50th/95th/99th percentile response times
- **RTO** - Recovery Time Objective: Maximum acceptable downtime (e.g., 4 hours)
- **RPO** - Recovery Point Objective: Maximum acceptable data loss (e.g., 1 hour)
- **MTTR** - Mean Time to Recovery: Average time to restore service after failure

### blckvox Domain Terms
- **Reconciliation** - Process of selecting final text when Vosk and Whisper disagree
- **Fallback Manager** - System that gracefully degrades when Accessibility permission denied
- **PcmRingBuffer** - Thread-safe ring buffer for captured microphone audio
- **Model Validation** - Startup check ensuring STT models are present and loadable

## Getting Started

**Choose your path:**

- **📦 End Users** (Want to install and use the app): See [INSTALL.md](INSTALL.md)
  - Pre-built JAR installation
  - Run as background service (auto-start on login)
  - Troubleshooting common issues

- **⚙️ Operators** (Deploying to production): See [DEPLOYMENT.md](DEPLOYMENT.md)
  - systemd/LaunchDaemon service configuration
  - Production environment setup
  - Monitoring, logging, and security
  - Performance tuning and backup strategies

- **👨‍💻 Developers** (Contributing code): Continue reading below

---

## Quick Start (New Developers)

This project includes **automated setup scripts** to simplify onboarding:

```bash
# 1. Download STT models (~2 GB, includes checksum verification)
chmod +x ./setup-models.sh
./setup-models.sh

# 2. Build whisper.cpp binary (~5 min, auto-updates application.properties)
chmod +x ./build-whisper.sh
WRITE_APP_PROPS=true ./build-whisper.sh

# 3. Verify everything compiles
./gradlew clean build

# 4. Run the application
./gradlew bootRun

# 5. Grant macOS permissions when prompted
# System Settings → Privacy & Security → Accessibility
# System Settings → Privacy & Security → Microphone
```

**Expected output after step 2:**
```
✅ whisper.cpp binary built: /Users/.../blckvox/tools/whisper.cpp/main
✅ Updated: stt.whisper.binary-path=/Users/.../blckvox/tools/whisper.cpp/main
```

---

## Setup Scripts Reference

### `./setup-models.sh` - Download STT Models

**What it does:**
- Downloads Vosk model (vosk-model-en-us-0.22, ~1.8 GB)
- Downloads Whisper model (ggml-base.en.bin, ~147 MB)
- Verifies integrity with SHA-256 checksums
- Locks checksums to `models/checksums.sha256` for reproducibility

**Checksum verification:**
- **First run:** Computes and locks checksums
- **Subsequent runs:** Verifies against locked checksums (fails if files changed)
- **Enforce official checksums:** Set env vars before running:
  ```bash
  VOSK_SHA256=<official_sha256_for_zip> \
  WHISPER_SHA256=<official_sha256_for_bin> \
  ./setup-models.sh
  ```

**If checksums change legitimately:** Delete `models/checksums.sha256` and re-run (after verifying upstream source)

---

### `./build-whisper.sh` - Build whisper.cpp Binary

**What it does:**
- Clones `ggerganov/whisper.cpp` to `tools/whisper.cpp/`
- Checks out **v1.7.2** (pinned for reproducibility)
- Builds the `main` binary with parallel make
- Clears macOS quarantine and sets executable permissions
- Optionally auto-updates `application.properties` with binary path

**Usage:**
```bash
# Build with auto-update (recommended for onboarding)
WRITE_APP_PROPS=true ./build-whisper.sh

# Build without modifying properties (manual config)
./build-whisper.sh

# Use different version (testing upgrades)
GIT_REF=v1.8.0 ./build-whisper.sh

# Use latest main branch (not recommended for production)
GIT_REF=main ./build-whisper.sh
```

**Environment variables:**
- `WRITE_APP_PROPS=true` - Auto-update `application.properties` with binary path
- `GIT_REF=v1.7.2` - Pin to specific whisper.cpp version (default: v1.7.2)
- `INSTALL_DIR=./tools` - Where to clone/build whisper.cpp (default: `./tools`)
- `MAKE_JOBS=<N>` - Parallel make jobs (default: auto-detected CPU cores)

**Why v1.7.2 is pinned:**
- **Reproducibility:** Everyone gets the same binary across all environments
- **Stability:** v1.7.2 is a known stable release (Dec 2024)
- **Testability:** Phase 2 tests validated against this exact version
- **Security:** Enables vulnerability tracking and audit compliance

**Output:**
```
Building whisper.cpp (models already present)
OS: Darwin, Arch: arm64
Git ref: v1.7.2
✅ Found Whisper model: /Users/.../models/ggml-base.en.bin
✅ whisper.cpp binary built: /Users/.../tools/whisper.cpp/main

Next steps:
1) Configure Spring Boot properties to use the built binary:
   stt.whisper.binary-path=/Users/.../tools/whisper.cpp/main
```

---

## Audio Capture Service (Phase 3.1)

The Audio Capture Service implements a single-active-session, push-to-talk capture API and always returns validated PCM16LE mono at 16 kHz:

- API: `startSession()` → `stopSession()`/`cancelSession()` → `readAll(sessionId)`
- Buffering: ring buffer with 20–40 ms chunks to minimize GC and jitter; capped by validation thresholds
- Validation: integrates `AudioValidator` to enforce min/max duration and block alignment before STT
- Error events: publishes `CaptureErrorEvent` for permission/device failures (privacy-safe)
- Testability: uses a `DataLineProvider` seam to run hermetically in CI (no real microphone required)

Configuration (application.properties):

```properties
# Audio capture defaults
audio.capture.chunk-millis=40
audio.capture.max-duration-ms=600000
# audio.capture.device-name=
```

Validation thresholds:

```properties
# Audio Validation
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000
```

Notes:
- macOS permissions: System Settings → Privacy & Security → Microphone
- The service emits PCM; engines expect PCM (WAV headers are not passed to engines)

## Running gated integration tests

By default, integration tests that require real models/binaries are skipped in CI. To run them locally:

Vosk engine (requires downloaded model):
```bash
./setup-models.sh
./gradlew test -Dvosk.model.available=true --tests "*VoskSttEngineIntegrationTest*"
```

Parallel Vosk+Whisper (Vosk real, Whisper stubbed to keep hermetic):
```bash
./gradlew test -Dvosk.model.available=true --tests "*ParallelSttEnginesIntegrationTest*"
```

Optional accent and noise tests look for additional PCM fixtures under `src/test/resources/audio` and will be skipped if not present:
- /audio/phrase_british_en_1s.pcm
- /audio/phrase_indian_en_1s.pcm
- /audio/speech_with_cafe_noise_3s.pcm
- /audio/cafe_noise_only_3s.pcm

These tests assert engine resilience (no exceptions) and reasonable confidence handling without enforcing exact transcripts to avoid flakiness.

### Watchdog configuration (Task 2.7)

The event-driven watchdog automatically restarts engines within a sliding-window budget.

Properties (defaults shown):
```properties
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

The watchdog listens for engine failure events and performs a bounded restart. It never logs transcripts and avoids heavy polling.

---

## Troubleshooting

### `./build-whisper.sh` fails with "make not found"

**macOS:**
```bash
# Install Xcode Command Line Tools
xcode-select --install
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get install -y build-essential git
```

### Whisper binary fails with "Operation not permitted" on macOS

The binary is quarantined. The script should clear this automatically, but if not:
```bash
xattr -dr com.apple.quarantine tools/whisper.cpp/main
chmod +x tools/whisper.cpp/main
```

### `./setup-models.sh` checksum mismatch

This means the downloaded file doesn't match the locked checksum:
```bash
# Option 1: Verify upstream source is legitimate, then re-lock
rm models/checksums.sha256
./setup-models.sh

# Option 2: Clean and re-download
rm -rf models/
./setup-models.sh
```

### Build fails with "whisper.cpp binary not found"

Different whisper.cpp versions put the binary in different locations. The script tries 4 locations:
- `tools/whisper.cpp/main` (older versions)
- `tools/whisper.cpp/bin/whisper` (newer versions)
- `tools/whisper.cpp/build/bin/whisper` (CMake builds)
- `tools/whisper.cpp/examples/cli/whisper` (example builds)

If all fail, try building manually:
```bash
cd tools/whisper.cpp
make clean
make -j$(nproc)  # or: make -j$(sysctl -n hw.ncpu) on macOS
```

## Verify Structured Logs

After starting the app, check the console and `logs/blckvox.log` for structured Log4j 2 output:

```bash
./gradlew bootRun
```

You should see log lines like:
```
2025-10-14 16:45:12.345 [main] INFO  c.b.b.config.stt.ModelValidationService - Vosk model validated: models/vosk-model-en-us-0.22
2025-10-14 16:45:13.456 [main] INFO  c.b.b.service.stt.vosk.VoskSttEngine - Vosk engine initialized
```

## Architecture

- **2-Tier Event-Driven Desktop Application** (Presentation + Service layers)
- **Dual-Engine Processing:** Vosk and Whisper run concurrently
- **No Database Yet:** Persistence planned for Phase 6 (currently in-memory only)
- **Log4j 2 Logging:** Structured logging with MDC for request correlation
- **Strategy Pattern:** Pluggable reconciliation strategies (3 implementations)
- **Spring ApplicationEventPublisher:** Event-driven coordination (hotkeys, errors, transcription)

See: [Architecture Overview](docs/diagrams/architecture-overview.md) and [Data Flow](docs/diagrams/data-flow-diagram.md)

## Configuration

The project uses Spring Boot properties (`src/main/resources/application.properties`) with typed configuration classes.

### Automated Configuration

If you ran `WRITE_APP_PROPS=true ./build-whisper.sh`, your configuration is already set correctly:

```properties
# Audio validation thresholds
audio.validation.min-duration-ms=250
audio.validation.max-duration-ms=300000

# Vosk (model path set by ./setup-models.sh)
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.vosk.sample-rate=16000
stt.vosk.max-alternatives=1

# Whisper (binary path set by ./build-whisper.sh with WRITE_APP_PROPS=true)
stt.whisper.binary-path=/Users/.../blckvox/tools/whisper.cpp/main
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.timeout-seconds=120
stt.whisper.language=en
stt.whisper.threads=4

# Orchestration
stt.parallel.timeout-ms=120000
```

### Manual Configuration

If you didn't use `WRITE_APP_PROPS=true`, update `stt.whisper.binary-path` manually:

```properties
# Set to the output from ./build-whisper.sh
stt.whisper.binary-path=/absolute/path/to/tools/whisper.cpp/main
```

**Verify binary path:**
```bash
# After ./build-whisper.sh, the script outputs:
# ✅ whisper.cpp binary built: /full/path/to/binary
# Copy that path to application.properties
```

### Configuration Classes

Properties are bound to typed records for compile-time safety:
- `VoskConfig` → `stt.vosk.*`
- `WhisperConfig` → `stt.whisper.*`
- `AudioValidationProperties` → `audio.validation.*`
- `LiveCaptionProperties` → `live-caption.*`

See: `src/main/java/com/boombapcompile/blckvox/config/`

---

## External Dependencies

### whisper.cpp (Required for Whisper Engine)

- **Version:** v1.7.2 (pinned for reproducibility)
- **Repository:** https://github.com/ggerganov/whisper.cpp
- **Build Method:** `./build-whisper.sh` (automated)
- **Install Location:** `tools/whisper.cpp/`
- **Binary Location:** `tools/whisper.cpp/main`

**Why pinned to v1.7.2?**
- Guarantees reproducible builds across all environments (dev, CI, production)
- Prevents breaking changes from upstream `main` branch
- Enables security audits and CVE tracking
- Tested and validated against Phase 2 test suite

**Upgrading whisper.cpp:**
```bash
# Test new version first
GIT_REF=v1.8.0 ./build-whisper.sh

# Verify tests still pass
./gradlew test

# If successful, update default in build-whisper.sh:
# GIT_REF=${GIT_REF:-"v1.8.0"}
```

### Vosk Models (Required for Vosk Engine)

- **Model:** vosk-model-en-us-0.22
- **Size:** ~1.8 GB
- **Source:** https://alphacephei.com/vosk/models
- **Download Method:** `./setup-models.sh` (automated)
- **Install Location:** `models/vosk-model-en-us-0.22/`
- **Checksum Verification:** `models/checksums.sha256`

### Whisper Models (Required for Whisper Engine)

- **Model:** ggml-base.en.bin
- **Source:** https://huggingface.co/ggerganov/whisper.cpp
- **Download Method:** `./setup-models.sh` (automated)
- **Install Location:** `models/ggml-base.en.bin`
- **Size:** ~147 MB
- **Checksum Verification:** `models/checksums.sha256`

## Documentation

### Getting Started Guides
- **[INSTALL.md](INSTALL.md)** - End user installation guide (JAR, background service, troubleshooting)
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Production deployment guide (systemd, monitoring, security)
- [User Guide](docs/user-guide.md) - Using the app (hotkey config, dictation, reconciliation)
- [Operator Guide](docs/operator-guide.md) - Running and maintaining the service
- [Developer Guide](docs/developer-guide.md) - Contributing to the codebase

### Implementation & Planning
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) - 40-task roadmap (28 MVP + 12 production)
- [Session Context](docs/SESSION_CONTEXT.md) - Comprehensive planning session summary
- [Guidelines](.junie/guidelines.md) - 2,223-line developer guide

### Architecture Decisions
- [ADR-001: Dual-Engine STT Strategy](docs/adr/001-dual-engine-stt-strategy.md)
- [ADR-002: PostgreSQL MVP Database](docs/adr/002-postgresql-mvp-database.md) (Rejected)
- [ADR-003: Manual Model Setup](docs/adr/003-manual-model-setup.md)
- [ADR-004: Properties-Based Hotkey Config](docs/adr/004-properties-hotkey-config.md)
- [ADR-005: Log4j 2 Logging](docs/adr/005-log4j2-logging.md)
- [ADR-006: Event-Driven Architecture](docs/adr/006-event-driven-architecture.md)
- [ADR-007: Threading Model](docs/adr/007-threading-model.md)
- [ADR-008: Observability via JMX Metrics](docs/adr/008-observability-jmx-metrics.md)
- [ADR-009: Typing Fallback Chain](docs/adr/009-typing-fallback-chain.md)
- [ADR-010: Whisper Process Isolation](docs/adr/010-whisper-process-isolation.md)
- [ADR-011: Spring Boot for Desktop](docs/adr/011-spring-boot-desktop.md)
- [ADR-012: Audio Format Constraints](docs/adr/012-audio-format-constraints.md)
- [ADR-013: Reconciliation Strategy Selection](docs/adr/013-reconciliation-strategy-selection.md)

### Diagrams & Runbooks

**Architecture Diagrams:**
- [Architecture Overview](docs/diagrams/architecture-overview.md) - High-level components, layers, patterns
- [Data Flow Diagram](docs/diagrams/data-flow-diagram.md) - Sequence diagrams, state machines, error flows
- [Class Dependencies](docs/diagrams/class-dependencies.md) - UML class diagrams, package structure, design patterns
- [Thread Model & Concurrency](docs/diagrams/thread-model-concurrency.md) - Thread pools, synchronization, MDC propagation
- [Live Caption System](docs/diagrams/live-caption-system.md) - Oscilloscope waveform, streaming Vosk captions, JavaFX overlay

**User Guides:**
- [User Journey Map](docs/diagrams/user-journey.md) - Onboarding timeline, decision trees, usage scenarios
- [Troubleshooting Guide](docs/diagrams/troubleshooting-guide.md) - Problem diagnosis flowcharts, common fixes

**Reference Guides:**
- [Reconciliation Guide](docs/reconciliation.md)
- [Configuration Reference](docs/configuration-reference.md)
- [FAQ](docs/FAQ.md)

**Runbooks:**
- [Engine Failures](docs/runbooks/engine-failures.md)
- [Permissions & Hotkeys](docs/runbooks/permissions-and-hotkeys.md)

## Development

### Build

```bash
./gradlew clean build
```

### Run Tests

Where are the tests?
- src/test/java/com/boombapcompile/blckvox/BlckvoxApplicationTests.java (Spring context load test)
- src/test/java/com/boombapcompile/blckvox/TestBlckvoxApplication.java (test bootstrap example)
- src/test/java/com/boombapcompile/blckvox/config/IntegrationTestConfiguration.java (test-only configuration)

How to run:
```bash
# All tests
./gradlew test

# Single class (simple name)
./gradlew test --tests BlckvoxApplicationTests

# Single class (fully-qualified)
./gradlew test --tests com.boombapcompile.blckvox.BlckvoxApplicationTests

# Single test method
./gradlew test --tests com.boombapcompile.blckvox.BlckvoxApplicationTests.contextLoads
```

Notes:
- Gradle is already configured with useJUnitPlatform() (JUnit 5).
- You may see a Java 21 + Mockito warning during tests; it is harmless. A mitigation flag is already set in build.gradle: `tasks.test.jvmArgs('-XX:+EnableDynamicAgentLoading')`. 

### Project Structure

```
src/main/java/com/boombapcompile/blckvox/
├── config/            # Spring configuration and typed properties
│   ├── properties/    # AudioValidationProperties, HotkeyProperties, TypingProperties, etc.
│   ├── stt/           # VoskConfig, WhisperConfig, ModelValidationService
│   └── orchestration/ # Thread pool, event, orchestration config
├── service/           # Business logic, STT engines, orchestration, audio capture
│   ├── audio/         # Audio capture, PCM events
│   ├── stt/           # STT engines (Vosk, Whisper), streaming
│   ├── orchestration/ # State tracking, recording service
│   ├── reconcile/     # Reconciliation strategies
│   ├── fallback/      # Typing adapters and fallback chain (Robot, Clipboard, Notify)
│   ├── hotkey/        # Hotkey detection and triggers
│   ├── livecaption/   # JavaFX overlay (oscilloscope + captions)
│   └── tray/          # System tray icon and menu
├── domain/            # Domain records (TranscriptionResult, etc.)
├── events/            # Centralized error event listener
├── exception/         # Custom exceptions
└── util/              # Utility classes (TimeUtils, ProcessTimeouts)
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for prerequisites, build commands, testing philosophy, and PR process.

This project follows:
- **Clean Code Principles** (Robert C. Martin) - Mandatory naming conventions
- **Event-Driven Architecture** - Spring ApplicationEventPublisher for loose coupling
- **SOLID Principles** - Enforced via Checkstyle
- **Always Working Code** - Incremental development (15-45 min tasks)

See [Guidelines](.junie/guidelines.md) for comprehensive development standards.

## Project Timeline

**MVP Track (Phases 0-5): ~25.5 hours**
- Day 1: Phase 0-1 (Environment + Abstractions)
- Day 2: Phase 2-3 (Vosk + Parallel)
- Day 3: Phase 4-5 (Integration + Docs)

**Production Track (Phase 6): ~20 hours**
- Day 4: Operations (Monitoring, Alerting, Backups)
- Day 5: Deployment Safety (Canary, Rollback)
- Day 6: Performance & Security (Load Testing, Scanning)

## License

[To be determined]

## Acknowledgments

- **Vosk** - Alpha Cephei (https://alphacephei.com/vosk/)
- **Whisper** - OpenAI (https://github.com/openai/whisper)
- **whisper.cpp** - Georgi Gerganov (https://github.com/ggerganov/whisper.cpp)
