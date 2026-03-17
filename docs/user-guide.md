# User Guide

This guide helps you install, run, and use blckvox for local voice dictation on macOS.

## Prerequisites
- macOS 13+ (Ventura or later)
- Java 21
- Disk space: ~3 GB for STT models and tools
- Permissions: Microphone (capture) and Accessibility (typing via Robot)

## Quick Start
1) Download models (Vosk + Whisper):
```bash
chmod +x ./setup-models.sh
./setup-models.sh
```
2) Build Whisper binary:
```bash
chmod +x ./build-whisper.sh
WRITE_APP_PROPS=true ./build-whisper.sh
```
3) Build & run:
```bash
./gradlew clean build
./gradlew bootRun
```
4) Grant permissions when macOS prompts you:
- System Settings → Privacy & Security → Microphone
- System Settings → Privacy & Security → Accessibility (for paste/typing)

## Using the App
- Default hotkey: Right Command (RIGHT_META) double-tap
- Default mode: Toggle (press once to start recording, press again to stop and transcribe)
- The transcribed text is pasted into the active application.

## Changing the Hotkey

Create or edit `application-local.properties` in your application directory:
```properties
hotkey.type=double-tap            # single-key | double-tap | modifier-combo (default: double-tap)
hotkey.key=RIGHT_META             # e.g., RIGHT_META, F13, D
# hotkey.modifiers=META,SHIFT     # for modifier-combo (required for this type)
# hotkey.threshold-ms=300         # for double-tap (100-1000ms)
hotkey.toggle-mode=true           # true for click-to-toggle (default), false for push-to-talk
# hotkey.reserved=META+TAB,META+L # flag conflicts (platform-aware)
```

Then start the application with:
```bash
./gradlew bootRun --args='--spring.config.additional-location=./application-local.properties'
```
Notes:
- The app detects common OS-reserved shortcuts (e.g., Cmd+Tab) and logs a warning.
- If Accessibility permission is denied, a HotkeyPermissionDeniedEvent is published and logged.

## Dictation Flow
- Push-to-talk, whole-buffer model (no streaming):
  - Hotkey Press → Start capture
  - Hotkey Release → Stop capture, transcribe, paste
- Audio capture returns validated PCM16LE mono @ 16kHz.

## Enabling Reconciliation (optional)
Run both engines in parallel and reconcile the results:
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=overlap    # simple | confidence | overlap (default: overlap)
stt.reconciliation.overlap-threshold=0.6
```
- The orchestrator publishes a reconciled `TranscriptionCompletedEvent`.
- INFO logs show strategy, duration, and character count (no full text).

## Enabling Whisper JSON Mode (optional)
JSON mode provides richer tokens for better overlap reconciliation:
```properties
stt.whisper.output=json   # default
```
- The manager adds `-oj` to the CLI and caps stdout to prevent memory spikes.
- JSON is parsed safely; malformed JSON falls back gracefully.

When `stt.whisper.output=text` (text mode), Whisper does not produce per-segment confidence
scores. A fixed confidence value is used instead:
```properties
stt.whisper.text-mode-confidence=0.85   # default: 0.85 (range 0.0-1.0)
```
- Lower values cause the reconciler to prefer the other engine's result more often.
- This property has no effect when `stt.whisper.output=json`.

## Troubleshooting
- Hotkey not detected:
  - Check for OS-reserved conflict in logs; adjust `hotkey.*` properties.
  - Ensure Accessibility permission is granted.
- Typing doesn’t work:
  - Robot tier requires Accessibility; clipboard fallback works without it.
  - Use `typing.clipboard-only-fallback=true` to skip issuing paste shortcut.
- Microphone capture errors:
  - Check Microphone permission and device availability.
  - See logs for `CaptureErrorEvent` with reason.

## Support
- Logs are privacy-safe at INFO level (no full transcript).
- Check `logs/blckvox.log` for diagnostics and engine health events.
