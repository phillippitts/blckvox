# Runbook: Engine Failures

This runbook helps diagnose and resolve Vosk/Whisper failures in blckvox.

## Symptoms
- Repeated `TranscriptionException` from an engine (timeout, non‑zero exit, I/O error)
- Watchdog logs engine set to DEGRADED or DISABLED; auto‑restarts triggered
- Slow or missing Whisper output; CPU spike

## Quick Checks
1. Validate models/binary on disk
   - `./setup-models.sh` (models)
   - `./build-whisper.sh` (binary)
2. macOS quarantine (Whisper):
   - `xattr -dr com.apple.quarantine tools/whisper.cpp/main`
   - `chmod +x tools/whisper.cpp/main`
3. CPU and thread settings (Whisper):
   - Reduce `stt.whisper.threads` if CPU saturated
4. Timeout tuning:
   - Increase `stt.parallel.timeout-ms` (parallel) or `stt.whisper.timeout-seconds`

## Common Errors & Fixes
- Timeout
  - Increase timeouts and verify system isn’t CPU bound
  - Ensure audio clips aren’t excessively long (validator limits)
- Non‑zero exit
  - Check stderr snippet in exception message for clues
  - Verify binary/model paths and permissions
- I/O failure
  - Review logs for file/permissions and path issues

## Watchdog
- Auto‑restart budget: `stt.watchdog.max-restarts-per-window` over `stt.watchdog.window-minutes`
- Cooldown: `stt.watchdog.cooldown-minutes` (engine disabled until elapsed)
- Orchestrator falls back to the other engine when available

## Metrics to Monitor

Micrometer JMX metrics are already implemented via `MetricsEventListener`, which records:

- Engine failure counts (per engine)
- Engine restart counts (per engine)
- Typing fallback events
- Transcription confidence scores
- Capture state changes

These metrics are also observable via INFO-level log events.

## Escalation
- Enable DEBUG for whisper/vosk packages temporarily to capture more detail (avoid logging text at INFO)
- If issues persist, pin/upgrade whisper.cpp version via `GIT_REF` in `build-whisper.sh` and retest
