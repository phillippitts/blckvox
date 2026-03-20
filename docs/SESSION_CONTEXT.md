# Session Context & Recovery Guide

> **Note:** This is historical planning context from early project phases. Some details may not match the current implementation. For current architecture, see the [Architecture Overview](diagrams/architecture-overview.md).

**Date:** 2025-10-17 (Updated)
**Status:** Phases 0-5 Complete
**Implementation Duration:** Phase 0-1 complete, Phase 2 complete (9 tasks, ~5.75 hours)
**Grade:** 90/100 (Production-Ready MVP, observability deferred to Phase 6)

---

## Quick Recovery

If session is lost, start here:

1. **Read:** `docs/IMPLEMENTATION_PLAN.md` (49 tasks, ~51.5 hours)
2. **Review:** `docs/adr/*.md` (13 architectural decisions)
3. **Status:** Phases 0-2 complete ✅ (Environment, abstractions, dual STT engines, watchdog)
4. **Status:** Phases 0-5 complete

---

## What Was Accomplished

### Phase 0: Environment Setup ✅
- ✅ Model validation service with SHA256 checksums
- ✅ Log4j2 configuration with MDC and structured logging
- ✅ Audio format validation (16kHz, 16-bit PCM, mono)
- ✅ Thread pool configuration with MDC propagation

### Phase 1: Core Abstractions ✅
- ✅ TranscriptionResult domain model
- ✅ SttEngine interface (Adapter pattern)
- ✅ Exception hierarchy with context
- ✅ AudioValidator utility (implemented at `service.validation.AudioValidator`; integration into capture flow planned for Phase 3)

### Phase 2: STT Engine Integration ✅ (9 tasks, ~5.75 hours)
- ✅ Task 2.1: Model Validation Service (Vosk + Whisper fail-fast)
- ✅ Task 2.2: Vosk Recognizer Creation (JNI lifecycle)
- ✅ Task 2.3: Vosk Audio Processing (JSON parsing, confidence extraction)
- ✅ Task 2.4a/b: Whisper Process Management (ProcessFactory, I/O, timeouts)
- ✅ Task 2.5: WhisperSttEngine Implementation (temp WAV files)
- ✅ Task 2.6: Parallel Execution Test (concurrent Vosk + Whisper validation)
- ✅ Task 2.7a/b: Engine Watchdog (event-driven auto-restart with budget tracking)

### Implementation Artifacts
- **~1,261 tests, 0 failures** (Phase 2 snapshot was 110; current coverage is 99.6% instruction / 98.1% branch)
- **Hermetic test infrastructure** with ProcessFactory seam
- **Event-driven watchdog** with sliding window budget (3 restarts/60min)
- **Refactored complexity** - SttEngineWatchdog expanded from 166 to 213 lines with better separation of concerns
- **Production-ready logging** - PII-safe, structured, never logs full transcriptions at INFO

### Documentation (Phase 0 Planning)
1. **`docs/IMPLEMENTATION_PLAN.md`** - 40 tasks, streamlined Phase 2
2. **`docs/adr/*.md`** - 13 architectural decisions
3. **`docs/diagrams/*.md`** - Architecture and data flow diagrams
4. **`.junie/guidelines.md`** - 1,515 lines development playbook
5. **`build.gradle`** - 14 dependencies, Checkstyle, SpotBugs, JaCoCo, OWASP, PIT, CycloneDX

---

## Key Architectural Decisions

### 1. Dual-Engine STT Strategy (ADR-001)
**Decision:** Run Vosk + Whisper in parallel, reconcile results  
**Rationale:** Speed (Vosk ~100ms) + Accuracy (Whisper ~1-2s) + Resilience (fallback)  
**Trade-off:** 2x CPU usage acceptable for 2-5 seconds per transcription  
**Wall-clock latency:** max(vosk, whisper) not sum (~1-2s vs 3s sequential)

### 2. PostgreSQL (ADR-002) -- DEFERRED (Phase 6 / Future)
**Decision:** PostgreSQL with JSONB for flexible engine results (not yet implemented)
**Rationale:** ACID for audit logs, JSONB flexibility, Spring Data JPA simplicity
**Status:** Deferred. The current MVP has no database; all processing is ephemeral.

### 3. Manual Model Setup (ADR-003)
**Decision:** `./setup-models.sh` with SHA256 checksums  
**Rationale:** Fast clones (5MB vs 205MB), clear errors, CI-friendly  
**Rejected:** Git LFS (slow clones, costs), auto-download (silent failures)

### 4. Properties-Based Hotkeys (ADR-004)
**Decision:** Externalize via `application.properties` with factory pattern  
**Rationale:** User customization without recompilation, validated at startup  
**Extensibility:** Supports single-key, double-tap, modifier combinations

