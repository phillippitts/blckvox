# Key Terms & Acronyms

## Core Technologies
- **STT** - Speech-to-Text: Technology that converts spoken audio into written text
- **JNI** - Java Native Interface: Bridge between Java and native C/C++ libraries (used by Vosk)
- **PCM** - Pulse Code Modulation: Uncompressed audio format (16kHz, 16-bit, mono)
- **MDC** - Mapped Diagnostic Context: Thread-local logging context for request correlation

## STT Engines
- **Vosk** - Fast, offline STT engine (~100ms latency, Kaldi-based)
- **Whisper** - Accurate STT engine from OpenAI (~1-2s latency, better punctuation)

## Audio Specifications
- **16kHz** - Sample rate required by both STT engines (models trained on this rate)
- **16-bit** - Bit depth for PCM audio (signed integers)
- **Mono** - Single audio channel (stereo not supported)
- **WAV** - Container format (RIFF/WAVE headers)

## Architecture & Patterns
- **ADR** - Architectural Decision Record: Document capturing key design decisions
- **DTO** - Data Transfer Object: Object for passing data between layers
- **SLA** - Service Level Agreement: Performance guarantees (e.g., p95 latency < 2s)
- **SLO** - Service Level Objective: Target reliability metrics (e.g., 99.9% uptime)
- **SLI** - Service Level Indicator: Measured metric (error rate, latency)

## Development & Operations
- **CI/CD** - Continuous Integration/Continuous Deployment: Automated build and deploy pipeline
- **OWASP** - Open Web Application Security Project: Security standards and tools
- **CVE** - Common Vulnerabilities and Exposures: Security vulnerability identifier
- **GDPR** - General Data Protection Regulation: EU privacy law (90-day retention, right to erasure)
- **HIPAA** - Health Insurance Portability and Accountability Act: US healthcare privacy law

## Testing
- **TDD** - Test-Driven Development: Write tests before implementation
- **UAT** - User Acceptance Testing: End-user validation of features
- **JMH** - Java Microbenchmark Harness: Performance benchmarking framework

## Metrics & Monitoring
- **p50/p95/p99** - Percentile latency: 50th/95th/99th percentile response times
- **RTO** - Recovery Time Objective: Maximum acceptable downtime (e.g., 4 hours)
- **RPO** - Recovery Point Objective: Maximum acceptable data loss (e.g., 1 hour)
- **MTTR** - Mean Time to Recovery: Average time to restore service after failure

## blckvox Domain Terms
- **Reconciliation** - Process of selecting final text when Vosk and Whisper disagree
- **Fallback Manager** - System that gracefully degrades when Accessibility permission denied
- **PcmRingBuffer** - Thread-safe ring buffer for captured microphone audio
- **Model Validation** - Startup check ensuring STT models are present and loadable
