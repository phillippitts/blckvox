# Troubleshooting Flowchart

## Master Troubleshooting Decision Tree

```mermaid
flowchart TD
    Start([Problem?]) --> Cat{What's the<br/>issue?}

    Cat -->|App won't start| StartIssue[App Won't Start]
    Cat -->|Hotkey not working| HotkeyIssue[Hotkey Not Working]
    Cat -->|No text appears| NoTextIssue[No Text Appears]
    Cat -->|Wrong transcription| WrongTextIssue[Wrong Transcription]
    Cat -->|Slow performance| SlowIssue[Slow Performance]

    StartIssue --> CheckModels
    HotkeyIssue --> CheckHotkey
    NoTextIssue --> CheckPermissions
    WrongTextIssue --> CheckAudio
    SlowIssue --> CheckConcurrency

    style Cat fill:#e1f5ff
    style StartIssue fill:#ffcdd2
    style HotkeyIssue fill:#ffe0b2
    style NoTextIssue fill:#fff4e1
    style WrongTextIssue fill:#f3e5f5
    style SlowIssue fill:#fff9c4
```

## Problem 1: App Won't Start

```mermaid
flowchart TD
    Start([App Won't Start]) --> Logs{Check logs<br/>for errors}

    Logs -->|Model not found| Models[Models Issue]
    Logs -->|Binary not found| Binary[Whisper Binary Issue]
    Logs -->|Other error| Generic[Generic Error]

    Models --> VoskMissing{Vosk model<br/>exists?}
    VoskMissing -->|No| DLVosk[Run ./setup-models.sh]
    VoskMissing -->|Yes| WhisperMissing{Whisper model<br/>exists?}
    WhisperMissing -->|No| DLWhisper[Run ./setup-models.sh]
    WhisperMissing -->|Yes| CheckPath[Verify paths in<br/>application.properties]

    Binary --> BinExists{Binary exists at<br/>configured path?}
    BinExists -->|No| BuildBin[Run ./build-whisper.sh<br/>with WRITE_APP_PROPS=true]
    BinExists -->|Yes| BinExec{Binary<br/>executable?}
    BinExec -->|No| FixPerms[chmod +x whisper-binary<br/>xattr -dr com.apple.quarantine]
    BinExec -->|Yes| BinWorks{Binary runs<br/>manually?}
    BinWorks -->|No| Rebuild[Rebuild whisper.cpp<br/>Check compiler errors]
    BinWorks -->|Yes| VerifyProps[Verify binary path<br/>in application.properties]

    Generic --> ViewFull[View full stack trace<br/>in logs]
    ViewFull --> Search[Search error in<br/>GitHub issues]

    DLVosk --> Retry1[Retry app start]
    DLWhisper --> Retry1
    CheckPath --> Retry1
    BuildBin --> Retry1
    FixPerms --> Retry1
    Rebuild --> Retry1
    VerifyProps --> Retry1

    Retry1 --> Success{App starts?}
    Success -->|Yes| Fixed([✅ Fixed])
    Success -->|No| Support([Contact Support<br/>Include logs])

    style DLVosk fill:#c8e6c9
    style DLWhisper fill:#c8e6c9
    style BuildBin fill:#c8e6c9
    style Fixed fill:#c8e6c9
    style Support fill:#ffcdd2
```

## Problem 2: Hotkey Not Working

