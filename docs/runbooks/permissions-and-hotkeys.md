# Runbook: Permissions & Hotkeys

This runbook helps resolve issues with permissions (Microphone/Accessibility) and hotkeys.

## Permissions
### Microphone (capture)
- Symptom: Capture errors in logs (e.g., `MIC_PERMISSION_DENIED`), empty/short audio
- Resolution:
  - System Settings → Privacy & Security → Microphone → Enable for your Java runtime/IDE
  - Restart the application after granting permission

### Accessibility (typing via Robot)
- Symptom: Hotkey register fails (permission denied), Robot typing fails, only clipboard tier works
- Resolution:
  - System Settings → Privacy & Security → Accessibility → Enable for your Java runtime/IDE
  - Restart the application after granting permission
- Note: Clipboard fallback works without Accessibility permission

## Hotkeys
### Not detected
- Check logs for `HotkeyPermissionDeniedEvent` (Accessibility) and `HotkeyConflictEvent` (OS-reserved)
- Avoid reserved combos like `META+TAB` (Cmd+Tab on macOS) and `META+L` (Win+L on Windows)
- Reconfigure in `application.properties`:
```properties
hotkey.type=double-tap            # single-key | double-tap | modifier-combo
hotkey.key=RIGHT_META
# hotkey.modifiers=META,SHIFT
# hotkey.threshold-ms=300
# hotkey.reserved=META+TAB,META+L
```

### Double‑tap tuning
- If taps are missed, adjust `hotkey.threshold-ms` (e.g., 250–350 ms)

### Modifier combinations
- Require all configured modifiers to be present. Normalize aliases (CMD → META) via KeyNameMapper.

## Diagnostics
- Enable DEBUG logs for hotkey packages to trace matching (`trigger=…`, `PRESSED/RELEASED` events)
- Error events are privacy‑safe and throttled:
  - `HotkeyPermissionDeniedEvent`
  - `HotkeyConflictEvent`
  - `CaptureErrorEvent`
