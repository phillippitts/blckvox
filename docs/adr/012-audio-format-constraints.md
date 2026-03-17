# ADR-012: Audio Format Constraints (16kHz/16-bit/Mono PCM)

## Status
Accepted (2025-01-16)

## Context
Both STT engines (Vosk and Whisper) require specific audio formats. Mismatched formats cause silent failures (garbage transcriptions) or exceptions. The application needs a single canonical format enforced at capture time to ensure reliable transcription across both engines.

## Decision
Standardize on **16kHz sample rate, 16-bit signed integer (PCM16LE), mono channel, little-endian byte order**.

**Architecture:**
- `AudioFormatConfig` — compile-time constants + startup validation
- `AudioValidator` — runtime checks on captured audio buffers
- `JavaSoundAudioCaptureService` — captures in this exact format

**Pipeline:**
- WAV headers added only for Whisper (temp file)
- Vosk receives raw PCM bytes directly

## Consequences

### Positive
- Both engines work with the same audio (no transcoding)
- Simple pipeline (capture → validate → process)
- Small buffer sizes (32KB/sec at 16kHz/16-bit/mono)
- PCM is universal (no codec dependencies)

### Negative
- No support for higher sample rates (some microphones capture at 44.1/48kHz — Java Sound downsamples)
- Mono only (can't leverage stereo for noise cancellation)
- 16-bit limits dynamic range (adequate for speech, not music)

### Mitigation
- Java Sound handles sample rate conversion transparently
- 16-bit is standard for speech recognition models worldwide
- Mono is sufficient since both engines are trained on mono audio

## Alternatives Considered

### 44.1kHz with Manual Downsampling
- **Rejected**: Added complexity, Java Sound handles this transparently

### OPUS/OGG Compressed Format
- **Rejected**: Both engines require uncompressed PCM, adds transcoding step

### 32-bit Float
- **Rejected**: Neither engine benefits, doubles buffer size

## References
- `AudioFormatConfig` (constants)
- `AudioValidator` (runtime validation)
- `JavaSoundAudioCaptureService` (capture)
- `VoskSttEngine` (raw PCM consumer)
- `WhisperSttEngine` (WAV file consumer)