### 5. Log4j 2 Over Logback (ADR-005)
**Decision:** Use Log4j 2 for async, garbage-free logging  
**Rationale:** 2-10x faster, lambda support, hot reload config  
**Trade-off:** Non-standard for Spring, CVE vigilance required (OWASP scanning)

### 6. 2-Tier Event-Driven Architecture (ADR-006)
**Decision:** Presentation → Service (event-driven, no data layer)
**Rationale:** Clear boundaries, testability, Spring best practice; Data layer deferred to Phase 6
**Package Structure:**
  - `service/tray/` - System tray
  - `service/livecaption/` - Live caption UI
  - `service/` - Business logic, STT engines, orchestration

---

## Critical Design Patterns

1. **Strategy Pattern**: Reconciliation strategies (Simple, Confidence, Overlap)
2. **Factory Pattern**: HotkeyTriggerFactory
3. **Adapter Pattern**: VoskSttEngine (wraps JNI), WhisperSttEngine (wraps binary)
4. **Observer Pattern**: Hotkey events via Spring ApplicationEvent
5. **Template Method**: STT engine lifecycle (future)
6. **Decorator Pattern**: Logging, metrics, caching (Phase 6)
7. **Circuit Breaker**: Production resilience (Phase 6)

---

## Architectural Characteristics (Priority)

1. **Privacy** (Critical): 100% local processing, no cloud APIs
2. **Resilience** (Critical): Dual-engine fallback, 3-tier typing fallback
3. **Performance** (High): Parallel execution, sub-3-second latency
4. **Extensibility** (High): Strategy pattern for hotkeys/reconciliation
5. **Observability** (High): Log4j 2 + MDC, Prometheus metrics (Phase 6)
6. **Maintainability** (High): Clean Code enforced via Checkstyle

---

## Implementation Plan Summary

### ✅ Completed: Phases 0-2 (~13.5 hours)

**Phase 0: Environment Setup** ✅
- Model validation with SHA256 checksums
- Log4j2 with MDC and structured logging
- Audio format validation
- Thread pool configuration

**Phase 1: Core Abstractions** ✅
- TranscriptionResult, SttEngine interface, Exception hierarchy, AudioValidator utility (not yet integrated)

**Phase 2: STT Engine Integration (9 tasks)** ✅
- Vosk engine (JNI, per-call recognizer for thread-safety)
- Whisper engine (ProcessFactory, temp WAV files, robust timeouts)
- Parallel execution test (validates concurrent operation)
- Event-driven watchdog (auto-restart within sliding window budget)

### 🚧 Next: Phases 3-5 (~11.75 hours MVP)

**Phase 3: Parallel Development (4 tasks, 6 hours)**
- Task 3.1: AudioCaptureService with thread-safe buffer
- Task 3.2: HotkeyManager with JNativeHook
- Task 3.3: Hotkey configuration loading
- Task 3.4: FallbackManager (3-tier: paste/clipboard/notification)

**Phase 4: Integration (3 tasks, 2.25 hours)**
- Task 4.1: Vosk + audio integration
- Task 4.2: Hotkey orchestration (DictationOrchestrator)
- Task 4.3: Typing service with fallback

**Phase 5: Documentation (2 tasks, 3.5 hours)**
- Task 5.1: README with quickstart
- Task 5.2: Architecture diagram

**MVP Checkpoint:** Working end-to-end dictation

### 📋 Planned: Phase 6 (~20 hours Production)

**Phase 6: Production Hardening (12 tasks)**
- Monitoring & alerting (Prometheus, Grafana, SLOs)
- Circuit breakers (Resilience4j for cascading failure prevention)
- Distributed tracing (OpenTelemetry + Jaeger)
- Database connection pool tuning (HikariCP, if database added)
- Security hardening (OWASP, PII redaction)
- Load testing (100 TPS sustained, memory leak detection)
- Cost monitoring & right-sizing

---

## Critical Risks & Mitigations

