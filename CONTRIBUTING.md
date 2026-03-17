# Contributing to blckvox

Thank you for your interest in contributing to blckvox, a Java 21 Spring Boot desktop application for speech-to-text.

## Prerequisites

- Java 21 (Temurin recommended)
- Gradle 8.x (wrapper included)
- macOS (primary platform; Linux/Windows partial support)
- Xcode Command Line Tools (for whisper.cpp compilation)

## Getting Started

```bash
git clone <repo>
cd speakToMack
./setup-models.sh          # Download STT models (~2GB)
WRITE_APP_PROPS=true ./build-whisper.sh  # Build whisper.cpp
./gradlew clean build      # Verify everything compiles
```

## Code Style

- Checkstyle enforced (`maxWarnings = 0`) — the build fails on any violation.
- Config: `config/checkstyle/checkstyle.xml`
- Constructor injection only (no field injection).
- Public API Javadoc on interfaces and widely-used components.
- Privacy: never log full transcripts at INFO level.

## Testing Philosophy

- **No Mockito** in most tests — use fakes, stubs, and lambdas.
- Test doubles live in `OrchestrationTestDoubles` (FakeEngine, SlowEngine, FailingEngine).
- `@TempDir` for file-based tests.
- Sparse files via `RandomAccessFile.setLength()` for large model stubs.
- Tags: `@Tag("integration")`, `@Tag("real-binary")` for gated tests.
- Run unit tests: `./gradlew test`
- Run all checks: `./gradlew check`
- Coverage report: `./gradlew test jacocoTestReport` (see `build/reports/jacoco/test/html/`)
- Coverage gates: 90% instruction / 80% branch minimum.

## Build Commands

| Command | Purpose |
|---------|---------|
| `./gradlew test` | Unit tests (excludes integration, real-binary) |
| `./gradlew integrationTest` | Integration tests only |
| `./gradlew check` | Full verification (unit + integration + Checkstyle + SpotBugs) |
| `./gradlew test jacocoTestReport` | Coverage report |
| `./gradlew bootRun` | Run the application |
| `./gradlew dependencyCheckAnalyze` | OWASP dependency scan |
| `./gradlew cyclonedxBom` | Generate SBOM |

## Pull Request Process

1. Create a feature branch from `main`.
2. Keep changes small and focused (one logical change per PR).
3. Add/update tests for all code changes.
4. Run `./gradlew check` locally before pushing.
5. PR description should explain the "why" not just the "what".
6. All CI checks must pass.

## Commit Message Format

Use conventional-style commits:

```
type: short description

Longer explanation if needed.
```

Types: `add`, `fix`, `update`, `refactor`, `test`, `docs`

## Architecture Quick Reference

- Event-driven: Spring `ApplicationEventPublisher` (14 event types).
- Dual STT engines: Vosk (JNI, fast) + Whisper (subprocess, accurate).
- Strategy pattern: reconciliation, hotkey triggers, typing adapters.
- Thread pools: `sttExecutor` (STT processing) + `eventExecutor` (event handling).
- See `docs/developer-guide.md` for detailed architecture.
- See `docs/diagrams/` for visual documentation (~112 diagrams).

## Reporting Issues

File issues on GitHub with:

- Steps to reproduce.
- Expected vs actual behavior.
- macOS version and Java version.
- Relevant log output (from `logs/blckvox.log`).
