# ADR-010: Whisper Process Isolation

## Status
Accepted (2025-01-16)

## Context
Whisper.cpp is a C++ binary that must be integrated into the JVM-based blckvox application. Two integration options exist: JNI binding (the same approach used for Vosk) or subprocess execution via `ProcessBuilder`. Several factors complicate the JNI approach:
- Whisper models are large (142 MB - 1.5 GB), consuming significant native memory outside the Java heap
- whisper.cpp has its own threading model that conflicts with JVM thread management
- Native crashes in JNI take down the entire JVM with no opportunity for graceful recovery

## Decision
Run whisper.cpp as an **external subprocess** via `ProcessBuilder` rather than embedding it through JNI.

**Architecture:**
- `WhisperProcessManager` manages the subprocess lifecycle (start, monitor, destroy)
- `WhisperCommandBuilder` constructs the CLI arguments for the whisper.cpp binary
- `ProcessStreamHandler` captures stdout/stderr with bounded buffers
- `WhisperSttEngine` orchestrates the full transcription flow

**Flow:**
1. Temp WAV file written to disk from the captured audio buffer
2. `WhisperCommandBuilder` assembles the CLI invocation with model path, output format, and flags
3. `ProcessBuilder` spawns whisper.cpp as a child process
4. `ProcessStreamHandler` captures stdout (text or JSON) and stderr
5. `Process.waitFor()` enforces a configurable timeout with `destroyForcibly()` fallback
6. Temp file deleted in a finally block; stdout parsed into a transcript result

## Consequences

### Positive
- Process isolation: whisper.cpp crash does not crash the JVM
- Independent resource limits (OOM killer targets the child process only)
- Simple upgrade path: replace the binary, no JNI recompilation required
- Output format flexibility (text/JSON) via CLI flags
- Timeout enforcement via `Process.waitFor()` with `destroyForcibly()` fallback

### Negative
- ~50 ms overhead per invocation (process spawn + temp file I/O)
- Temp WAV file written to disk (security: audio briefly on filesystem)
- No streaming: must wait for full audio before processing

### Mitigation
- Temp files deleted in finally blocks to minimize filesystem exposure
- Stdout bounded to 1 MB (`stt.whisper.max-stdout-bytes`) to prevent unbounded memory growth
- Process timeout configurable (`stt.whisper.timeout-seconds`) to avoid hung processes

## Alternatives Considered

### JNI Binding (like Vosk)
- **Rejected**: Native crash takes down the entire JVM with no recovery
- **Advantage**: No process spawn overhead, potential for streaming
- **Disadvantage**: Complex cross-platform build, no stable Java binding for whisper.cpp, JVM-incompatible threading model

### gRPC Server Wrapper
- **Rejected**: Overkill for a single-process desktop application
- **Advantage**: Language-agnostic, supports streaming via bidirectional streams
- **Disadvantage**: Requires running a persistent sidecar process, adds gRPC dependency and proto management

## References
- `WhisperProcessManager` (subprocess lifecycle)
- `WhisperCommandBuilder` (CLI argument construction)
- `ProcessStreamHandler` (stdout/stderr capture with bounded buffers)
- `WhisperSttEngine` (transcription orchestration)