| Risk | Probability | Mitigation | Status |
|------|-------------|------------|--------|
| Native library compatibility | Medium | Multi-arch CI (ARM64 + x86_64) | ✅ RESOLVED (Phase 0) |
| Model download failures | Medium | SHA256 checksums | ✅ RESOLVED (Phase 0) |
| Audio format mismatch | High | Explicit validation (16kHz/16-bit/mono) | ✅ RESOLVED (Phase 1) |
| JVM crashes from native code | Medium | Event-driven watchdog with auto-restart | ✅ RESOLVED (Phase 2) |
| Whisper process management | Medium | ProcessFactory, timeout handling | ✅ RESOLVED (Phase 2) |
| Thread pool exhaustion | Medium | Optimized thread pools, semaphores | ✅ RESOLVED (Phase 1-2) |
| Accessibility permission denial | Medium | 3-tier fallback (paste/clipboard/notify) | Planned (Phase 3) |
| Memory leaks (JNI) | Low | Automated stress tests (100 iterations) | Planned (Phase 6) |
| Cascading STT failures | Low | Circuit breakers (Resilience4j) | Planned (Phase 6) |
| PII leakage in logs | Low | PII redaction, truncation | Planned (Phase 6) |
| Dependency CVEs | Medium | OWASP + Dependabot automation | Planned (Phase 6) |

**Current Residual Risk: LOW-MEDIUM** (MVP functional, production hardening deferred)

---

## Technology Stack

### Core Framework
- Spring Boot 3.5.6
- Java 21 (toolchain)
- Gradle 8.x

### STT Engines
- Vosk 0.3.38 (com.alphacephei:vosk:0.3.38, Java API via JNI; runtime dependency: net.java.dev.jna:jna:5.16.0)
- Whisper (whisper.cpp native binary)

### Audio & Hotkeys
- Java Sound API (javax.sound.sampled)
- JNativeHook 2.2.2 (global hotkeys)
- java.awt.Robot (keystroke injection)

### Database
- None (ephemeral by design; database was evaluated and rejected per ADR-002)

### Logging
- Log4j 2 (spring-boot-starter-log4j2)
- Disruptor 3.4.4 (async appenders)

### Security
- OWASP Dependency Check (SCA, implemented in build.gradle — CVSS ≥ 7.0 fails build)
- Trivy (container scanning, planned for future containerized deployment)

### Testing
- JUnit 5 (Jupiter)
- Fakes, stubs, and lambdas (no Mockito in most tests)
- AssertJ (fluent assertions)
- Awaitility 4.2.2 (async testing)

---

## Dependencies (build.gradle)

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-log4j2'

    // STT
    implementation 'net.java.dev.jna:jna:5.16.0'
    implementation 'com.alphacephei:vosk:0.3.38'

    // Hotkeys
    implementation 'com.github.kwhat:jnativehook:2.2.2'

    // Utilities
    implementation 'org.json:json:20250107'
    implementation 'com.lmax:disruptor:3.4.4'

    // Observability
    implementation 'io.micrometer:micrometer-core'
    implementation 'io.micrometer:micrometer-registry-jmx'

    // Annotation Processing
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.awaitility:awaitility:4.2.2'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

## Configuration Template

### application.properties (Starter)

```properties
spring.application.name=blckvox
spring.main.web-application-type=none

stt.parallel.timeout-ms=120000
stt.reconciliation.enabled=true
stt.reconciliation.strategy=overlap
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.vosk.sample-rate=16000
stt.whisper.binary-path=tools/whisper.cpp/main
stt.whisper.model-path=models/ggml-base.en.bin
stt.whisper.output=json
stt.whisper.timeout-seconds=120

hotkey.type=double-tap
hotkey.key=RIGHT_META
hotkey.toggle-mode=true

logging.level.com.boombapcompile.blckvox=DEBUG
logging.level.org.springframework=INFO
```

---

## Model Setup (Critical First Step)

### create setup-models.sh

```bash
#!/bin/bash
set -e

MODELS_DIR="./models"
BIN_DIR="./bin/whisper.cpp"
mkdir -p "$MODELS_DIR" "$BIN_DIR"

# Expected checksums (SHA256)
VOSK_CHECKSUM="d3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3"
WHISPER_CHECKSUM="a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"

echo "📥 Downloading Vosk model (50 MB)..."
curl -L "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip" \
    -o "$MODELS_DIR/vosk.zip"
shasum -a 256 -c <<< "$VOSK_CHECKSUM *$MODELS_DIR/vosk.zip"
unzip -q "$MODELS_DIR/vosk.zip" -d "$MODELS_DIR"
rm "$MODELS_DIR/vosk.zip"

echo "📥 Downloading Whisper model (150 MB)..."
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin" \
    -o "$MODELS_DIR/ggml-base.en.bin"
shasum -a 256 -c <<< "$WHISPER_CHECKSUM *$MODELS_DIR/ggml-base.en.bin"

echo "🔧 Building Whisper binary..."
git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git /tmp/whisper-cpp
cd /tmp/whisper-cpp && make && cp main "$BIN_DIR/main" && cd -
chmod +x "$BIN_DIR/main"
rm -rf /tmp/whisper-cpp

echo "✅ Models ready!"
ls -lh "$MODELS_DIR"
```

**Execute:** `chmod +x setup-models.sh && ./setup-models.sh`

