# FlagShip Quickstart Guide

This 15-minute quickstart demonstrates how to integrate the **FlagShip Spring Boot Starter** into an existing or new Spring Boot application.

---

## 1. Add Maven Dependency

Add `flagship-spring-boot-starter` to your application's `pom.xml`:

```xml
<dependency>
    <groupId>io.flagship</groupId>
    <artifactId>flagship-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 2. Configure `application.yml`

Configure FlagShip connection properties in your `src/main/resources/application.yml`:

```yaml
flagship:
  enabled: true
  server-url: http://localhost:8080
  project: default
  environment: production
  api-key: fs_live_default
  snapshot-path: ./data/flagship-snapshot.json
  startup-timeout-ms: 2000
  sse-enabled: true
  polling-interval-seconds: 30
```

---

## 3. Inject and Use `FlagShipClient`

The starter auto-configures `FlagShipClient` as a Spring Bean. Inject it into your controllers or services:

```java
package com.example.service;

import io.flagship.core.context.EvaluationContext;
import io.flagship.starter.client.FlagShipClient;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private final FlagShipClient flagShipClient;

    public CheckoutService(FlagShipClient flagShipClient) {
        this.flagShipClient = flagShipClient;
    }

    public String processOrder(String userId, double amount) {
        // Build evaluation context with user attributes
        EvaluationContext context = EvaluationContext.builder(userId)
            .attribute("country", "BD")
            .attribute("appVersion", "2.4.0")
            .build();

        // 1. Boolean check (< 10 microseconds)
        if (flagShipClient.isEnabled("bkash-one-click-checkout", context)) {
            // Track conversion event for statistical analysis
            flagShipClient.trackGoal("bkash-one-click-checkout", "checkout_completed", amount, context);
            return "Processed via Accelerated bKash One-Click Gateway";
        }

        // Fallback to legacy payment
        return "Processed via Standard Payment Gateway";
    }

    public String getDynamicGreeting(String userId) {
        EvaluationContext context = EvaluationContext.builder(userId).build();
        // 2. String variation evaluation
        return flagShipClient.getString("welcome-banner-text", "Welcome to our store!", context);
    }

    public double getDiscountPercentage(String userId) {
        EvaluationContext context = EvaluationContext.builder(userId).build();
        // 3. Number evaluation
        return flagShipClient.getNumber("checkout-discount-pct", 0, context).doubleValue();
    }
}
```

---

## 4. Annotation-Based Method Guarding (`@FeatureFlagGuard`)

Decorate methods with `@FeatureFlagGuard` to automatically redirect disabled code paths to a fallback method using Spring AOP:

```java
package com.example.controller;

import io.flagship.starter.aop.FeatureFlagGuard;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping("/checkout")
    @FeatureFlagGuard(
        flag = "bkash-one-click-checkout",
        userKeyExpression = "#userId",
        fallbackMethod = "legacyRedirectCheckout"
    )
    public Map<String, Object> instantCheckout(@RequestParam String userId) {
        return Map.of(
            "status", "SUCCESS",
            "experience", "ONE_CLICK_INSTANT_CHECKOUT"
        );
    }

    // Fallback method executed when flag evaluates to false
    public Map<String, Object> legacyRedirectCheckout(String userId) {
        return Map.of(
            "status", "REDIRECT",
            "experience", "LEGACY_MULTI_STEP_REDIRECT"
        );
    }
}
```

---

## 5. Inspect Actuator Endpoint

FlagShip exposes runtime status and local cache metrics on Spring Boot Actuator:

```bash
curl http://localhost:8081/actuator/flagship
```

Response:
```json
{
  "revision": 101,
  "loadedFlagCount": 5,
  "sseConnected": true,
  "lastSyncTime": "2026-11-18T07:46:14.325565700Z",
  "flags": {
    "bkash-one-click-checkout": {
      "type": "BOOLEAN",
      "enabled": true,
      "defaultVariation": "off"
    }
  }
}
```

---

## 6. Run the Built-In Demo Application

To verify everything end-to-end:

```bash
# 1. Start FlagShip Server (Port 8080)
java -jar flagship-server/target/flagship-server-1.0.0.jar

# 2. In another terminal, start the Demo App (Port 8081)
java -jar flagship-demo-app/target/flagship-demo-app-1.0.0.jar

# 3. Test flag evaluation endpoint
curl "http://localhost:8081/api/demo/evaluate?userId=nayem1541"

# 4. Test guarded payment endpoint
curl "http://localhost:8081/api/demo/pay?userId=nayem1541"
```
