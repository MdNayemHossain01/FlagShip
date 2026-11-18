package io.flagship.starter.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.starter.autoconfigure.FlagShipProperties;
import io.flagship.starter.client.FlagShipClient;
import io.flagship.starter.sync.ResilientSnapshotManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagGuardAspectTest {

    private StubSnapshotManager stubManager;
    private SampleService proxyService;

    public static class SampleService {
        @FeatureFlagGuard(flag = "feature-x", userKeyExpression = "#userId", fallbackMethod = "fallbackHandler")
        public String executePayment(String userId, double amount) {
            return "SUCCESS_NEW_FLOW:" + userId + ":" + amount;
        }

        public String fallbackHandler(String userId, double amount) {
            return "FALLBACK_LEGACY_FLOW:" + userId + ":" + amount;
        }

        @FeatureFlagGuard(flag = "feature-x", userKeyExpression = "#userId", throwOnDisabled = true)
        public String guardedStrict(String userId) {
            return "STRICT_FLOW:" + userId;
        }
    }

    static class StubSnapshotManager extends ResilientSnapshotManager {
        private FlagSnapshot stubSnapshot;

        public StubSnapshotManager(FlagSnapshot stubSnapshot) {
            super(new FlagShipProperties(), new ObjectMapper());
            this.stubSnapshot = stubSnapshot;
        }

        public void setSnapshot(FlagSnapshot stubSnapshot) {
            this.stubSnapshot = stubSnapshot;
        }

        @Override
        public FlagSnapshot getSnapshot() {
            return stubSnapshot != null ? stubSnapshot : FlagSnapshot.empty();
        }

        @Override
        public void recordExposure(String flagKey, String variation, String userKey) {}

        @Override
        public void recordGoal(String flagKey, String goalName, String userKey, double value) {}

        @Override
        public void afterPropertiesSet() {}

        @Override
        public void destroy() {}
    }

    @BeforeEach
    void setUp() {
        stubManager = new StubSnapshotManager(null);
        FlagEvaluator evaluator = new FlagEvaluator();
        FlagShipClient flagShipClient = new FlagShipClient(stubManager, evaluator, new SimpleMeterRegistry(), null);

        FeatureFlagGuardAspect aspect = new FeatureFlagGuardAspect(flagShipClient);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        proxyService = factory.getProxy();
    }

    @Test
    void whenFlagIsEnabled_executeOriginalMethod() {
        FlagDefinition flag = new FlagDefinition(
            "feature-x", FlagType.BOOLEAN, true, "off", "on",
            Map.of("on", true, "off", false),
            Map.of("on", Set.of("user-100")),
            null, null, "s"
        );
        stubManager.setSnapshot(new ResilientSnapshotManager.FlagSnapshot(1L, Instant.now(), Map.of("feature-x", flag)));

        String result = proxyService.executePayment("user-100", 50.0);
        assertEquals("SUCCESS_NEW_FLOW:user-100:50.0", result);
    }

    @Test
    void whenFlagIsDisabled_executeFallbackMethod() {
        FlagDefinition flag = new FlagDefinition(
            "feature-x", FlagType.BOOLEAN, false, "off", "off",
            Map.of("on", true, "off", false),
            Map.of(),
            null, null, "s"
        );
        stubManager.setSnapshot(new ResilientSnapshotManager.FlagSnapshot(1L, Instant.now(), Map.of("feature-x", flag)));

        String result = proxyService.executePayment("user-100", 50.0);
        assertEquals("FALLBACK_LEGACY_FLOW:user-100:50.0", result);
    }

    @Test
    void whenFlagIsDisabledAndThrowOnDisabled_throwsException() {
        FlagDefinition flag = new FlagDefinition(
            "feature-x", FlagType.BOOLEAN, false, "off", "off",
            Map.of("on", true, "off", false),
            null, null, null, "s"
        );
        stubManager.setSnapshot(new ResilientSnapshotManager.FlagSnapshot(1L, Instant.now(), Map.of("feature-x", flag)));

        assertThrows(FeatureDisabledException.class, () -> proxyService.guardedStrict("user-blocked"));
    }
}