```mermaid
flowchart TD
    Start([Hotkey Not Working]) --> Type{What hotkey<br/>type?}

    Type -->|Single-key| Single[Single-Key Debug]
    Type -->|Double-tap| Double[Double-Tap Debug]
    Type -->|Modifier combo| Combo[Modifier Combo Debug]

    Single --> Reserved{Key in reserved<br/>shortcuts?}
    Reserved -->|Yes| ChangeKey[Choose different key<br/>See reserved list]
    Reserved -->|No| Detect1{Hotkey detected<br/>in logs?}
    Detect1 -->|No| Perm1[Check Accessibility<br/>permission]
    Detect1 -->|Yes| Event1[Check HotkeyPressed<br/>event fired]

    Double --> Timing{Too fast or<br/>too slow?}
    Timing -->|Too fast| Increase[Increase threshold:<br/>hotkey.threshold-ms=400]
    Timing -->|Too slow| Decrease[Decrease threshold:<br/>hotkey.threshold-ms=200]
    Timing -->|Just right| Detect2{Hotkey detected<br/>in logs?}
    Detect2 -->|No| Perm2[Check Accessibility<br/>permission]
    Detect2 -->|Yes| Event2[Check HotkeyPressed<br/>event fired]

    Combo --> Mods{All modifiers<br/>pressed?}
    Mods -->|No| Guide[Press all modifiers<br/>THEN primary key]
    Mods -->|Yes| Detect3{Hotkey detected<br/>in logs?}
    Detect3 -->|No| Perm3[Check Accessibility<br/>permission]
    Detect3 -->|Yes| Event3[Check HotkeyPressed<br/>event fired]

    Perm1 --> GrantAccess[System Settings → Privacy<br/>→ Accessibility → Add app]
    Perm2 --> GrantAccess
    Perm3 --> GrantAccess

    Event1 --> AudioCheck[Check audio capture<br/>starts in logs]
    Event2 --> AudioCheck
    Event3 --> AudioCheck

    ChangeKey --> Restart1[Restart app]
    Increase --> Restart1
    Decrease --> Restart1
    Guide --> Restart1
    GrantAccess --> Restart1
    AudioCheck --> Restart1

    Restart1 --> TestAgain{Works now?}
    TestAgain -->|Yes| Fixed([✅ Fixed])
    TestAgain -->|No| Debug[Enable DEBUG logging<br/>Check raw key events]

    style Fixed fill:#c8e6c9
    style GrantAccess fill:#ffe0b2
    style Increase fill:#fff4e1
    style Decrease fill:#fff4e1
```

## Problem 3: No Text Appears

```mermaid
flowchart TD
    Start([No Text Appears]) --> Audio{Audio captured?}

    Audio -->|No| Mic[Microphone Issue]
    Audio -->|Yes| Trans{Transcription<br/>completed?}

    Mic --> MicPerm{Microphone<br/>permission?}
    MicPerm -->|No| GrantMic[Grant Microphone<br/>permission]
    MicPerm -->|Yes| MicWork{Mic works in<br/>other apps?}
    MicWork -->|No| SysMic[Check System Settings<br/>→ Sound → Input]
    MicWork -->|Yes| DeviceName[Check device name<br/>in application.properties]

    Trans -->|No| EngineErr[Engine Errors]
    Trans -->|Yes| Paste[Pasting Issue]

    EngineErr --> VoskErr{Vosk failed?}
    VoskErr -->|Yes| VoskLog[Check logs for<br/>Vosk error details]
    VoskErr -->|No| WhisperErr{Whisper failed?}
    WhisperErr -->|Yes| WhisperLog[Check logs for<br/>Whisper error/timeout]
    WhisperErr -->|No| BothFail[Both engines failed]

    VoskLog --> ModelIssue{Model load<br/>error?}
    ModelIssue -->|Yes| RedownloadVosk[Re-download Vosk model<br/>./setup-models.sh]
    ModelIssue -->|No| VoskOOM{Out of memory?}
    VoskOOM -->|Yes| CloseApps[Close other apps<br/>Free memory]

    WhisperLog --> Timeout{Timeout error?}
    Timeout -->|Yes| IncTimeout[Increase timeout:<br/>stt.whisper.timeout-seconds=120]
    Timeout -->|No| WhisperBin{Binary found<br/>and executable?}
    WhisperBin -->|No| RebuildWhisper[./build-whisper.sh]

    BothFail --> CheckAudioDur[Check audio duration<br/>Min: 250ms, Max: 5min]

    Paste --> AccPerm{Accessibility<br/>permission?}
    AccPerm -->|No| Limited[App in limited mode<br/>Text in clipboard only]
    AccPerm -->|Yes| PasteLog[Check paste logs<br/>for errors]

    Limited --> Manual[Manually paste<br/>from clipboard<br/>Cmd+V]
    PasteLog --> RobotFail{Robot paste<br/>failed?}
    RobotFail -->|Yes| ClipOnly[Fallback to<br/>clipboard only]
    RobotFail -->|No| AppFocus[Ensure target app<br/>has focus]

    GrantMic --> TestMic[Test microphone<br/>in Voice Memos]
    SysMic --> TestMic
    DeviceName --> TestMic
    RedownloadVosk --> TestAgain
    CloseApps --> TestAgain
    IncTimeout --> TestAgain
    RebuildWhisper --> TestAgain
    CheckAudioDur --> TestAgain
    Manual --> TestAgain
    ClipOnly --> TestAgain
    AppFocus --> TestAgain

    TestMic --> Retry{Try dictation<br/>again}
    TestAgain --> Retry

    Retry -->|Works| Fixed([✅ Fixed])
    Retry -->|Still broken| Support([Check logs<br/>Contact Support])

    style Fixed fill:#c8e6c9
    style Support fill:#ffcdd2
    style Limited fill:#ffe0b2
```

## Problem 4: Wrong Transcription

