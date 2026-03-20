# ADR-005: Log4j 2 Over Logback

## Status
Accepted (2025-01-14)

## Context
Logging requirements:
- Structured logging with MDC (request correlation)
- Async logging for performance (transcription is CPU-bound)
- Separate audit log (GDPR compliance, synchronous writes)
- Hot reload configuration without restart

Spring Boot defaults to Logback (SLF4J binding).

## Decision
Use **Log4j 2** instead of Logback.

**Configuration:**
- Exclude `spring-boot-starter-logging`
- Include `spring-boot-starter-log4j2`
- Add Disruptor library for async appenders
- Separate appenders: Console, AsyncFile, AuditLog (synchronous)

```xml
<Logger name="com.boombapcompile.blckvox.audit" level="info" additivity="false">
    <AppenderRef ref="AuditLog"/>  <!-- NEVER async -->
</Logger>
```

## Consequences

### Positive
- ✅ **2-10x faster** async logging vs Logback
- ✅ **Garbage-free** logging reduces GC pauses
- ✅ **Lambda support**: `log.debug("Details: {}", () -> expensiveCall())`
- ✅ **Plugin architecture**: Custom appenders extensible
- ✅ **Hot reload**: `monitorInterval="30"` reloads config every 30s

### Negative
- ❌ **Non-standard for Spring**: Most projects use Logback
- ❌ **Configuration migration**: Logback → Log4j 2 syntax differs
- ❌ **Potential CVE history**: Log4Shell (CVE-2021-44228) requires vigilance

### Mitigation
- Log4j2 version managed by Spring Boot BOM (not explicitly pinned; currently 2.24.3 via Spring Boot 3.5.6)
- OWASP Dependency Check scans for vulnerabilities weekly
- Audit log uses synchronous appender (data integrity)
- MDC filter clears ThreadContext after requests (prevent leaks)

## Alternatives Considered

### Logback (Spring Boot Default)
- **Rejected**: Slower async performance
- **Advantage**: Zero configuration, Spring native
- **Disadvantage**: No garbage-free mode, slower lambda support

### JUL (Java Util Logging)
- **Rejected**: Poor performance, limited features
- **Advantage**: Built into JDK
- **Disadvantage**: No async support, weak formatting

### SLF4J Simple
- **Rejected**: No file output, no configuration
- **Advantage**: Minimal dependency
- **Disadvantage**: Console-only, no production use

## References
- Guidelines: Lines 1207-1494 (Logging Strategy)
- Configuration: `log4j2-spring.xml`
- CVE mitigation: OWASP Dependency Check (Task 0.2)
