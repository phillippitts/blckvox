# Continuous Integration (CI)

This project uses GitHub Actions for automated testing.

## CI Pipeline

The CI pipeline runs on every push to `main` and on all pull requests.

### What It Does

**Single Job: Build & Test**
- Runs on: Ubuntu (latest), macOS 14 (Apple Silicon), and macOS 15
- Duration: ~3-4 minutes per platform
- Executes: `./gradlew clean build -x integrationTest`

**What's tested:**
- ✅ Code compiles on Linux and macOS (Apple Silicon)
- ✅ All all unit tests pass
- ✅ Checkstyle validates code style
- ✅ JAR builds successfully (55 MB)

**What's NOT tested in CI:**
- ❌ Integration tests (require STT models - 2 GB download)
- ❌ Vosk model loading
- ❌ Whisper.cpp binary execution

## Why Integration Tests Are Skipped

Integration tests require:
1. **STT Models** (~2 GB download)
   - Vosk model: 1.8 GB
   - Whisper model: 150 MB
2. **Whisper.cpp binary** (must be built from source)
3. **Audio hardware** (some tests use real microphone)

Running these in CI would:
- Take 8-10 minutes (vs 3-4 minutes now)
- Download 2 GB on every run (costs, slow)
- Be flaky (hardware dependencies)

**Solution**: Integration tests run locally before merging:
```bash
./setup-models.sh
./build-whisper.sh
./gradlew integrationTest
```

## Current CI Configuration

**File**: `.github/workflows/ci.yml`

```yaml
jobs:
  test:
    runs-on: [ubuntu-latest, macos-14, macos-15]
    steps:
      - Checkout code
      - Setup Java 21
      - Setup Gradle (with caching)
      - Build: ./gradlew clean build -x integrationTest
      - Upload JAR (Ubuntu only)
      - Upload test reports (on failure)
```

**Key flags**:
- `-x integrationTest` - Skip integration tests
- This still runs all unit tests
- This still runs checkstyle

**Design decisions**:
- No Gradle wrapper validation (removed due to GitHub Actions network reliability issues)
- We control the wrapper in our repo, so validation is less critical

## Running CI Locally

Exactly replicate what CI does:

```bash
./gradlew clean build -x integrationTest
```

Expected output:
```
> Task :test
> Task :integrationTest SKIPPED
> Task :build

BUILD SUCCESSFUL in 38s
```

## Common CI Failures

### 1. Checkstyle Violations

**Error**: `Checkstyle rule violations were found`

**Fix**:
```bash
# See violations
./gradlew checkstyleMain checkstyleTest

# View report
open build/reports/checkstyle/main.html
```

### 2. Unit Test Failures

**Error**: `N tests completed, 1 failed`

**Fix**:
```bash
# Run specific test
./gradlew test --tests FailingTestClass

# View report
open build/reports/tests/test/index.html
```

### 3. Compilation Errors

**Error**: `Compilation failed; see the compiler error output for details`

**Fix**: This means your code doesn't compile. Check the error messages.

### 4. JNativeHook Exception

**Not a failure**: The `RejectedExecutionException` at end of build is **harmless**.

It's from the global hotkey library shutting down during test cleanup. If tests pass, ignore it.

## Artifacts

### Build Artifacts (Ubuntu only)
- **File**: `build-artifacts/blckvox-0.0.1-SNAPSHOT.jar`
- **Size**: ~55 MB
- **Retention**: 30 days
- **How to download**: Actions tab → Workflow run → Artifacts section

### Test Reports (On failure only)
- **Files**: HTML reports and XML results
- **Retention**: 7 days
- **Contents**:
  - `build/reports/tests/test/` - HTML test report
  - `build/test-results/test/` - XML test results
  - `build/reports/checkstyle/` - Checkstyle reports

## Performance

### Typical Run Times

| Platform | Duration | Notes |
|----------|----------|-------|
| Ubuntu | 3-4 min | Faster platform |
| macOS-14 | 4-5 min | Apple Silicon (ARM64) |
| macOS-15 | 4-5 min | Apple Silicon |