```mermaid
flowchart TD
    Start([Wrong Transcription]) --> Type{What kind<br/>of error?}

    Type -->|Missing words| Missing[Missing Words]
    Type -->|Wrong words| Wrong[Wrong Words]
    Type -->|No punctuation| Punct[No Punctuation]
    Type -->|Mixed language| Lang[Mixed Language]

    Missing --> Short{Audio too<br/>short?}
    Short -->|Yes| SpeakLonger[Speak longer<br/>Min 250ms required]
    Short -->|No| Noise{Background<br/>noise?}
    Noise -->|Yes| QuietPlace[Move to quiet place<br/>Close windows<br/>Mute notifications]
    Noise -->|No| Volume{Mic volume<br/>too low?}
    Volume -->|Yes| IncVolume[Increase mic input<br/>in System Settings]
    Volume -->|No| Engine1[Try different<br/>reconciliation strategy]

    Wrong --> Accent{Heavy accent<br/>or dialect?}
    Accent -->|Yes| AccentLimits[Known limitation<br/>Models trained on<br/>standard English]
    Accent -->|No| Fast{Speaking too<br/>fast?}
    Fast -->|Yes| SlowDown[Speak slower<br/>Enunciate clearly]
    Fast -->|No| Quality{Mic quality<br/>issue?}
    Quality -->|Yes| BetterMic[Use better microphone<br/>External USB mic]
    Quality -->|No| Engine2[Try different<br/>reconciliation strategy]

    Punct --> WhisperCheck{Using Whisper<br/>engine?}
    WhisperCheck -->|No| OnlyVosk[Vosk doesn't<br/>add punctuation]
    WhisperCheck -->|Yes| Enabled{Both engines<br/>enabled?}
    Enabled -->|No| EnableBoth[Set stt.reconciliation.enabled=true]
    Enabled -->|Yes| ReconcileCheck{Reconciliation<br/>using Whisper?}
    ReconcileCheck -->|No| ConfStrat[Use confidence or<br/>overlap strategy]

    Lang --> SetLang[Set language in config:<br/>stt.whisper.language=en]

    Engine1 --> TryConf1[Try: confidence<br/>stt.reconciliation.strategy=confidence]
    Engine2 --> TryConf2[Try: confidence<br/>stt.reconciliation.strategy=confidence]
    TryConf1 --> TryOverlap1[Or try: overlap<br/>stt.reconciliation.strategy=overlap]
    TryConf2 --> TryOverlap2[Or try: overlap<br/>stt.reconciliation.strategy=overlap]

    SpeakLonger --> Test
    QuietPlace --> Test
    IncVolume --> Test
    AccentLimits --> Accept[Accept limitation<br/>Edit manually]
    SlowDown --> Test
    BetterMic --> Test
    OnlyVosk --> Accept
    EnableBoth --> Test
    ConfStrat --> Test
    SetLang --> Test
    TryOverlap1 --> Test
    TryOverlap2 --> Test

    Test --> Better{Transcription<br/>improved?}
    Better -->|Yes| Fixed([✅ Fixed])
    Better -->|No| Support([May need<br/>manual editing])

    style Fixed fill:#c8e6c9
    style Accept fill:#fff9c4
    style Support fill:#ffe0b2
```

## Problem 5: Slow Performance

