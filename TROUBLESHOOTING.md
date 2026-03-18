# Troubleshooting

## `./build-whisper.sh` fails with "make not found"

**macOS:**
```bash
# Install Xcode Command Line Tools
xcode-select --install
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get install -y build-essential git
```

## Whisper binary fails with "Operation not permitted" on macOS

The binary is quarantined. The script should clear this automatically, but if not:
```bash
xattr -dr com.apple.quarantine tools/whisper.cpp/main
chmod +x tools/whisper.cpp/main
```

## `./setup-models.sh` checksum mismatch

This means the downloaded file doesn't match the locked checksum:
```bash
# Option 1: Verify upstream source is legitimate, then re-lock
rm models/checksums.sha256
./setup-models.sh

# Option 2: Clean and re-download
rm -rf models/
./setup-models.sh
```

## Build fails with "whisper.cpp binary not found"

Different whisper.cpp versions put the binary in different locations. The script tries 4 locations:
- `tools/whisper.cpp/main` (older versions)
- `tools/whisper.cpp/bin/whisper` (newer versions)
- `tools/whisper.cpp/build/bin/whisper` (CMake builds)
- `tools/whisper.cpp/examples/cli/whisper` (example builds)

If all fail, try building manually:
```bash
cd tools/whisper.cpp
make clean
make -j$(nproc)  # or: make -j$(sysctl -n hw.ncpu) on macOS
```

## macOS Permissions

The app requires two macOS permissions:
- **System Settings -> Privacy & Security -> Accessibility** (for hotkey detection and typing)
- **System Settings -> Privacy & Security -> Microphone** (for audio capture)

See also: [Troubleshooting Guide](docs/diagrams/troubleshooting-guide.md) for diagnosis flowcharts.