---

## Validation Gates

### Gate 1: After Phase 5 (MVP Complete)
**Question:** Does it work for a single user?

**Pass Criteria:**
- ✅ Can transcribe 5-sentence speech accurately
- ✅ Hotkey triggers reliably
- ✅ Text pastes into 5 different apps
- ✅ Fallback works when Accessibility denied
- ✅ CI tests pass on ARM64 + x86_64

### Gate 2: After Phase 6 (Production Ready)
**Question:** Can it survive production scale?

**Pass Criteria:**
- ✅ Load test passes 100 TPS for 10 minutes
- ✅ Memory leak test shows < 50MB growth
- ✅ Prometheus dashboards green
- ✅ Incident runbook dry-run passed
- ✅ Rollback procedure validated

---

## Timeline Estimates

### MVP (Solo Developer)
- Day 1: Phase 0-1 (Environment + Abstractions)
- Day 2: Phase 2-3 (Vosk + Parallel tracks)
- Day 3: Phase 4-5 (Integration + Docs)
- **Checkpoint:** User acceptance testing

### Production (Solo Developer)
- Day 4: Operations (Monitoring, backups, incidents)
- Day 5: Deployment (Canary, rollback)
- Day 6: Performance & Security

**Total: 6 work days (45.5 hours)**

### With 2 Developers (Parallelized)
- Developer A: Phase 0 → 1 → 2 = ~12 hours
- Developer B: Phase 3 (Tasks 3.1 + 3.4) = ~3.5 hours
- **Both:** Phase 4-6 = ~15.5 hours
- **Total elapsed:** ~18 hours (~2.5 days)

---

## Clean Code Standards (Enforced)

### Naming Conventions (MANDATORY)

**Classes:**
- ✅ GOOD: `TranscriptionResult`, `AudioCaptureService`, `SttEngine`
- ❌ BAD: `Manager`, `Helper`, `Processor`, `Handler`, `DataObject`

**Methods:**
- ✅ GOOD: `transcribe()`, `captureAudio()`, `isValid()`, `hasCompleted()`
- ❌ BAD: `doStuff()`, `process()`, `handle()`, `getData()` (for computed values)

**Variables:**
- ✅ GOOD: `audioBuffer`, `selectedEngine`, `reconciliationStrategy`
- ❌ BAD: `temp`, `tmp`, `data`, `txnRes`, `buf`, `mgr`

**Booleans:** Prefix with `is/has/can/should`

**Constants:** `UPPER_SNAKE_CASE` with full words: `MAX_AUDIO_SIZE_BYTES`

### Method Size Limits
- **Target:** 5-20 lines
- **Maximum:** 50 lines (extract beyond this)

### Class Size Limits
- **Target:** Under 200 lines
- **Maximum:** 500 lines (split beyond this)

### Dependency Injection
- **Required:** Constructor injection (final fields)
- **Forbidden:** Field injection (`@Autowired` on fields)

### Checkstyle Enforcement
**Blocking Violations:**
- Naming conventions
- Method length > 50 lines
- More than 3 parameters
- Field injection

---

## Testing Strategy

### Unit Tests
- JUnit 5 with fakes, stubs, and lambdas (no Mockito in most tests)
- Test doubles in `OrchestrationTestDoubles` (FakeEngine, SlowEngine, FailingEngine) and `WhisperTestDoubles`
- AssertJ for fluent assertions
- Naming: `shouldDoSomethingWhenCondition()`

### Integration Tests
- `@SpringBootTest` with real models loaded
- End-to-end flows validated

### Async Tests
- Awaitility for waiting on conditions
- `await().atMost(5, SECONDS).until(condition)`

### Memory Leak Tests
- 100-iteration stress test
- < 50MB growth acceptable
- JVisualVM profiling documented

### Load Tests
- Gatling or JMeter (Phase 6)
- 100 TPS sustained for 10 minutes
- p50 < 500ms, p95 < 2s validation

---

## Security Practices

### Input Validation
- Audio size: 1 KB - 10 MB
- Audio format: 16kHz, 16-bit, mono (explicit validation)
- WAV header validation (RIFF, WAVE)

### Audit Logging
- Synchronous appender (never async)
- 365-day retention for compliance
- IP anonymization (last octet zeroed)

### Secrets Management
- All passwords from environment variables
- No hardcoded credentials
- Redacted toString() for sensitive objects

---

## Privacy & GDPR (Future / Phase 6)

### Data Minimization
- Audio: ephemeral (not persisted)
- Transcriptions: not persisted (no database in current MVP)

### Right to Erasure (Planned)
Not yet implemented. There is no database in the current MVP; all data is ephemeral.