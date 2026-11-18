package io.flagship.demo;

import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.starter.sync.ResilientSnapshotManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "flagship.enabled=true",
    "flagship.server-url=http://127.0.0.1:9",
    "flagship.startup-timeout=100ms"
})
class DemoApplicationIntegrationTest {

    @Autowired
    private CheckoutDemoController controller;

    @Autowired
    private ResilientSnapshotManager snapshotManager;

    @BeforeEach
    void setUpSnapshot() {
        FlagDefinition checkoutFlag = new FlagDefinition(
            "bkash-one-click-checkout",
            FlagType.BOOLEAN,
            true,
            "off",
            "off",
            Map.of("on", true, "off", false),
            Map.of("on", Set.of("nayem1541")),
            List.of(),
            new FlagDefinition.RolloutConfig(List.of(
                new FlagDefinition.WeightedVariation("on", 0),
                new FlagDefinition.WeightedVariation("off", 100_000)
            )),
            "s1"
        );

        FlagDefinition bannerFlag = new FlagDefinition(
            "welcome-banner-text",
            FlagType.STRING,
            true,
            "off",
            "Welcome Nayem!",
            Map.of("Welcome Nayem!", "Welcome Nayem!"),
            Map.of(),
            List.of(),
            null,
            "s2"
        );

        FlagDefinition discountFlag = new FlagDefinition(
            "checkout-discount-pct",
            FlagType.NUMBER,
            true,
            "off",
            "20",
            Map.of("20", 20),
            Map.of(),
            List.of(),
            null,
            "s3"
        );

        FlagDefinition layoutFlag = new FlagDefinition(
            "ui-layout-config",
            FlagType.JSON,
            true,
            "off",
            "modern",
            Map.of("modern", "{\"theme\":\"dark\"}"),
            Map.of(),
            List.of(),
            null,
            "s4"
        );

        ResilientSnapshotManager.FlagSnapshot snapshot = new ResilientSnapshotManager.FlagSnapshot(
            100L,
            Instant.now(),
            Map.of(
                "bkash-one-click-checkout", checkoutFlag,
                "welcome-banner-text", bannerFlag,
                "checkout-discount-pct", discountFlag,
                "ui-layout-config", layoutFlag
            )
        );

        snapshotManager.updateSnapshot(snapshot);
    }

    @Test
    @DisplayName("QA5 Adoption: VIP user targeted for feature executes accelerated @FeatureFlagGuard method")
    void testTargetedUserExecutesGuardedFlow() {
        Map<String, Object> response = controller.oneClickPay("nayem1541");

        assertEquals("SUCCESSFUL_INSTANT_PAYMENT", response.get("status"));
        assertEquals("BKASH_ONE_CLICK_V2_ACCELERATED", response.get("gateway"));
        assertEquals(true, response.get("aopGuarded"));
    }

    @Test
    @DisplayName("QA5 Adoption: Untargeted user is transparently routed to legacy fallback method")
    void testUntargetedUserRoutesToFallbackMethod() {
        Map<String, Object> response = controller.oneClickPay("regular-customer");

        assertEquals("FALLBACK_TO_MULTI_STEP_CHECKOUT", response.get("status"));
        assertEquals("STANDARD_LEGACY_REDIRECT_GATEWAY", response.get("gateway"));
        assertEquals(true, response.get("aopGuarded"));
    }

    @Test
    @DisplayName("QA5 Adoption: Typed evaluation methods return typed values from local in-memory snapshot")
    void testTypedFlagEvaluations() {
        Map<String, Object> result = controller.evaluateAllFlags("nayem1541", "BD", "2.4.0");

        assertTrue(result.containsKey("evaluations"));
        @SuppressWarnings("unchecked")
        Map<String, Object> evals = (Map<String, Object>) result.get("evaluations");

        assertEquals("Welcome Nayem!", evals.get("welcome-banner-text"));
        assertEquals(20, evals.get("checkout-discount-pct"));
        assertEquals("{\"theme\":\"dark\"}", evals.get("ui-layout-config"));
        assertEquals(true, result.get("offlineSafe"));
    }
}
