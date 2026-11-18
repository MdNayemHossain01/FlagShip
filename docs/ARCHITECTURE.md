# FlagShip Architecture & Design Document

FlagShip is an enterprise-grade, self-hosted **Feature Flag and Gradual-Rollout Platform** built with **Java 21**, **Spring Boot 3.3.4**, and a high-performance **reusable Spring Boot Starter** (`flagship-spring-boot-starter`). It is architected around **local in-memory evaluation** (< 10 µs per check), **deterministic sticky bucketing**, and **complete offline resilience**.

---

## 1. High-Level System Architecture

FlagShip follows a decoupled client-server architecture:

```
+-----------------------------------------------------------------------------------+
|                            FlagShip Platform Ecosystem                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  +---------------------------+                +--------------------------------+  |
|  |     FlagShip Server       |                |   Client Spring Boot Services  |  |
|  |   (Admin UI + REST API)   |                |   (FlagShip Starter Embedded)  |  |
|  +---------------------------+                +--------------------------------+  |
|  | - Management Dashboard    |                | - In-Memory Snapshot Cache     |  |
|  | - Targeting Rule Builder  |  SSE Stream    | - MurmurHash3 Sticky Bucketer  |  |
|  | - 2-Person Approvals      | ------------>  | - Fast AST Rule Visitor        |  |
|  | - Audit Log & 1-Click Rev |  (Push Updates)| - @FeatureFlagGuard AOP Aspect |  |
|  | - Statistical Experiments |                | - Local Disk Snapshot Backup   |  |
|  | - Flag Hygiene Engine     | <------------  | - Actuator /actuator/flagship  |  |
|  +---------------------------+  Event Batch   +--------------------------------+  |
|                |                (Async Flush)                  |                  |
|                v                                               v                  |
|       [ H2 / PostgreSQL ]                             [ Local File System ]       |
|    (Persistence & Audit Log)                         (Offline Snapshot File)      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Multi-Module Project Structure

| Module | Description | Key Components |
|---|---|---|
| **`flagship-core`** | Framework-agnostic evaluation engine and data models | `MurmurHash3Bucketer`, `FastRuleEvaluationVisitor`, `FlagEvaluator`, `RuleNode`, `EvaluationContext` |
| **`flagship-spring-boot-starter`** | Reusable Spring Boot 3 starter dependency | `FlagShipClient`, `ResilientSnapshotManager`, `FeatureFlagGuardAspect`, `FlagShipActuatorEndpoint`, `FlagShipAutoConfiguration` |
| **`flagship-server`** | Management server, web UI, analytics, approvals | `AdminWebController`, `SdkApiController`, `ApprovalService`, `StatisticalCalculator`, `FlagHygieneScanner`, `SseFanOutHub` |
| **`flagship-demo-app`** | Sample e-commerce application consuming the starter | `CheckoutDemoController`, `@FeatureFlagGuard` examples, payment fallback routes |

---

## 3. Design Patterns Applied

### 3.1 Composite + Visitor Pattern (Targeting Rule AST)
Targeting rules are modeled as an Abstract Syntax Tree (AST):
- **Composite (`RuleNode`)**:
  - `LeafClause`: Matches user attributes (`country`, `appVersion`, `email`, `role`, `tier`) against operators (`EQUALS`, `IN`, `CONTAINS`, `REGEX`, `SEMVER_GT`, `DATE_BEFORE`, etc.).
  - `AndNode`: Evaluates child clauses with short-circuit boolean AND.
  - `OrNode`: Evaluates child clauses with short-circuit boolean OR.
  - `NotNode`: Inverts child evaluation.
- **Visitor (`FastRuleEvaluationVisitor`)**: Decouples evaluation logic from the node data structure. High performance, zero reflection during evaluation runtime.

### 3.2 Chain of Responsibility (Evaluation Pipeline)
`FlagEvaluator.evaluate()` enforces a strict, deterministic evaluation priority:
1. **Kill Switch / Disabled Check**: If the flag is disabled in the target environment, return `offVariation` immediately with reason `DISABLED`.
2. **Individual User Targeting**: Explicit user key overrides (e.g., QA tester `nayem1541` -> `"on"`). Returns `INDIVIDUAL_TARGET`.
3. **Segment Rules**: Ordered segment AST rules evaluated sequentially. First matching rule dictates variation with reason `SEGMENT_RULE`.
4. **Percentage Rollout**: Deterministic sticky bucketing across weighted variation cohorts. Returns `PERCENTAGE_ROLLOUT`.
5. **Default Variation**: Fallback to environment default variation with reason `DEFAULT_FALLBACK`.

### 3.3 Strategy Pattern (Operators & Bucketing)
- **Attribute Matching Strategy (`Operator`)**: Type-safe operator implementations handling SemVer string comparisons (`2.4.0` > `2.0.0`), ISO-8601 timestamps (`2026-11-18`), regex matching, set membership (`IN`), and exact equality.
- **Bucketing Strategy (`MurmurHash3Bucketer`)**: 32-bit x86 MurmurHash3 hashing to map `flagKey:salt:userKey` uniformly onto $[0, 100000)$.

### 3.4 Observer Pattern (Live SSE Updates)
- `SseFanOutHub` maintains thread-safe sets of active client `SseEmitter` subscriptions.
- When an administrator modifies a flag, approves a change, or rolls back a version, the event is immediately fanned out to all connected application instances within milliseconds.

### 3.5 Proxy / Decorator Pattern (AOP Method Guarding)
- `@FeatureFlagGuard(flag = "...", userKeyExpression = "#userId", fallbackMethod = "...")` is intercepted by `FeatureFlagGuardAspect`.
- Evaluates the flag in-memory using SpEL-extracted parameters. If enabled, the target method proceeds; if disabled, the aspect transparently routes execution to the declared fallback method or throws `FeatureFlagDisabledException`.

### 3.6 Command Pattern (Audit Log & 1-Action Rollback)
- Every state change in `FlagService` records a complete immutable snapshot in `FlagVersion` (author, timestamp, delta reason, full JSON config).
- Rollback is executed as a reverse Command: taking target version $N$, applying its state, and writing version $N+1$ with an audit link back to version $N$.

### 3.7 Builder Pattern (Evaluation Context)
- `EvaluationContext.builder(userId)` constructs immutable evaluation requests with typed attributes, operating system, IP, tenant ID, and custom metadata.

---

## 4. Deterministic Bucketing Algorithm

### The Rollout Invariant
When releasing features to users:
1. **Cohort Stickiness**: A user in the 10% rollout cohort must remain in the cohort when traffic is increased to 20%.
2. **Zero Drift**: Evaluation does not depend on random numbers or central server state.
3. **Cross-Platform Uniformity**: The server and client SDK compute identical buckets for the same `(flagKey, salt, userKey)`.

### Mathematical Formulation
$$\text{bucket} = \left| \text{MurmurHash3\_x86\_32}(\text{flagKey} + ":" + \text{salt} + ":" + \text{userKey}, \text{seed}=0) \right| \pmod{100\,000}$$

Cumulative thresholds are evaluated against $[0, 100000)$:
- $0\% \to 10\%$: Users with $\text{bucket} < 10\,000$ receive `"on"`.
- $10\% \to 20\%$: Users with $\text{bucket} < 20\,000$ receive `"on"`.
All users in the $[0, 10000)$ range are strictly preserved.

---

## 5. Offline Resilience Architecture

The client SDK guarantee: **Flag evaluation NEVER throws network exceptions or blocks user requests.**

```
+--------------------------------------------------------------+
|               Startup Sequence (Bounded to < 2s)             |
+--------------------------------------------------------------+
| 1. Load cached snapshot from disk file (instant).            |
| 2. Spawn async non-blocking HTTP fetch to FlagShip Server.   |
| 3. If server reachable within 2s -> update in-memory cache   |
|    and write snapshot file to disk.                          |
| 4. If server unreachable/offline -> retain disk snapshot     |
|    and log warning. Application boots with zero downtime.    |
| 5. Spawn background SSE stream with automatic reconnect      |
|    and exponential backoff polling fallback.                 |
+--------------------------------------------------------------+
```

---

## 6. Experiments & Statistical Analysis

FlagShip provides native A/B testing analytics:
- **Sample Ratio Mismatch (SRM) Detection**: Pearson's Chi-Square Goodness-of-Fit test compares observed sample counts against expected traffic split ratios:
  $$\chi^2 = \sum \frac{(O_i - E_i)^2}{E_i}$$
  If $p < 0.001$, FlagShip flags an SRM alert indicating traffic selection bias or tracking failures.
- **Conversion Rate & Hypothesis Testing**: Two-Proportion Pooled Z-Test:
  $$Z = \frac{\hat{p}_B - \hat{p}_A}{\sqrt{\hat{p}(1-\hat{p})\left(\frac{1}{n_A} + \frac{1}{n_B}\right)}}$$
  Calculates $p$-value, relative uplift percentage, and $95\%$ Confidence Interval.
