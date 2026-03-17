# Frequently Asked Questions (FAQ)

Common questions and answers about blckvox.

## Table of Contents

- [General](#general)
- [Setup and Installation](#setup-and-installation)
- [Models and Engines](#models-and-engines)
- [Hotkey Configuration](#hotkey-configuration)
- [Typing and Output](#typing-and-output)
- [Performance and Accuracy](#performance-and-accuracy)
- [Troubleshooting](#troubleshooting)
- [macOS-Specific](#macos-specific)
- [Advanced Configuration](#advanced-configuration)

---

## General

### What is blckvox?

blckvox is an offline voice dictation application for macOS that converts speech to text using push-to-talk hotkeys. Unlike cloud-based solutions, all processing happens locally on your machine, ensuring complete privacy.

### How does it work?

1. Press and hold a configured hotkey (e.g., Right ⌘)
2. Speak into your microphone
3. Release the hotkey
4. The transcribed text is automatically typed into your active application

### What makes it different from macOS built-in dictation?

| Feature | blckvox | macOS Dictation |
|---------|-------------|-----------------|
| **Privacy** | 100% offline | Sends audio to Apple servers |
| **Speed** | ~1-2 seconds | 3-5 seconds (network latency) |
| **Customization** | Full control over engines, models, hotkeys | Limited customization |
| **Accuracy** | Dual-engine reconciliation (10-25% better) | Single-engine |
| **Cost** | Free, open source | Free but data-mining concerns |

### Is it free?

Yes, blckvox is completely free and open source (license to be determined).

### What platforms are supported?

Currently macOS only (requires macOS 13+). Linux and Windows support may be added in the future.

---

## Setup and Installation

### How do I install blckvox?

See the [Setup Guide](../README.md#getting-started) for step-by-step instructions. Quick summary:

```bash
git clone https://github.com/your-org/blckvox.git
cd blckvox
./setup-models.sh
./gradlew bootRun
```

### What are the system requirements?

**Minimum:**
- macOS 13+ (Ventura or later)
- 4GB RAM
- 2GB free disk space (for models)
- Java 21 or later
- Microphone

**Recommended:**
- macOS 14+ (Sonoma or later)
- 8GB RAM
- 4-core CPU
- SSD storage

### Do I need an internet connection?

**Initial setup:** Yes, to download models (~500MB-2GB depending on choice).

**Runtime:** No, all transcription happens offline.

### How long does installation take?

- **Model download:** 5-15 minutes (depending on internet speed)
- **First build:** 2-5 minutes (Gradle dependency download)
- **Subsequent starts:** <30 seconds

### Can I use blckvox without macOS Accessibility permission?

Yes, but with limitations. Without Accessibility permission:
- Text is copied to clipboard (you must manually paste with ⌘+V)
- No automatic typing into active application

To enable clipboard-only mode:
```properties
typing.enable-robot=false
typing.clipboard-only-fallback=true
```

See [Accessibility Permission](#how-do-i-grant-accessibility-permission) for granting permission.

---

## Models and Engines

### Which STT engines does blckvox support?

Two offline engines:
1. **Vosk** (JNI-based, fast, moderate accuracy)
2. **Whisper** (process-based, slower, high accuracy)

### Which engine should I use?

Both engines are always loaded as Spring beans. Use `stt.orchestration.primary-engine` and `stt.reconciliation.enabled` to control behavior.

**For speed:**
```properties
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false
```

**For accuracy:**
```properties
stt.orchestration.primary-engine=whisper
stt.reconciliation.enabled=false
```

**For best results (recommended):**
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
```

### What models are available?

**Vosk Models:**
| Model | Size | Speed | Accuracy | Recommended For |
|-------|------|-------|----------|-----------------|
| `vosk-model-en-us-0.22` | 1.8GB | Fast | Excellent | Daily use (default) |

**Whisper Models:**
| Model | Size | Speed | Accuracy | Recommended For |
|-------|------|-------|----------|-----------------|
| `ggml-tiny.en.bin` | 75MB | Very Fast | Fair | Testing only |
| `ggml-base.en.bin` | 142MB | Fast | Good | Daily use (default) |
| `ggml-small.en.bin` | 466MB | Moderate | Excellent | High accuracy needs |
| `ggml-medium.en.bin` | 1.5GB | Slow | Superior | Professional use |

### How do I change models?

1. Download desired model using `./setup-models.sh` or manually
2. Update `application.properties`:
   ```properties
   stt.vosk.model-path=models/vosk-model-en-us-0.22
   stt.whisper.model-path=models/ggml-small.en.bin
   ```
3. Restart application

### Can I use non-English models?

Yes! Example for Spanish:
```properties
stt.whisper.model-path=models/ggml-base.bin  # Multi-language model
stt.whisper.language=es
```

See [Vosk Models](https://alphacephei.com/vosk/models) and [Whisper Models](https://github.com/ggerganov/whisper.cpp#models) for available languages.

### Where are models stored?

By default: `blckvox/models/`

You can change this via `application.properties`:
```properties
stt.vosk.model-path=/path/to/your/vosk-model
stt.whisper.model-path=/path/to/your/whisper-model
```

### How do I verify model checksums?

The `setup-models.sh` script automatically verifies checksums. Manual verification:

```bash
shasum -a 256 models/ggml-base.en.bin
# Compare output with official checksum from whisper.cpp releases
```

---

## Hotkey Configuration

### How do I change the hotkey?

Edit `application.properties`:

**Single key (e.g., Right Command):**
```properties
hotkey.type=single-key
hotkey.key=RIGHT_META
```

**Combination (e.g., ⌘+Shift+M):**
```properties
hotkey.type=modifier-combo
hotkey.key=M
hotkey.modifiers=META,SHIFT
```

See [Configuration Reference](configuration-reference.md#hotkey-configuration) for all options.

### What keys can I use?

- **Letters:** A-Z
- **Numbers:** 0-9
- **Special:** SPACE, ENTER, TAB, ESCAPE
- **Modifiers:** SHIFT, CTRL, ALT, META (⌘), LEFT_META, RIGHT_META

Full list: [JNativeHook Key Codes](https://github.com/kwhat/jnativehook/wiki/Key-Event-Codes)

### Why is my hotkey not working?

**Common causes:**

1. **Conflicting OS shortcut:**
   ```
   WARN: Hotkey META+TAB conflicts with system shortcut (app switcher)
   ```
   **Fix:** Choose a different hotkey

2. **Accessibility permission denied:**
   ```
   ERROR: Failed to register global hotkey
   ```
   **Fix:** Grant permission in System Preferences → Security & Privacy → Privacy → Accessibility

3. **Invalid key name:**
   ```
   IllegalArgumentException: Unknown key: CMD
   ```
   **Fix:** Use `META` instead of `CMD`

### Can I use the same hotkey for press and release?

Yes, that's the default behavior (push-to-talk). Hold = recording, release = transcribe.

### Can I use a click-to-toggle mode instead of hold?

Yes! Toggle mode is supported. Enable it with:
```properties
hotkey.toggle-mode=true
```

With toggle mode enabled:
- First hotkey press starts recording
- Second hotkey press stops recording and transcribes
- Hotkey release events are ignored

---

## Typing and Output

### Where does the transcribed text appear?

In the **currently focused application** where your cursor is active when you release the hotkey.

### Why isn't text being typed automatically?

**Most common cause:** Accessibility permission not granted.

**Fix:**
1. System Preferences → Security & Privacy → Privacy → Accessibility
2. Add your terminal app (Terminal.app or iTerm2) or the Java executable
3. Check the box to enable
4. Restart blckvox

**Alternative:** Use clipboard-only mode (see [Can I use blckvox without Accessibility permission?](#can-i-use-blckvox-without-macos-accessibility-permission))

### Can I copy to clipboard instead of typing?

Yes, configure clipboard-only mode:
```properties
typing.enable-robot=false
typing.clipboard-only-fallback=true
```

Text will be copied to clipboard; you manually paste with ⌘+V.

### Why is there an extra newline after transcription?

The STT engine may include a trailing newline. To remove it:
```properties
typing.trim-trailing-newline=true
```

### Can I change the paste shortcut?

Yes:
```properties
# macOS: ⌘+V (default)
typing.paste-shortcut=os-default

# Custom: Ctrl+V
typing.paste-shortcut=CTRL+V
```

### Why does pasting fail for long transcriptions?

Some apps have clipboard size limits. blckvox splits large texts into chunks:
```properties
typing.chunk-size=800          # Characters per chunk
typing.inter-chunk-delay-ms=30 # Delay between chunks
```

Increase `inter-chunk-delay-ms` if chunks are being lost.

---

## Performance and Accuracy

### How fast is transcription?

**Vosk (0.22 model):** 1-2 seconds for 5-second clip
**Whisper (base model):** 1-3 seconds for 5-second clip
**Reconciliation (both):** 1-3 seconds (parallel execution)

Actual speed depends on CPU, model size, and audio length.

### How accurate is transcription?

**Vosk (0.22 model):** ~90-93% accuracy (casual speech)
**Whisper (base model):** ~92-95% accuracy (casual speech)
**Reconciliation (confidence):** ~95-97% accuracy (10-25% improvement)

Accuracy varies by:
- Accent/pronunciation
- Background noise
- Microphone quality
- Speaking speed
- Model size

### How can I improve accuracy?

**1. Use larger models:**
```properties
stt.vosk.model-path=models/vosk-model-en-us-0.22
stt.whisper.model-path=models/ggml-small.en.bin
```

**2. Enable reconciliation:**
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence
```

**3. Improve audio quality:**
- Use external microphone (vs. built-in)
- Reduce background noise
- Speak clearly and at normal pace
- Position mic 6-12 inches from mouth

**4. Tune Whisper threads:**
```properties
stt.whisper.threads=8  # Match your CPU core count
```

### Why is transcription slow?

**Common causes:**

1. **Large model + low CPU:**
   **Fix:** Use smaller model (e.g., `ggml-base.en.bin` → `ggml-tiny.en.bin`)

2. **Concurrency limit reached:**
   ```
   WARN: Whisper concurrency limit reached
   ```
   **Fix:** Increase limit:
   ```properties
   stt.concurrency.whisper-max=4
   ```

3. **CPU throttling (laptop on battery):**
   **Fix:** Plug in power adapter

4. **Reconciliation enabled with slow engines:**
   **Fix:** Disable reconciliation:
   ```properties
   stt.reconciliation.enabled=false
   ```

### How much CPU/RAM does it use?

**Idle:** <1% CPU, ~200MB RAM

**Transcribing (Vosk small model):**
- CPU: 50-100% (1 core)
- RAM: ~300MB

**Transcribing (Whisper base model):**
- CPU: 200-400% (multi-core)
- RAM: ~500MB

**Reconciliation (both engines):**
- CPU: 300-500% (multi-core)
- RAM: ~700MB

---

## Troubleshooting

### Application won't start

**Error: `ModelNotFoundException`**
```
Caused by: java.io.FileNotFoundException: models/vosk-model-en-us-0.22
```
**Fix:** Run `./setup-models.sh` to download models

**Error: `UnsupportedClassVersionError`**
```
Unsupported class file major version 65
```
**Fix:** Install Java 21 or later
```bash
brew install openjdk@21
```

### Transcription fails

**Error: `Both engines failed or timed out`**
```
TranscriptionException: Both engines failed or timed out
```
**Fix:** Check logs for underlying error. Common causes:
- Model files corrupted → Re-download with `./setup-models.sh`
- Whisper binary missing → Run `./build-whisper.sh`

**Error: `Whisper transcription failed: timeout`**
```
TranscriptionException: Whisper timeout after 10000ms
```
**Fix:** Increase timeout:
```properties
stt.whisper.timeout-seconds=20
```

### Microphone not working

**Error: `Microphone permission denied`**
```
CaptureErrorEvent: Microphone permission denied
```
**Fix:**
1. System Preferences → Security & Privacy → Privacy → Microphone
2. Add Terminal.app (or your Java executable)
3. Restart application

**Error: `No audio captured`**
```
WARN: Audio clip too short (50ms < 250ms minimum)
```
**Fix:** Speak longer (minimum 250ms) or reduce threshold:
```properties
audio.validation.min-duration-ms=100
```

### Hotkey not registering

**Error: `Failed to register global hotkey`**
```
ERROR: Native hook registration failed
```
**Fix:** Grant Accessibility permission (see [macOS-Specific](#how-do-i-grant-accessibility-permission))

### Text not appearing in target app

**Symptom:** Transcription completes but no text appears

**Cause:** Target app doesn't have input focus

**Fix:** Click into the target app's text field before releasing hotkey

---

## macOS-Specific

### How do I grant Accessibility permission?

1. Open **System Preferences** (or **System Settings** on macOS 13+)
2. Navigate to **Security & Privacy** → **Privacy** → **Accessibility**
3. Click the lock icon (🔒) to unlock (requires admin password)
4. Click **+** and add:
   - **If running from IDE:** Add IntelliJ IDEA or Eclipse
   - **If running from terminal:** Add Terminal.app or iTerm2
   - **If running as packaged app:** Add the .app bundle
5. Check the box next to the added app
6. Restart blckvox

### How do I grant Microphone permission?

Same steps as Accessibility, but select **Privacy** → **Microphone** instead.

### Does blckvox work on Apple Silicon (M1/M2/M3)?

Yes, fully supported via Rosetta 2. Native ARM64 binaries are used when available (Vosk, Whisper.cpp).

### Can I use blckvox in Parallels/VMware?

Not recommended. Virtual machines have unreliable global hotkey capture and audio input.

---

## Advanced Configuration

### How do I enable reconciliation?

Both engines are loaded as Spring beans automatically. Edit `application.properties`:
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=confidence  # or: simple, overlap
stt.whisper.output=json  # Required for 'overlap' strategy
```

See [Configuration Reference - Reconciliation](configuration-reference.md#reconciliation-phase-4) for details.

### What's the difference between reconciliation strategies?

**Simple:** Always prefers primary engine unless empty
```properties
stt.reconciliation.strategy=simple
```
**Best for:** You trust one engine more than the other

**Confidence:** Selects result with higher confidence score
```properties
stt.reconciliation.strategy=confidence
```
**Best for:** Balanced accuracy (10-15% improvement)

**Overlap:** Uses Jaccard word similarity
```properties
stt.reconciliation.strategy=overlap
stt.reconciliation.overlap-threshold=0.6
stt.whisper.output=json
```
**Best for:** Maximum accuracy (20-25% improvement), but slower

### How do I monitor engine health?

**Watchdog logging:**
The `SttEngineWatchdog` monitors engine health automatically and logs status changes:
```
INFO  SttEngineWatchdog - Engine vosk: HEALTHY
WARN  SttEngineWatchdog - Engine whisper: DEGRADED (2 failures in window)
```

**Application logs:**
Check `logs/blckvox.log` for engine health events (`EngineFailureEvent`, `EngineRecoveredEvent`).

**Configuration:**
```properties
stt.watchdog.enabled=true
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

### How do I disable an engine?

Both engines are always loaded as Spring beans. To use only one engine, set it as primary and disable reconciliation:

**Use Vosk only:**
```properties
stt.orchestration.primary-engine=vosk
stt.reconciliation.enabled=false
```

**Use Whisper only:**
```properties
stt.orchestration.primary-engine=whisper
stt.reconciliation.enabled=false
```

### How do I run with a custom Spring profile?

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Create profile-specific config:
- `application-dev.properties`
- `application-prod.properties`

### Will Prometheus/Micrometer metrics be available?

Prometheus metrics and Micrometer integration are planned for Phase 6 (Production Hardening). Currently, observability is provided through structured Log4j 2 logging with MDC correlation IDs. See `docs/IMPLEMENTATION_PLAN.md` Phase 6 for details.

### Can I run blckvox as a background service?

Yes, use macOS Launch Agents:

1. Create `~/Library/LaunchAgents/com.boombapcompile.blckvox.plist`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
       <key>Label</key>
       <string>com.boombapcompile.blckvox</string>
       <key>ProgramArguments</key>
       <array>
           <string>/path/to/blckvox/gradlew</string>
           <string>bootRun</string>
       </array>
       <key>RunAtLoad</key>
       <true/>
       <key>KeepAlive</key>
       <true/>
   </dict>
   </plist>
   ```

2. Load the agent:
   ```bash
   launchctl load ~/Library/LaunchAgents/com.boombapcompile.blckvox.plist
   ```

---

## Still Having Issues?

**Check the logs:**
```bash
tail -f logs/blckvox.log
```

**Increase log verbosity:**
```properties
logging.level.com.boombapcompile.blckvox=DEBUG
```

**File a bug report:**
- GitHub Issues: https://github.com/your-org/blckvox/issues
- Include: OS version, Java version, error logs, `application.properties`

**Community support:**
- Discussions: https://github.com/your-org/blckvox/discussions
- Discord: https://discord.gg/blckvox (coming soon)

---

## See Also

- [Configuration Reference](configuration-reference.md) - Complete property reference
- [Developer Guide](developer-guide.md) - Architecture and development
- [README](../README.md) - Quick start and overview
- [Troubleshooting](../README.md#troubleshooting) - Common issues
