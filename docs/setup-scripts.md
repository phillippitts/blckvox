# Setup Scripts Reference

## `./setup-models.sh` - Download STT Models

**What it does:**
- Downloads Vosk model (vosk-model-en-us-0.22, ~1.8 GB)
- Downloads Whisper model (ggml-base.en.bin, ~147 MB)
- Verifies integrity with SHA-256 checksums
- Locks checksums to `models/checksums.sha256` for reproducibility

**Checksum verification:**
- **First run:** Computes and locks checksums
- **Subsequent runs:** Verifies against locked checksums (fails if files changed)
- **Enforce official checksums:** Set env vars before running:
  ```bash
  VOSK_SHA256=<official_sha256_for_zip> \
  WHISPER_SHA256=<official_sha256_for_bin> \
  ./setup-models.sh
  ```

**If checksums change legitimately:** Delete `models/checksums.sha256` and re-run (after verifying upstream source)

---

## `./build-whisper.sh` - Build whisper.cpp Binary

**What it does:**
- Clones `ggerganov/whisper.cpp` to `tools/whisper.cpp/`
- Checks out **v1.7.2** (pinned for reproducibility)
- Builds the `main` binary with parallel make
- Clears macOS quarantine and sets executable permissions
- Optionally auto-updates `application.properties` with binary path

**Usage:**
```bash
# Build with auto-update (recommended for onboarding)
WRITE_APP_PROPS=true ./build-whisper.sh

# Build without modifying properties (manual config)
./build-whisper.sh

# Use different version (testing upgrades)
GIT_REF=v1.8.0 ./build-whisper.sh

# Use latest main branch (not recommended for production)
GIT_REF=main ./build-whisper.sh
```

**Environment variables:**
- `WRITE_APP_PROPS=true` - Auto-update `application.properties` with binary path
- `GIT_REF=v1.7.2` - Pin to specific whisper.cpp version (default: v1.7.2)
- `INSTALL_DIR=./tools` - Where to clone/build whisper.cpp (default: `./tools`)
- `MAKE_JOBS=<N>` - Parallel make jobs (default: auto-detected CPU cores)

**Why v1.7.2 is pinned:**
- **Reproducibility:** Everyone gets the same binary across all environments
- **Stability:** v1.7.2 is a known stable release (Dec 2024)
- **Testability:** Phase 2 tests validated against this exact version
- **Security:** Enables vulnerability tracking and audit compliance

**Output:**
```
Building whisper.cpp (models already present)
OS: Darwin, Arch: arm64
Git ref: v1.7.2
whisper.cpp binary built: /Users/.../tools/whisper.cpp/main

Next steps:
1) Configure Spring Boot properties to use the built binary:
   stt.whisper.binary-path=/Users/.../tools/whisper.cpp/main
```

---

## External Dependencies

### whisper.cpp (Required for Whisper Engine)

- **Version:** v1.7.2 (pinned for reproducibility)
- **Repository:** https://github.com/ggerganov/whisper.cpp
- **Build Method:** `./build-whisper.sh` (automated)
- **Install Location:** `tools/whisper.cpp/`
- **Binary Location:** `tools/whisper.cpp/main`

**Upgrading whisper.cpp:**
```bash
# Test new version first
GIT_REF=v1.8.0 ./build-whisper.sh

# Verify tests still pass
./gradlew test

# If successful, update default in build-whisper.sh:
# GIT_REF=${GIT_REF:-"v1.8.0"}
```

### Vosk Models (Required for Vosk Engine)

- **Model:** vosk-model-en-us-0.22
- **Size:** ~1.8 GB
- **Source:** https://alphacephei.com/vosk/models
- **Download Method:** `./setup-models.sh` (automated)
- **Install Location:** `models/vosk-model-en-us-0.22/`
- **Checksum Verification:** `models/checksums.sha256`

### Whisper Models (Required for Whisper Engine)

- **Model:** ggml-base.en.bin
- **Source:** https://huggingface.co/ggerganov/whisper.cpp
- **Download Method:** `./setup-models.sh` (automated)
- **Install Location:** `models/ggml-base.en.bin`
- **Size:** ~147 MB
- **Checksum Verification:** `models/checksums.sha256`