All three run in parallel, so total wall time is ~4-5 minutes.

### What Takes Time

1. **Download dependencies** (first run): ~30s
2. **Compile Java**: ~10s
3. **Run all unit tests**: ~2-3 min
4. **Checkstyle**: ~5s
5. **Build JAR**: ~5s

Gradle caching makes subsequent runs faster.

## Gradle Caching

CI uses `gradle/actions/setup-gradle@v3` which automatically caches:
- Downloaded dependencies
- Gradle wrapper
- Build cache

This makes subsequent runs **much faster** (no re-downloading dependencies).

## Before You Push

**Pre-flight checklist**:

```bash
# 1. Does it build?
./gradlew clean build -x integrationTest

# 2. Do integration tests pass? (recommended)
./gradlew integrationTest

# 3. Any style violations?
./gradlew checkstyleMain checkstyleTest
```

If all three pass, CI will pass.

## Branch Protection

Recommended GitHub settings for `main` branch:

**Status checks**:
- ✅ Require status checks before merging
- ✅ Require "Build & Test (ubuntu-latest)" to pass
- ✅ Require "Build & Test (macos-14)" to pass
- ✅ Require "Build & Test (macos-15)" to pass
- ✅ Require branches to be up to date

**Additional**:
- ✅ Require pull request reviews (at least 1)
- ✅ Dismiss stale reviews on new commits

## Already Configured (Local Build)

These tools are configured in `build.gradle` and run during local builds, but are **not yet integrated into CI**:

1. **JaCoCo code coverage** — `./gradlew test jacocoTestReport` generates HTML/XML reports. Current: 95% instruction, 88% branch coverage.
2. **OWASP dependency-check** — `./gradlew dependencyCheckAnalyze` scans for CVEs. Fails build on CVSS >= 7.0. Suppression file at `config/owasp/suppression.xml`.
3. **SpotBugs** — `./gradlew spotbugsMain` performs bytecode-level static analysis (effort=MAX, reportLevel=MEDIUM).

## Future Improvements

**Possible enhancements** (not yet implemented):

1. **Add integration test job** (separate, optional)
   - Cache models between runs
   - Only run on main branch
   - Make it non-blocking

2. **CI coverage reporting**
   - Upload JaCoCo reports to Codecov
   - Require minimum coverage threshold
   - Track coverage trends

3. **CI security scanning**
   - Run OWASP/SpotBugs in CI pipeline
   - Trivy container scanning
   - Snyk vulnerability scanning

4. **Performance benchmarks**
   - JMH benchmarks
   - Track latency trends
   - Alert on regressions

**Why not now?**
- Keep CI fast and simple
- Focus on reliability over features
- Add complexity only when needed

## Troubleshooting

### "Action failed" but no error shown

**Check**:
1. Go to Actions tab
2. Click the failed workflow run
3. Click the failed job (Ubuntu or macOS)
4. Expand the "Build with Gradle" step
5. Scroll to see the actual error

### Gradle daemon warnings

**Warning**: `Deprecated Gradle features were used in this build`

**Impact**: None. This is informational, not an error.

**Fix** (optional):
```bash
./gradlew build --warning-mode all
```

### Out of disk space (rare)

**Error**: `No space left on device`

**Why**: GitHub Actions runners have limited space (~14 GB)

**Fix**: Not needed currently (our build is small)

### GitHub Actions network/cache errors

**Error**: `Cache service responded with 400` or `ETIMEDOUT` connecting to external services

**Why**: GitHub Actions infrastructure issues (transient)

**Impact**:
- Build will be slower (no cached dependencies)
- Build will still succeed
- These are warnings, not failures

**Fix**: None needed - these are GitHub infrastructure issues, not our code

## Getting Help

- **CI issues**: Check [GitHub Actions docs](https://docs.github.com/en/actions)
- **Build issues**: See [DEPLOYMENT.md](../DEPLOYMENT.md)
- **Test failures**: See test output in artifacts
- **Questions**: Open an issue with CI logs attached
