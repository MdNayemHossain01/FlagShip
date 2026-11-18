package io.flagship.demo;

import io.flagship.core.context.EvaluationContext;
import io.flagship.starter.aop.FeatureFlagGuard;
import io.flagship.starter.aop.FeatureGate;
import io.flagship.starter.client.FlagShipClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CheckoutDemoController {

    private final FlagShipClient flagShipClient;

    public CheckoutDemoController(FlagShipClient flagShipClient) {
        this.flagShipClient = flagShipClient;
    }

    /**
     * Demonstrates typed evaluation methods:
     * - flagShipClient.isEnabled(...)
     * - flagShipClient.getString(...)
     * - flagShipClient.getNumber(...)
     * - flagShipClient.getJson(...)
     */
    @GetMapping("/api/demo/evaluate")
    public Map<String, Object> evaluateAllFlags(
            @RequestParam(defaultValue = "nayem1541") String userId,
            @RequestParam(defaultValue = "BD") String country,
            @RequestParam(defaultValue = "2.4.0") String appVersion) {

        EvaluationContext context = EvaluationContext.builder(userId)
            .attribute("country", country)
            .attribute("appVersion", appVersion)
            .build();

        // 1. Boolean flag evaluation
        boolean isCheckoutEnabled = flagShipClient.isEnabled("bkash-one-click-checkout", context);

        // 2. String flag evaluation
        String welcomeBanner = flagShipClient.getString("welcome-banner-text", "Default Welcome", context);

        // 3. Number flag evaluation
        Number discountPct = flagShipClient.getNumber("checkout-discount-pct", 0, context);

        // 4. JSON flag evaluation
        String layoutConfig = flagShipClient.getJson("ui-layout-config", context);

        // 5. Detailed evaluation with match reason
        var detail = flagShipClient.evaluateDetail("bkash-one-click-checkout", context, false);

        return Map.of(
            "userContext", Map.of("userId", userId, "country", country, "appVersion", appVersion),
            "evaluations", Map.of(
                "bkash-one-click-checkout", Map.of(
                    "enabled", isCheckoutEnabled,
                    "matchedVariation", detail.variationName(),
                    "reason", detail.reason().name()
                ),
                "welcome-banner-text", welcomeBanner,
                "checkout-discount-pct", discountPct,
                "ui-layout-config", layoutConfig
            ),
            "offlineSafe", true,
            "latencyMicroseconds", "< 10 µs (in-memory)"
        );
    }

    /**
     * Demonstrates @FeatureFlagGuard annotation with fallbackMethod invocation when disabled.
     */
    @GetMapping("/api/demo/pay")
    @FeatureFlagGuard(flag = "bkash-one-click-checkout", userKeyExpression = "#userId", fallbackMethod = "legacyPayment")
    public Map<String, Object> oneClickPay(@RequestParam(defaultValue = "nayem1541") String userId) {
        // Track conversion goal when user completes checkout
        flagShipClient.trackGoal("bkash-one-click-checkout", "checkout_completed", 30.0,
            EvaluationContext.builder(userId).build());

        return Map.of(
            "userId", userId,
            "gateway", "BKASH_ONE_CLICK_V2_ACCELERATED",
            "aopGuarded", true,
            "status", "SUCCESSFUL_INSTANT_PAYMENT"
        );
    }

    /**
     * Fallback method called when bkash-one-click-checkout evaluates to false.
     */
    public Map<String, Object> legacyPayment(String userId) {
        return Map.of(
            "userId", userId,
            "gateway", "STANDARD_LEGACY_REDIRECT_GATEWAY",
            "aopGuarded", true,
            "status", "FALLBACK_TO_MULTI_STEP_CHECKOUT"
        );
    }

    /**
     * Backward-compatible @FeatureGate annotation test.
     */
    @GetMapping("/api/demo/legacy-gate")
    @FeatureGate(flag = "bkash-one-click-checkout", userKeyExpression = "#userId", fallbackMethod = "legacyPayment")
    public Map<String, Object> legacyGatePay(@RequestParam(defaultValue = "nayem1541") String userId) {
        return Map.of("userId", userId, "gateway", "FEATURE_GATE_METHOD");
    }

    /**
     * Status of local snapshot and offline resilience.
     */
    @GetMapping("/api/demo/status")
    public Map<String, Object> getSnapshotStatus() {
        var snap = flagShipClient.getSnapshotManager().getSnapshot();
        return Map.of(
            "revision", snap.revision(),
            "syncedAt", snap.syncedAt().toString(),
            "flagsCount", snap.flags().size(),
            "sseConnected", flagShipClient.getSnapshotManager().isSseConnected(),
            "offlineResilient", true
        );
    }
}
