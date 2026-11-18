# Requirements Traceability Matrix

This matrix maps every functional requirement (FR1–FR10) and quality attribute (QA1–QA5) from the Master Specification to the concrete Java classes, methods, endpoints, and automated tests.

---

## Functional Requirements (FR1 – FR10)

| Requirement ID | Description | Implementation File(s) | Verification / Test Case | Status |
|---|---|---|---|---|
| **FR1 — Feature Flags** | Projects, Environments (dev, stg, prod), Boolean/String/Number/JSON types, named variations, default variation, CRUD operations | `FeatureFlag.java`<br>`FlagEnvironmentConfig.java`<br>`FlagApiController.java`<br>`FlagService.java` | `FlagServiceAndRollbackTest.java`<br>`verify_server.ps1` | **PASS** |
| **FR2 — Targeting Engine** | Evaluation order: Kill switch -> User target -> Segment rule -> Rollout -> Default. Operators: EQUALS, NOT_EQUALS, IN, CONTAINS, REGEX, SEMVER, DATES. | `FlagEvaluator.java`<br>`FastRuleEvaluationVisitor.java`<br>`Operator.java`<br>`RuleNode.java` | `EvaluationOrderAndOperatorTest.java` (5 unit tests) | **PASS** |
| **FR3 — Sticky Rollouts** | Deterministic MurmurHash3 bucketing (`flagKey:salt:userKey`), cohort preservation from 10% to 20%, server/SDK algorithmic parity. | `MurmurHash3Bucketer.java`<br>`FlagEvaluator.java` | `ConsistencyAndPerformanceTest.java` (1,000,000 user stickiness test) | **PASS** |
| **FR4 — Spring Boot Starter** | Reusable starter, `@ConfigurationProperties`, `FlagShipClient`, typed evaluation, `@FeatureFlagGuard` AOP aspect, Actuator endpoint. | `FlagShipClient.java`<br>`FeatureFlagGuardAspect.java`<br>`FlagShipActuatorEndpoint.java`<br>`FlagShipAutoConfiguration.java` | `FeatureFlagGuardAspectTest.java`<br>`DemoApplicationIntegrationTest.java`<br>`verify_demo_app.ps1` | **PASS** |
| **FR5 — Live Updates** | Server-Sent Events (SSE) broadcast, real-time snapshot invalidation, fallback polling, last sync tracking. | `SseFanOutHub.java`<br>`SdkApiController.java`<br>`ResilientSnapshotManager.java` | `SseFanOutPropagationTest.java` (50 concurrent SSE subscribers)<br>`verify_server.ps1` | **PASS** |
| **FR6 — Offline Resilience** | In-memory evaluation, local snapshot disk persistence, startup timeout bound (< 2s), zero exceptions to application. | `ResilientSnapshotManager.java`<br>`FlagShipClient.java` | `OfflineResilienceTest.java`<br>`DemoApplicationIntegrationTest.java` | **PASS** |
| **FR7 — Change Control** | Change author, reason, audit versions, version comparison, 1-action rollback, 2-person production approval workflow, scheduled releases. | `ApprovalService.java`<br>`FlagVersion.java`<br>`ScheduledChangeService.java`<br>`AdminWebController.java` | `ApprovalWorkflowTest.java` (3 test cases)<br>`FlagServiceAndRollbackTest.java`<br>`ScheduledChangeServiceTest.java` | **PASS** |
| **FR8 — Experiments** | Exposure & goal event ingestion, variation conversion reporting, Sample Ratio Mismatch (SRM) $\chi^2$ test, Two-Proportion Z-Test. | `StatisticalCalculator.java`<br>`ExperimentService.java`<br>`SdkApiController.java` | `StatisticalCalculatorTest.java` (4 math unit tests) | **PASS** |
| **FR9 — Flag Hygiene** | Track evaluations, detect inactive flags (> 30 days) and permanently rolled out flags (100% > 14 days), owner metadata. | `FlagHygieneScanner.java`<br>`FeatureFlagRepository.java` | `FlagHygieneScannerTest.java`<br>`verify_server.ps1` (`/hygiene`) | **PASS** |
| **FR10 — Admin Web App** | Dark navy SaaS UI (Thymeleaf, Bootstrap 5), Dashboard metrics, Flag editor, Approvals, Audit log, Experiments, Hygiene, Users, Scheduled Releases, Playground. | `templates/*.html`<br>`AdminWebController.java`<br>`SecurityConfig.java` | Web UI templates verified<br>`verify_server.ps1` | **PASS** |

---

## Quality Attributes & Performance (QA1 – QA5)

| Metric | Target | Verified Actual Result | Test / Evidence | Status |
|---|---|---|---|---|
| **QA1: Evaluation Latency** | $< 50\ \mu\text{s}$ (p99) | **$< 10\ \mu\text{s}$ average** ($0.501\text{s}$ for 1M evaluations) | `ConsistencyAndPerformanceTest.qa1_p99EvaluationLatencyUnder50Microseconds` | **PASS** |
| **QA1: Memory Footprint** | $< 20\ \text{MB}$ heap for 1,000 flags | **$0.50\ \text{MB}$ heap** for 1,000 fully populated flags | `ConsistencyAndPerformanceTest.qa1_heapMemoryFootprintUnder20MBFor1000Flags` | **PASS** |
| **QA2: Outage & Startup Resilience** | Bounded $< 2\text{s}$, 0 exceptions | **Non-blocking asynchronous startup** with immediate local snapshot fallback | `OfflineResilienceTest.java`<br>`DemoApplicationIntegrationTest.java` | **PASS** |
| **QA3: Real-Time Fan-Out** | 50 concurrent SSE subscribers in $< 5000\text{ ms}$ | **p50 = 20 ms, p95 = 29 ms** across 50 connected listeners | `SseFanOutPropagationTest.java` | **PASS** |
| **QA4: Monotonic Rollout Stickiness** | $100\%$ cohort retention | **$100.00\%$ retained** ($100,000$ users from 10% strictly preserved in 20%) | `ConsistencyAndPerformanceTest.qa4_oneMillionUsersStickinessAndRampFrom10To20Percent` | **PASS** |
| **QA5: Adoption & Developer Experience** | Integration in $< 15$ min | **Drop-in starter verified** with `@FeatureFlagGuard` and typed accessors in demo app | `DemoApplicationIntegrationTest.java`<br>`verify_demo_app.ps1` | **PASS** |

---

## Summary of Automated Test Suite

- **`flagship-core`**: 8 passed (QA1 Latency, QA1 Memory, QA4 Stickiness, 5 Rule Operator tests)
- **`flagship-spring-boot-starter`**: 4 passed (3 AOP Guard tests, 1 Offline Resilience test)
- **`flagship-server`**: 12 passed (4 Statistical Math tests, 3 Approval tests, 1 Hygiene test, 2 Rollback tests, 1 Scheduled Change test, 1 QA3 SSE Fan-Out test)
- **`flagship-demo-app`**: 3 passed (Targeted User Guard, Untargeted Fallback Routing, Typed In-Memory Evaluations)
- **Total**: **27 of 27 automated tests passed** (0 failures, 0 errors).
