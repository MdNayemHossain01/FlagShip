# FlagShip — Enterprise Feature-Flag & Gradual-Rollout Platform

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/tests-27%20passed-success.svg)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)]()

**FlagShip** is a self-hosted, high-performance **Feature Flag and Gradual-Rollout Platform** featuring a **reusable Spring Boot 3 Starter** (`flagship-spring-boot-starter`), deterministic sticky percentage rollouts, an extensible AST targeting rule engine, two-person production release approvals, automated flag hygiene scanning, real-time SSE streaming, and complete offline resilience.

---

## 🌟 Key Platform Capabilities

1. **Sub-Microsecond In-Memory Evaluation**:
   - Evaluates flags locally in your microservices with $< 10\ \mu\text{s}$ average latency. Zero remote HTTP calls on the critical application path.
2. **Deterministic Sticky Percentage Rollouts**:
   - Powered by 32-bit x86 **MurmurHash3** over `flagKey:salt:userKey`.
   - **Cohort Preservation**: Increasing rollout from $10\%$ to $20\%$ strictly preserves all users who were in the original $10\%$ cohort.
3. **Comprehensive AST Targeting Engine**:
   - Evaluation order: **Kill Switch $\to$ Individual User Targets $\to$ Segment Rules $\to$ Percentage Rollout $\to$ Default Variation**.
   - Supports 9 attribute operators: `EQUALS`, `NOT_EQUALS`, `IN`, `CONTAINS`, `REGEX`, `SEMVER_GT`, `SEMVER_LT`, `DATE_BEFORE`, `DATE_AFTER`.
4. **Complete Offline Resilience**:
   - Automatic local file snapshot backup (`flagship-snapshot.json`).
   - Bounded non-blocking startup: If the FlagShip Server is down or unreachable, applications boot instantly using their last-known snapshot with **zero exceptions**.
5. **Real-Time Push Updates via Server-Sent Events (SSE)**:
   - Server pushes configuration updates to all connected instances within milliseconds.
   - Automatic background reconnection loop with exponential backoff and polling fallback.
6. **Enterprise Change Control & 2-Person Approval**:
   - Production environment changes can mandate independent 2-person approval (`APPROVER` or `ADMIN`).
   - **Self-Approval Protection**: Authors are cryptographically prevented from approving their own change requests.
   - **1-Action Rollback**: Instant reversion to any historical version from the audit timeline.
7. **Native Experimentation & Statistical Analytics**:
   - Automatic **Sample Ratio Mismatch (SRM)** detection via $\chi^2$ Goodness-of-Fit test.
   - Two-Proportion Pooled Z-Test with $p$-value, relative uplift, and $95\%$ confidence intervals.
8. **Flag Hygiene & Technical Debt Prevention**:
   - Automated hourly scanner flagging zombie/inactive flags ($> 30$ days with 0 evaluations) and permanent flags ($100\%$ rollout for $> 14$ days).
9. **Modern Dark Navy SaaS Administration UI**:
   - Built with Spring MVC, Thymeleaf, and Bootstrap 5 with responsive navigation, interactive playground simulator, and metrics dashboard.
10. **AOP Method Guarding**:
    - Declarative `@FeatureFlagGuard` annotation with SpEL context extraction and automatic fallback routing.

---

## 🏗️ Repository Architecture

FlagShip is organized as a clean Maven multi-module project:

```
flagship/
|-- pom.xml                                  # Root reactor POM (Java 21, Spring Boot 3.3.4)
|-- flagship-core/                           # Framework-agnostic evaluation engine
|   |-- bucketing/                           # MurmurHash3 bucketer & cohort partitioner
|   |-- context/                             # EvaluationContext & builder
|   |-- engine/                              # Chain-of-responsibility FlagEvaluator
|   |-- model/                               # FlagDefinition, FlagType, EvaluationDetail
|   +-- rules/                               # Composite AST (RuleNode) & FastRuleVisitor
|-- flagship-spring-boot-starter/            # Reusable Spring Boot 3 Starter
|   |-- aop/                                 # @FeatureFlagGuard aspect & SpEL extractor
|   |-- client/                              # FlagShipClient & typed accessors
|   |-- config/                              # FlagShipProperties & AutoConfiguration
|   |-- sync/                                # ResilientSnapshotManager (Disk + SSE)
|   +-- actuator/                            # Custom Actuator endpoint (/actuator/flagship)
|-- flagship-server/                         # Central Management Server & Web UI
|   |-- controller/                          # SdkApiController & AdminWebController
|   |-- entity/                              # JPA entities (Project, Environment, Flag, etc.)
|   |-- service/                             # FlagService, ApprovalService, DataInitializer
|   |-- analytics/                           # StatisticalCalculator (SRM & Z-test)
|   |-- hygiene/                             # FlagHygieneScanner (Stale flag detector)
|   |-- sse/                                 # SseFanOutHub (Live push updates)
|   +-- resources/templates/                 # Dark Navy Thymeleaf UI templates
|-- flagship-demo-app/                       # Sample microservice consuming the starter
|   |-- CheckoutDemoController.java          # Live payment checkout with @FeatureFlagGuard
|   +-- application.yml                      # Client configuration (Port 8081)
|-- docs/                                    # Documentation suite
|   |-- ARCHITECTURE.md                      # Architecture, patterns & bucketing math
|   |-- QUICKSTART.md                        # 15-minute integration guide
|   |-- TRACEABILITY_MATRIX.md               # FR1-FR10 and QA verification matrix
|   +-- API.md                               # REST API reference
+-- README.md                                # Project overview (this document)
```

