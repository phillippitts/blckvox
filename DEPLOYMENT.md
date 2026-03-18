# Production Deployment Guide

This guide is for operators deploying blckvox in production environments.

## Table of Contents
1. [Pre-Deployment Checklist](#pre-deployment-checklist)
2. [Environment Configuration](#environment-configuration)
3. [Deployment Options](#deployment-options)
4. [Monitoring & Health Checks](#monitoring--health-checks)
5. [Logging](#logging)
6. [Security Hardening](#security-hardening)
7. [Performance Tuning](#performance-tuning)
8. [Backup & Recovery](#backup--recovery)
9. [Troubleshooting](#troubleshooting)

---

## Pre-Deployment Checklist

Before deploying to production, verify:

- [ ] Java 21 JRE installed on target system
- [ ] Models downloaded and checksums verified (`./setup-models.sh`)
- [ ] Whisper.cpp binary built for target architecture (`./build-whisper.sh`)
- [ ] Integration tests passed (`./gradlew integrationTest`)
- [ ] Application properties configured with **absolute paths**
- [ ] Production profile enabled (`spring.profiles.active=production`)
- [ ] Log rotation configured
- [ ] Process monitoring configured (`ps aux | grep blckvox`)
- [ ] Log file monitoring configured (`tail -f logs/blckvox.log`)

---

## Environment Configuration

### Critical: Use Absolute Paths in Production

**Problem**: Default `application.properties` uses relative paths that **will not work** when running as a system service.

**Solution**: Create environment-specific configuration.

#### Option 1: Environment Variables (Recommended)

Set these environment variables before starting the application:

```bash
export VOSK_MODEL_PATH=/opt/blckvox/models/vosk-model-en-us-0.22
export WHISPER_MODEL_PATH=/opt/blckvox/models/ggml-base.en.bin
export WHISPER_BINARY_PATH=/opt/blckvox/bin/whisper
export BLCKVOX_LOG_PATH=/var/log/blckvox
export SPRING_PROFILES_ACTIVE=production
```

Update `application.properties` to read from env vars:

```properties
stt.vosk.model-path=${VOSK_MODEL_PATH:models/vosk-model-en-us-0.22}
stt.whisper.model-path=${WHISPER_MODEL_PATH:models/ggml-base.en.bin}
stt.whisper.binary-path=${WHISPER_BINARY_PATH:tools/whisper.cpp/main}
logging.file.path=${BLCKVOX_LOG_PATH:/var/log/blckvox}
```

#### Option 2: External Properties File

Create `/etc/blckvox/application-production.properties`:

```properties
# Model paths (absolute)
stt.vosk.model-path=/opt/blckvox/models/vosk-model-en-us-0.22
stt.whisper.model-path=/opt/blckvox/models/ggml-base.en.bin
stt.whisper.binary-path=/opt/blckvox/bin/whisper

# Logging (production location)
logging.file.path=/var/log/blckvox

# Graceful shutdown
spring.lifecycle.timeout-per-shutdown-phase=30s

# Performance tuning
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2
stt.parallel.timeout-ms=120000

# Watchdog configuration
stt.watchdog.enabled=true
stt.watchdog.window-minutes=60
stt.watchdog.max-restarts-per-window=3
stt.watchdog.cooldown-minutes=10
```

Launch with:
```bash
java -jar blckvox.jar \
  -Dspring.profiles.active=production \
  --spring.config.additional-location=/etc/blckvox/application-production.properties
```

### Logging Configuration

**Critical Issue**: Default `log4j2-spring.xml` uses **INFO level** which is appropriate for most environments but may be too verbose for high-throughput production.

**Fix Required**: Create `log4j2-production.xml` in `/etc/blckvox/`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="APP_NAME">blckvox</Property>
        <Property name="LOG_PATTERN">
            %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] [%X{requestId}] [%X{userId}] %-5level %logger{36} - %msg%n
        </Property>
    </Properties>

    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>

        <RollingFile name="RollingFile"
                     fileName="/var/log/blckvox/blckvox.log"
                     filePattern="/var/log/blckvox/blckvox-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                <SizeBasedTriggeringPolicy size="100 MB"/>
            </Policies>
            <DefaultRolloverStrategy max="30"/>
        </RollingFile>

        <Async name="AsyncFile" bufferSize="512">
            <AppenderRef ref="RollingFile"/>
        </Async>
    </Appenders>

    <Loggers>
        <!-- PRODUCTION: Use INFO level (not DEBUG) -->
        <Logger name="com.boombapcompile.blckvox" level="info" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Logger>

        <Logger name="org.springframework" level="warn"/>
        <Logger name="org.springframework.web" level="warn"/>

        <Root level="info">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Root>
    </Loggers>
</Configuration>
```

Launch with:
```bash
java -jar blckvox.jar \
  -Dlogging.config=/etc/blckvox/log4j2-production.xml \
  -Dspring.profiles.active=production
```

---

## Deployment Options

### Option 1: systemd Service (Linux)

**File**: `/etc/systemd/system/blckvox.service`

See [deployment/systemd/blckvox.service](deployment/systemd/blckvox.service) for complete configuration.

Quick setup:
```bash
# Create deployment directory
sudo mkdir -p /opt/blckvox/{models,bin,logs}

# Copy JAR
sudo cp build/libs/blckvox-0.0.1-SNAPSHOT.jar /opt/blckvox/blckvox.jar

# Copy models
sudo cp -r models/* /opt/blckvox/models/

# Copy Whisper binary
sudo cp tools/whisper.cpp/main /opt/blckvox/bin/whisper
sudo chmod +x /opt/blckvox/bin/whisper

# Create blckvox user
sudo useradd -r -s /bin/false blckvox

# Set permissions
sudo chown -R blckvox:blckvox /opt/blckvox
sudo mkdir -p /var/log/blckvox
sudo chown blckvox:blckvox /var/log/blckvox

# Copy service file
sudo cp deployment/systemd/blckvox.service /etc/systemd/system/

# Reload systemd
sudo systemctl daemon-reload

# Enable and start
sudo systemctl enable blckvox
sudo systemctl start blckvox

# Check status
sudo systemctl status blckvox
```

**Service management**:
```bash
# Start
sudo systemctl start blckvox

# Stop
sudo systemctl stop blckvox

# Restart
sudo systemctl restart blckvox

# View logs
sudo journalctl -u blckvox -f

# View last 100 lines
sudo journalctl -u blckvox -n 100
```

### Option 2: macOS LaunchDaemon (System-Wide)

**File**: `/Library/LaunchDaemons/com.boombapcompile.blckvox.plist`

See [deployment/macos/com.boombapcompile.blckvox.plist](deployment/macos/com.boombapcompile.blckvox.plist) for complete configuration.

Quick setup:
```bash
# Create deployment directory
sudo mkdir -p /opt/blckvox/{models,bin,logs}

# Copy files (same as Linux)
sudo cp build/libs/blckvox-0.0.1-SNAPSHOT.jar /opt/blckvox/blckvox.jar
sudo cp -r models/* /opt/blckvox/models/
sudo cp tools/whisper.cpp/main /opt/blckvox/bin/whisper
sudo chmod +x /opt/blckvox/bin/whisper

# Clear quarantine
sudo xattr -dr com.apple.quarantine /opt/blckvox/bin/whisper

# Copy LaunchDaemon
sudo cp deployment/macos/com.boombapcompile.blckvox.plist /Library/LaunchDaemons/

# Load service
sudo launchctl load /Library/LaunchDaemons/com.boombapcompile.blckvox.plist

# Check if running
ps aux | grep blckvox
```

**Service management**:
```bash
# Start
sudo launchctl start com.boombapcompile.blckvox

# Stop
sudo launchctl stop com.boombapcompile.blckvox

# Unload (disable)
sudo launchctl unload /Library/LaunchDaemons/com.boombapcompile.blckvox.plist

# Reload (enable)
sudo launchctl load /Library/LaunchDaemons/com.boombapcompile.blckvox.plist

# View logs
tail -f /var/log/blckvox/blckvox.log
```

### Option 3: Docker Container (Future)

**Status**: Not yet implemented. Planned for Phase 6.

Challenges for desktop audio app in container:
- Accessing host microphone (requires `--device` mapping)
- Global hotkey detection (needs host key events)
- Typing to foreground app (needs host accessibility)

Recommendation: Use native systemd/LaunchDaemon for now.

---

## Monitoring & Health Checks

blckvox is a headless desktop application (`spring.main.web-application-type=none`) with no web server or actuator endpoints. Use the following methods to monitor the application:

### Process-Based Monitoring

```bash
# Check if the application is running
ps aux | grep blckvox | grep -v grep

# Check process uptime (macOS)
ps -o etime= -p $(pgrep -f blckvox)
```

### JMX Health MBean

The application registers a JMX MBean at `com.boombapcompile.blckvox:type=Health` that provides real-time health status. This is richer than process-level checks — it detects deadlocked engines, all-disabled states, and degraded operation.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `Status` | String | `UP`, `DEGRADED`, or `DOWN` |
| `Details` | String | Per-engine states and uptime |
| `LastCheckEpochMs` | long | Epoch millis of last health check |
| `UptimeMs` | long | JVM uptime in milliseconds |

**Querying via JConsole:**
1. Start the JVM with JMX enabled (or use `jcmd <pid> ManagementAgent.start`)
2. Connect JConsole to the process
3. Navigate to MBeans → `com.boombapcompile.blckvox` → `Health`

**Querying via command line (jmxterm):**
```bash
# Using jmxterm
echo "get -b com.boombapcompile.blckvox:type=Health Status" | \
  java -jar jmxterm.jar -l localhost:9010
```

### File-Based Heartbeat

The application writes a heartbeat file at a configurable interval (default: 30s). This is designed for init systems (systemd, launchd) and simple monitoring scripts that can check file freshness.

**Configuration:**
```properties
health.heartbeat.enabled=true
health.heartbeat.path=${java.io.tmpdir}/blckvox-heartbeat
health.heartbeat.interval-ms=30000
```

**File format:** `<epoch_ms> <STATUS>` (e.g., `1711234567890 UP`)

**Staleness contract:** If the file is not updated within `2 × interval-ms` (default: 60s), the application should be considered unhealthy.

**Checking heartbeat (macOS):**
```bash
# Check if heartbeat file was updated in the last 2 minutes
if [ -f /tmp/blckvox-heartbeat ] && \
   [ "$(( $(date +%s) - $(stat -f %m /tmp/blckvox-heartbeat) ))" -lt 120 ]; then
  echo "HEALTHY: $(cat /tmp/blckvox-heartbeat)"
else
  echo "UNHEALTHY: heartbeat stale or missing"
fi
```

**Checking heartbeat (Linux):**
```bash
# Check if heartbeat file was updated in the last 2 minutes
if find /tmp/blckvox-heartbeat -mmin -2 -print -quit 2>/dev/null | grep -q .; then
  echo "HEALTHY: $(cat /tmp/blckvox-heartbeat)"
else
  echo "UNHEALTHY: heartbeat stale or missing"
fi
```

**systemd integration (watchdog-style):**
```ini
# In blckvox.service [Service] section:
ExecStartPost=/bin/sh -c 'sleep 5 && test -f /tmp/blckvox-heartbeat'
ExecCondition=/bin/sh -c 'test -f /tmp/blckvox-heartbeat && \
  test "$(($(date +%%s) - $(stat -c %%Y /tmp/blckvox-heartbeat)))" -lt 120'
```

### Log Monitoring

```bash
# Tail live application logs
tail -f logs/blckvox.log

# Check for successful startup
grep "Started BlckvoxApplication" logs/blckvox.log

# Check for engine initialization
grep "engine initialized" logs/blckvox.log

# Check for errors
grep ERROR logs/blckvox.log | tail -20
```

### System Tray Verification

When the application is running correctly, a system tray icon appears in the macOS menu bar. The tray menu provides status information and controls (start/stop recording, toggle live captions, quit).

---

## Logging

### Log Locations

Default locations based on deployment method:

| Deployment Method | Log Location |
|-------------------|--------------|
| systemd | `/var/log/blckvox/blckvox.log` |
| macOS LaunchDaemon | `/var/log/blckvox/blckvox.log` |
| Docker | stdout (captured by Docker) |
| Manual | `./logs/blckvox.log` (relative to working directory) |

### Log Rotation

**Built-in rotation** (Log4j2):
- Max size: 100 MB per file
- Retention: 30 days
- Compression: gzip

**System log rotation** (Linux):

Create `/etc/logrotate.d/blckvox`:

```
/var/log/blckvox/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 blckvox blckvox
    sharedscripts
    postrotate
        systemctl reload blckvox > /dev/null 2>&1 || true
    endscript
}
```

### Log Levels by Component

Production logging (`INFO` level) includes:

| Component | Logged Events |
|-----------|---------------|
| Startup | Model validation, engine initialization, health check results |
| Transcription | Duration, character count, engine used (no actual text) |
| Watchdog | Engine restarts, cooldown triggers, recovery events |
| Errors | Exception stack traces, error context (engine, duration, exit codes) |
| Security | Permission denied events, accessibility issues |

**Privacy guarantee**: Full transcribed text is **never logged** at INFO level.

### Viewing Logs

```bash
# Tail live logs
sudo tail -f /var/log/blckvox/blckvox.log

# Search for errors
sudo grep ERROR /var/log/blckvox/blckvox.log

# Search for watchdog events
sudo grep "watchdog" /var/log/blckvox/blckvox.log

# Search for specific engine failures
sudo grep "engine=whisper" /var/log/blckvox/blckvox.log | grep FAIL

# View systemd journal (if using systemd)
sudo journalctl -u blckvox -f --since "1 hour ago"
```

---

## Security Hardening

### Principle of Least Privilege

1. **Run as dedicated user** (not root):
```bash
# Linux
sudo useradd -r -s /bin/false blckvox

# macOS (service runs as _blckvox)
sudo dscl . -create /Users/_blckvox
sudo dscl . -create /Users/_blckvox UserShell /usr/bin/false
```

2. **Restrict file permissions**:
```bash
# Application files (read-only for service user)
sudo chown -R root:blckvox /opt/blckvox
sudo chmod -R 750 /opt/blckvox

# JAR should not be writable
sudo chmod 640 /opt/blckvox/blckvox.jar

# Logs directory (writable by service)
sudo chown blckvox:blckvox /var/log/blckvox
sudo chmod 750 /var/log/blckvox
```

### Network Security

The application has no listening port (headless desktop app with `spring.main.web-application-type=none`). There are no HTTP endpoints, no actuator, and no network attack surface to protect.

### Dependency Scanning

```bash
# Check for CVEs in dependencies
./gradlew dependencyCheckAnalyze

# Update dependencies regularly
./gradlew dependencyUpdates
```

---

## Performance Tuning

### JVM Tuning

**Recommended JVM flags** for production:

```bash
java -jar blckvox.jar \
  -Xms512m \                          # Initial heap
  -Xmx2g \                            # Max heap (adjust based on load)
  -XX:+UseG1GC \                      # G1 garbage collector (good for responsiveness)
  -XX:MaxGCPauseMillis=200 \          # Target GC pause time
  -XX:+HeapDumpOnOutOfMemoryError \   # Debug OOM errors
  -XX:HeapDumpPath=/var/log/blckvox/heap-dump.hprof \
  -Dspring.profiles.active=production
```

### Concurrency Limits

Adjust based on CPU cores and expected load:

```properties
# Conservative (low-end machines, 2-4 cores)
stt.concurrency.vosk-max=2
stt.concurrency.whisper-max=1

# Balanced (4-8 cores)
stt.concurrency.vosk-max=4
stt.concurrency.whisper-max=2

# Aggressive (8+ cores, high throughput)
stt.concurrency.vosk-max=8
stt.concurrency.whisper-max=4
```

### Disabling Reconciliation

For **maximum speed** (50% faster), use single engine:

```properties
stt.reconciliation.enabled=false
stt.orchestration.primary-engine=vosk    # Vosk is faster (~100ms vs 1-2s)
```

Trade-off: Lower accuracy (no Whisper cross-validation).

---

## Backup & Recovery

### What to Back Up

**Critical files**:
- JAR: `/opt/blckvox/blckvox.jar`
- Models: `/opt/blckvox/models/*` (~2 GB)
- Configuration: `/etc/blckvox/application-production.properties`
- Whisper binary: `/opt/blckvox/bin/whisper`

**Not critical** (can be regenerated):
- Logs: `/var/log/blckvox/`
- Build artifacts: `build/`

### Backup Script Example

```bash
#!/bin/bash
# /usr/local/bin/backup-blckvox.sh

BACKUP_DIR=/backup/blckvox/$(date +%Y%m%d)
mkdir -p $BACKUP_DIR

# Backup application
tar -czf $BACKUP_DIR/app.tar.gz /opt/blckvox

# Backup config
cp /etc/blckvox/application-production.properties $BACKUP_DIR/

# Keep last 7 days
find /backup/blckvox -type d -mtime +7 -exec rm -rf {} +
```

### Recovery Procedure

```bash
# 1. Stop service
sudo systemctl stop blckvox

# 2. Restore files
sudo tar -xzf /backup/blckvox/20251020/app.tar.gz -C /
sudo cp /backup/blckvox/20251020/application-production.properties /etc/blckvox/

# 3. Verify permissions
sudo chown -R blckvox:blckvox /opt/blckvox
sudo chmod +x /opt/blckvox/bin/whisper

# 4. Start service
sudo systemctl start blckvox

# 5. Verify health
ps aux | grep blckvox | grep -v grep
tail -20 /var/log/blckvox/blckvox.log | grep "Started BlckvoxApplication"
```

---

## Troubleshooting

### Service Won't Start

**Diagnostic steps**:

```bash
# Check systemd status
sudo systemctl status blckvox

# View full logs
sudo journalctl -u blckvox -n 100 --no-pager

# Common issues:
# 1. Models not found - check paths
ls -lh /opt/blckvox/models/

# 2. Whisper binary not executable
ls -lh /opt/blckvox/bin/whisper
sudo chmod +x /opt/blckvox/bin/whisper

# 3. Permission denied
sudo chown -R blckvox:blckvox /opt/blckvox
```

### High Failure Rate

```bash
# Check logs for patterns
sudo grep FAIL /var/log/blckvox/blckvox.log | tail -20

# Common causes:
# - Timeout too low: increase stt.parallel.timeout-ms
# - Concurrency limit hit: increase stt.concurrency.* limits
# - Watchdog cooldown: wait 10 minutes or restart service
```

### Memory Issues

```bash
# Check process memory usage
ps aux | grep blckvox | grep -v grep

# Analyze heap dump (if OOM occurred)
jhat /var/log/blckvox/heap-dump.hprof

# Fix: Increase heap
# Edit service file, add: -Xmx4g
```

---

## Upgrading

### Safe Upgrade Procedure

```bash
# 1. Back up current version
sudo /usr/local/bin/backup-blckvox.sh

# 2. Test new version in staging
# (deploy to test system first)

# 3. Stop production service
sudo systemctl stop blckvox

# 4. Replace JAR
sudo cp blckvox-NEW.jar /opt/blckvox/blckvox.jar

# 5. Update models if needed
sudo cp -r models/* /opt/blckvox/models/

# 6. Update Whisper binary if needed
sudo cp tools/whisper.cpp/main /opt/blckvox/bin/whisper
sudo chmod +x /opt/blckvox/bin/whisper

# 7. Check configuration compatibility
# (review CHANGELOG for breaking changes)

# 8. Start service
sudo systemctl start blckvox

# 9. Verify health
ps aux | grep blckvox | grep -v grep
tail -20 /var/log/blckvox/blckvox.log | grep "Started BlckvoxApplication"

# 10. Monitor for issues
sudo journalctl -u blckvox -f
```

### Rollback Procedure

```bash
# 1. Stop service
sudo systemctl stop blckvox

# 2. Restore from backup
sudo tar -xzf /backup/blckvox/YYYYMMDD/app.tar.gz -C /

# 3. Start service
sudo systemctl start blckvox

# 4. Verify
ps aux | grep blckvox | grep -v grep
tail -20 /var/log/blckvox/blckvox.log | grep "Started BlckvoxApplication"
```

---

## Production Readiness Checklist

Before going live:

**Configuration**:
- [ ] Absolute paths configured for models and binaries
- [ ] Production profile enabled
- [ ] Logging level set to INFO (not DEBUG)
- [ ] Appropriate concurrency limits set

**Security**:
- [ ] Service runs as dedicated user (not root)
- [ ] File permissions restricted
- [ ] Network access restricted (firewall rules)
- [ ] Dependencies scanned for CVEs

**Monitoring**:
- [ ] Health checks automated
- [ ] Alerts configured (failure rate, latency, downtime)
- [ ] Logs forwarded to centralized system (optional)

**Reliability**:
- [ ] Service auto-starts on boot
- [ ] Graceful shutdown configured
- [ ] Watchdog enabled with reasonable limits
- [ ] Backup procedure tested
- [ ] Recovery procedure documented and tested

**Performance**:
- [ ] JVM heap size tuned
- [ ] Concurrency limits tested under load
- [ ] Log rotation configured
- [ ] Disk space monitored

---

## Getting Help

- **Operator Guide**: [docs/operator-guide.md](docs/operator-guide.md)
- **Runbooks**: [docs/runbooks/](docs/runbooks/)
- **Architecture**: [docs/diagrams/architecture-overview.md](docs/diagrams/architecture-overview.md)
- **Issue Tracker**: [GitHub Issues](https://github.com/your-org/blckvox/issues)
