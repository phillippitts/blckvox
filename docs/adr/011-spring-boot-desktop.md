# ADR-011: Spring Boot for Desktop Application

## Status
Accepted (2025-01-16)

## Context
blckvox is a desktop application (macOS system tray, JavaFX overlay, local audio capture) but uses Spring Boot — typically a server-side framework. Need to justify this choice and document the trade-offs.

## Decision
Use Spring Boot with `spring.main.web-application-type=none` to leverage:

- **Dependency injection**: Constructor-based injection throughout the codebase
- **@ConfigurationProperties**: Typed config binding across 14 property classes
- **ApplicationEventPublisher**: Event-driven architecture with 15 event types
- **SmartLifecycle**: Ordered startup/shutdown for tray, hotkeys, live caption
- **@ConditionalOnProperty**: Feature toggling with 11 conditional beans
- **Thread pool management**: `sttExecutor`, `eventExecutor` via Spring-managed beans
- **Micrometer metrics**: JMX-based observability without a web server

**Configuration:**
```properties
spring.main.web-application-type=none
```

## Consequences

### Positive
- Rich DI container eliminates manual wiring and promotes testability
- Event system decouples components without custom infrastructure
- Lifecycle management ensures ordered startup/shutdown of system resources
- Property binding provides type-safe, validated configuration across 14 classes
- Conditional beans enable runtime feature toggling without code changes
- Proven ecosystem with extensive documentation and community support
- Easy testing with `@SpringBootTest` and slice testing

### Negative
- ~3-second startup overhead from framework initialization
- ~80 MB baseline memory footprint for the framework itself
- Dependency count (~40 transitive JARs) increases distribution size
- Slight overkill for a desktop application compared to lighter alternatives

### Mitigation
- Startup overhead is acceptable for a desktop app (not a microservice with cold-start SLAs)
- Memory overhead is dwarfed by STT models loaded at runtime (2+ GB)
- No web server is started (`web-application-type=none` eliminates Tomcat/Netty)
- Lazy initialization used where possible to reduce startup cost

## Alternatives Considered

### Plain Java with Manual DI
- **Rejected**: Would reinvent Spring features (lifecycle, events, property binding, conditional wiring)

### Micronaut
- **Rejected**: Smaller ecosystem, less mature event system

### Quarkus
- **Rejected**: Optimized for cloud-native workloads, not desktop applications

### Guice
- **Rejected**: No lifecycle management, no property binding

## References
- `BlckvoxApplication` (entry point, `web-application-type=none`)
- `ThreadPoolConfig` (executor bean definitions)
- `OrchestrationConfig` (conditional bean wiring)
- `SystemTrayManager` (SmartLifecycle implementation)
