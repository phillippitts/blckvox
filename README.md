# blckvox

Privacy-first voice dictation for macOS using dual-engine speech-to-text (Vosk + Whisper).

> **Quick start:** Users -> [INSTALL.md](INSTALL.md) | Operators -> [DEPLOYMENT.md](DEPLOYMENT.md) | Developers -> [CONTRIBUTING.md](CONTRIBUTING.md)

## What Is This?

blckvox lets you dictate text into any macOS application using a configurable hotkey. Unlike cloud-based solutions (Dragon, Google), all transcription happens **locally on your Mac** - your voice data never leaves your device.

## Status

Current phase: **Phases 0-5 complete** (Environment, Core Abstractions, STT Engines, Parallel + Reconciliation, Documentation)
Next: Phase 6 - Production Hardening (Monitoring, Security, Performance)
See: [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)

Current capabilities (implemented):
- Vosk STT engine (JNI, ~100ms) + Whisper STT engine (whisper.cpp, ~1-2s)
- Smart reconciliation: conditional dual-engine based on Vosk confidence threshold
- Audio Capture Service (Java Sound, PCM16LE mono @16kHz) with ring buffer and validation
- Hotkey detection (single-key, double-tap, modifier-combo)
- Fallback typing chain (Robot -> Clipboard -> Notify)
- Event-driven watchdog with bounded auto-restart
- JMX metrics via Micrometer
- Live Caption overlay (JavaFX oscilloscope + streaming Vosk captions)
- Log4j2 structured logging with MDC propagation
- OWASP dependency-check + SpotBugs bytecode analysis

Planned: Database persistence, security hardening, GDPR compliance.

## Key Features

- **Push-to-Talk Dictation:** Press/hold hotkey -> speak -> release -> text appears
- **Smart Dual-Engine Transcription:** Starts with Vosk (fast), upgrades to Whisper when confidence is low - saves 70-80% resources
- **100% Local:** No cloud APIs, no internet required after setup
- **Configurable Hotkeys:** Via Spring Boot properties
- **Live Caption Overlay:** Real-time oscilloscope waveform and streaming captions
- **Graceful Fallback:** Works even if Accessibility permission denied

See [Glossary](docs/glossary.md) for key terms and acronyms.

## Quick Start (New Developers)

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
# System Settings -> Privacy & Security -> Accessibility
# System Settings -> Privacy & Security -> Microphone
```

For setup script details and environment variables, see [Setup Scripts Reference](docs/setup-scripts.md).

## Running Tests

```bash
# All unit tests
./gradlew test

# Single class
./gradlew test --tests BlckvoxApplicationTests

# Integration tests (requires real models)
./setup-models.sh
./gradlew test -Dvosk.model.available=true --tests "*VoskSttEngineIntegrationTest*"
```

### Watchdog configuration

The event-driven watchdog automatically restarts engines within a sliding-window budget:
```properties
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

## Configuration

The project uses Spring Boot properties with typed configuration classes. If you ran `WRITE_APP_PROPS=true ./build-whisper.sh`, configuration is already set correctly. Otherwise, update `stt.whisper.binary-path` manually.

Key property prefixes: `stt.vosk.*`, `stt.whisper.*`, `audio.validation.*`, `stt.orchestration.*`, `live-caption.*`

See [Configuration Reference](docs/configuration-reference.md) for all properties.

## Architecture

- **2-Tier Event-Driven Desktop Application** (Presentation + Service layers)
- **Dual-Engine Processing:** Vosk and Whisper run concurrently
- **Strategy Pattern:** Pluggable reconciliation strategies (simple, confidence, overlap)
- **Spring ApplicationEventPublisher:** Event-driven coordination

See: [Architecture Overview](docs/diagrams/architecture-overview.md) | [Data Flow](docs/diagrams/data-flow-diagram.md)

## Project Structure

```
src/main/java/com/boombapcompile/blckvox/
├── config/            # Spring configuration and typed properties
│   ├── properties/    # AudioValidationProperties, HotkeyProperties, etc.
│   ├── stt/           # VoskConfig, WhisperConfig, ModelValidationService
│   └── orchestration/ # Thread pool, event, orchestration config
├── service/           # Business logic
│   ├── audio/         # Audio capture, silence detection, PCM events
│   ├── stt/           # STT engines (Vosk, Whisper), streaming
│   ├── orchestration/ # State tracking, recording service
│   ├── reconcile/     # Reconciliation strategies
│   ├── fallback/      # Typing adapters and fallback chain
│   ├── hotkey/        # Hotkey detection and triggers
│   ├── livecaption/   # JavaFX overlay (oscilloscope + captions)
│   └── tray/          # System tray icon and menu
├── domain/            # Domain records (TranscriptionResult, etc.)
├── events/            # Centralized error event listener
├── exception/         # Custom exceptions
└── util/              # Utility classes
```

## Documentation

### Guides
- **[INSTALL.md](INSTALL.md)** - End user installation
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Production deployment (systemd, monitoring, security)
- [User Guide](docs/user-guide.md) - Hotkey config, dictation, reconciliation
- [Operator Guide](docs/operator-guide.md) - Running and maintaining the service
- [Developer Guide](docs/developer-guide.md) - Contributing to the codebase
- [Troubleshooting](TROUBLESHOOTING.md) - Common issues and fixes

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

### Diagrams & Reference
- [Architecture Overview](docs/diagrams/architecture-overview.md) | [Data Flow](docs/diagrams/data-flow-diagram.md) | [Class Dependencies](docs/diagrams/class-dependencies.md)
- [Thread Model](docs/diagrams/thread-model-concurrency.md) | [Live Caption System](docs/diagrams/live-caption-system.md)
- [User Journey](docs/diagrams/user-journey.md) | [Troubleshooting Flowcharts](docs/diagrams/troubleshooting-guide.md)
- [Reconciliation Guide](docs/reconciliation.md) | [Configuration Reference](docs/configuration-reference.md) | [FAQ](docs/FAQ.md)
- [Glossary](docs/glossary.md) | [Setup Scripts](docs/setup-scripts.md)

### Runbooks
- [Engine Failures](docs/runbooks/engine-failures.md) | [Permissions & Hotkeys](docs/runbooks/permissions-and-hotkeys.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for prerequisites, build commands, testing philosophy, and PR process.
See [Guidelines](.junie/guidelines.md) for comprehensive development standards.

## License

[To be determined]

## Acknowledgments

- **Vosk** - Alpha Cephei (https://alphacephei.com/vosk/)
- **Whisper** - OpenAI (https://github.com/openai/whisper)
- **whisper.cpp** - Georgi Gerganov (https://github.com/ggerganov/whisper.cpp)