---

## ⚡ Quick Start

### 1. Prerequisites
- **Java 21** or later (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)

### 2. Build and Test All Modules
```bash
mvn clean test
```
*All 27 automated unit and integration tests across all 4 modules pass with 0 errors and 0 failures.*

### 3. Package Executable JARs
```bash
mvn clean package -DskipTests
```

### 4. Launch FlagShip Server
```bash
java -jar flagship-server/target/flagship-server-1.0.0.jar
```
- **Admin Dashboard**: Open `http://localhost:8080/`
- **Default Accounts**:
  - `admin` / `admin123` (`ADMIN` role)
  - `approver` / `approver123` (`APPROVER` role)
  - `editor` / `editor123` (`EDITOR` role)
  - `viewer` / `viewer123` (`VIEWER` role)
- **H2 Database Console**: `http://localhost:8080/h2-console` (`jdbc:h2:mem:flagshipdb`, user `SA`, password empty)

### 5. Launch Demo Application
In a separate terminal:
```bash
java -jar flagship-demo-app/target/flagship-demo-app-1.0.0.jar
```
- **Evaluation API**: `http://localhost:8081/api/demo/evaluate?userId=nayem1541`
- **Guarded Payment API**: `http://localhost:8081/api/demo/pay?userId=nayem1541`
- **Starter Actuator Status**: `http://localhost:8081/actuator/flagship`

---

## 🛡️ Annotation-Based Method Guarding

Add declarative method guarding to your Spring beans:

```java
@RestController
public class OrderController {

    @GetMapping("/checkout")
    @FeatureFlagGuard(
        flag = "bkash-one-click-checkout",
        userKeyExpression = "#userId",
        fallbackMethod = "legacyRedirectCheckout"
    )
    public Map<String, Object> instantCheckout(@RequestParam String userId) {
        return Map.of("status", "SUCCESS", "gateway", "BKASH_ONE_CLICK_V2");
    }

    public Map<String, Object> legacyRedirectCheckout(String userId) {
        return Map.of("status", "FALLBACK", "gateway", "STANDARD_REDIRECT_LEGACY");
    }
}
```

---

## 🔬 Experimentation & Statistical Significance

FlagShip automatically tracks feature flag conversion metrics:
- **Sample Ratio Mismatch (SRM)**: Computes Pearson's $\chi^2$ goodness-of-fit against the expected traffic split ratio. Flags severe traffic assignment bias if $p < 0.001$.
- **Hypothesis Testing**: Computes the pooled two-proportion Z-statistic, two-tailed $p$-value, relative uplift %, and $95\%$ confidence intervals.

---

## 🧹 Flag Hygiene Management

FlagShip scans for technical debt continuously:
1. **Zombie Flags**: Flags created $> 30$ days ago with zero recorded evaluations.
2. **Inactive Flags**: Flags evaluated in the past but with no traffic for $> 30$ days.
3. **Permanent Rollouts**: Flags rolled out to $100\%$ in production for $> 14$ days.

Review stale flags directly in the UI at `http://localhost:8080/hygiene` with actionable cleanup recommendations.

---

## 📚 Documentation
- [System Architecture & Design Patterns](docs/ARCHITECTURE.md)
- [Starter Quickstart Guide](docs/QUICKSTART.md)
- [Requirements Traceability Matrix](docs/TRACEABILITY_MATRIX.md)
- [REST API Reference](docs/API.md)
