# ADR-013: Reconciliation Strategy Selection

## Status
Accepted (2026-03-17)

## Context
The dual-engine STT architecture (ADR-001) produces two transcription results for each audio segment. Different use cases have different accuracy/latency trade-offs:

- **Live dictation** prioritises speed — a quick pick from the primary engine is sufficient.
- **Professional transcription** prioritises accuracy — comparing both results and selecting the best one reduces errors.
- **Mixed workloads** need a middle ground — use confidence scores to decide when to trust a single engine vs. reconcile.

A single fixed reconciliation strategy cannot serve all three scenarios well. The system needs pluggable strategies that users can switch via configuration.

## Decision
Provide three pluggable reconciliation strategies behind the `TranscriptReconciler` interface, selected at startup via `stt.reconciliation.strategy`:

1. **SIMPLE** (`SimplePreferenceReconciler`) — always returns the primary engine's result unless it is empty, then falls back to the secondary. Lowest latency, no comparison overhead.

2. **CONFIDENCE** (`ConfidenceReconciler`) — compares the confidence scores from both engines and returns the result with the higher score. Moderate overhead (both engines must complete).

3. **OVERLAP** (`WordOverlapReconciler`) — computes Jaccard word-overlap similarity between both results. If overlap exceeds the configurable threshold (`stt.reconciliation.overlap-threshold`), picks the higher-confidence result; otherwise treats them as divergent and picks the longer (more complete) result. Highest accuracy, highest latency.

Strategy instantiation is handled by `ReconciliationConfig`, which reads `ReconciliationProperties` and constructs the appropriate bean. The `TranscriptReconciler` interface has a single method — `reconcile(EngineResult vosk, EngineResult whisper)` — keeping implementations stateless and thread-safe.

Smart reconciliation (`stt.reconciliation.confidence-threshold`) adds a second decision layer: if the primary engine's confidence exceeds the threshold, skip the secondary engine entirely and return the primary result without reconciliation. This reduces resource usage when the primary engine is confident.

## Consequences

### Positive
- Users choose the trade-off that fits their workload via a single property.
- Each strategy follows SRP — small, testable, independent classes.
- New strategies can be added by implementing `TranscriptReconciler` and adding a `case` to `ReconciliationConfig`.
- The `AbstractReconciler` base class handles null-safety and shared edge cases.

### Negative
- Three code paths to maintain and test.
- No universal "best" default — OVERLAP is most accurate but slowest; SIMPLE is fastest but ignores the secondary engine.
- Users must understand trade-offs to choose well (mitigated by documentation in `application.properties` and `configuration-reference.md`).

## Alternatives Considered

- **Single fixed strategy**: Simpler code, but forces all users into one trade-off. Rejected because the dual-engine design specifically exists to offer flexibility.
- **Weighted token merge**: Merge tokens from both engines weighted by per-token confidence. Rejected as overly complex — neither Vosk nor Whisper provides reliable per-token confidence, and the implementation complexity is not justified by measurable accuracy gains.
- **Plugin JAR mechanism**: Load strategies from external JARs at runtime. Rejected as over-engineered — three built-in strategies cover known use cases, and adding a fourth requires only a new class + enum value.