```mermaid
flowchart TD
    Start([Slow Performance]) --> Measure{How slow?}

    Measure -->|5+ seconds| VerySlow[Very Slow]
    Measure -->|2-5 seconds| MedSlow[Medium Slow]
    Measure -->|<2 seconds| Normal[Normal - Expected]

    VerySlow --> WhisperTimeout{Whisper<br/>timing out?}
    WhisperTimeout -->|Yes| LongAudio{Audio >30s?}
    LongAudio -->|Yes| ShorterClips[Speak in shorter clips<br/>Max recommended: 30s]
    LongAudio -->|No| IncTO[Increase timeout:<br/>stt.whisper.timeout-seconds=120]
    WhisperTimeout -->|No| BothRun{Both engines<br/>running?}

    MedSlow --> BothRun

    BothRun -->|Yes| Conditional{Conditional mode<br/>enabled?}
    Conditional -->|No| EnableCond[Enable conditional:<br/>stt.reconciliation.enabled=true<br/>stt.reconciliation.vosk-threshold=0.7]
    Conditional -->|Yes| Threshold{Threshold<br/>too low?}
    Threshold -->|<0.6| RaiseThresh[Raise threshold:<br/>stt.reconciliation.vosk-threshold=0.8<br/>More Vosk-only, less Whisper]
    Threshold -->|0.6-0.9| CheckCPU[Check CPU usage]

    CheckCPU --> HighCPU{CPU >80%?}
    HighCPU -->|Yes| LimitConc[Reduce concurrency:<br/>stt.concurrency.vosk-max=2<br/>stt.concurrency.whisper-max=1]
    HighCPU -->|No| CheckMem[Check memory usage]

    CheckMem --> LowMem{Memory low?}
    LowMem -->|Yes| CloseApps[Close other apps<br/>Free RAM]
    LowMem -->|No| DiskCheck[Check disk I/O<br/>Activity Monitor]

    Normal --> Explain[Dual-engine transcription<br/>is compute-intensive:<br/>Vosk: 100ms<br/>Whisper: 1-2s<br/>Total: max(both) + overhead]

    ShorterClips --> Retry
    IncTO --> Retry
    EnableCond --> Retry
    RaiseThresh --> Retry
    LimitConc --> Retry
    CloseApps --> Retry
    DiskCheck --> Retry

    Retry{Performance<br/>improved?}
    Retry -->|Yes| Fixed([✅ Fixed])
    Retry -->|No| Hardware([Consider hardware<br/>upgrade or reduce<br/>model size])

    Explain --> Expected([This is expected<br/>behavior])

    style Fixed fill:#c8e6c9
    style Expected fill:#c8e6c9
    style Hardware fill:#ffe0b2
```

## Quick Reference: Common Fixes

| Symptom | Most Likely Cause | Quick Fix |
|---------|-------------------|-----------|
| App won't start | Models not downloaded | `./setup-models.sh` |
| "Binary not found" error | Whisper not built | `WRITE_APP_PROPS=true ./build-whisper.sh` |
| Hotkey not responding | Accessibility permission | System Settings → Privacy → Accessibility |
| Double-tap too sensitive | Threshold too low | `hotkey.threshold-ms=400` |
| No audio captured | Microphone permission | Grant permission when prompted |
| No text pasted | Accessibility permission | Grant permission, restart app |
| Wrong transcription | Background noise | Move to quiet environment |
| Missing punctuation | Vosk-only mode | Enable reconciliation: `stt.reconciliation.enabled=true` |
| 5+ second transcription | Both engines always run | `stt.reconciliation.enabled=true` |
| Whisper timeout | Long audio or slow CPU | `stt.whisper.timeout-seconds=120` |

## Diagnostic Commands

### Check System State

```bash
# Verify models exist
ls -lh models/vosk-model-en-us-0.22
ls -lh models/ggml-base.en.bin

# Verify whisper binary
ls -lh tools/whisper.cpp/main
file tools/whisper.cpp/main  # Should show "Mach-O executable"

# Check binary permissions
xattr tools/whisper.cpp/main  # Should be empty (no quarantine)

# Test whisper binary manually
tools/whisper.cpp/main -m models/ggml-base.en.bin -f test-audio.wav

# Monitor app logs in real-time
tail -f logs/blckvox.log | grep -E "(ERROR|WARN|Vosk|Whisper|Hotkey)"

# Check system microphone
system_profiler SPAudioDataType | grep -A 10 "Input"
```

### Enable Debug Logging

Add to `application.properties`:

```properties
logging.level.com.boombapcompile.blckvox=DEBUG
logging.level.com.boombapcompile.blckvox.service.hotkey=TRACE
```

## When to Seek Help

```mermaid
flowchart LR
    Issue[Have an issue] --> Try[Try troubleshooting]
    Try --> Check{Issue resolved?}
    Check -->|Yes| Done([✅ Done])
    Check -->|No| Review{Reviewed all<br/>sections?}
    Review -->|No| MoreTrouble[Try other sections]
    Review -->|Yes| Logs[Collect logs]

    MoreTrouble --> Check

    Logs --> GitHub[Post GitHub issue:<br/>github.com/.../blckvox/issues]
    Logs --> Include[Include:<br/>- OS version<br/>- App version<br/>- Error logs<br/>- Steps to reproduce]

    style Done fill:#c8e6c9
    style GitHub fill:#e1f5ff
```

## Preventive Maintenance Checklist

- [ ] Models in correct locations (`models/` directory)
- [ ] Whisper binary built and executable
- [ ] Accessibility permission granted
- [ ] Microphone permission granted
- [ ] Application.properties paths are absolute
- [ ] Reserved shortcuts list reviewed
- [ ] Hotkey threshold tuned to your speed
- [ ] Reconciliation strategy chosen
- [ ] Logs directory writable
- [ ] Sufficient disk space (>500MB free)
- [ ] Sufficient RAM (>2GB free during transcription)
