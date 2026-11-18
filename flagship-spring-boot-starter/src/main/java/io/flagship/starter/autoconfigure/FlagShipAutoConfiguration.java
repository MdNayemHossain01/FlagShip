package io.flagship.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.starter.actuator.FlagShipActuatorEndpoint;
import io.flagship.starter.aop.FeatureFlagGuardAspect;
import io.flagship.starter.aop.FeatureGateAspect;
import io.flagship.starter.client.FlagClient;
import io.flagship.starter.client.FlagShipClient;
import io.flagship.starter.sync.ResilientSnapshotManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass(FlagEvaluator.class)
@ConditionalOnProperty(prefix = "flagship", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlagShipProperties.class)
public class FlagShipAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlagEvaluator flagEvaluator() {
        return new FlagEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilientSnapshotManager resilientSnapshotManager(
            FlagShipProperties props, ObjectMapper objectMapper, MeterRegistry registry) {
        ResilientSnapshotManager manager = new ResilientSnapshotManager(props, objectMapper);
        if (registry != null) {
            Gauge.builder("flagship.sync.sse_connected", manager, m -> m.isSseConnected() ? 1.0 : 0.0)
                .description("FlagShip live SSE connection status (1 = connected, 0 = disconnected)")
                .register(registry);
            Gauge.builder("flagship.snapshot.revision", manager, m -> m.getSnapshot().revision())
                .description("Active FlagShip in-memory snapshot revision")
                .register(registry);
            Gauge.builder("flagship.snapshot.flags.count", manager, m -> m.getSnapshot().flags().size())
                .description("Number of active feature flags in snapshot")
                .register(registry);
        }
        return manager;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(FlagShipClient.class)
    public FlagShipClient flagShipClient(
            ResilientSnapshotManager snapshotManager,
            FlagEvaluator evaluator,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        return new FlagShipClient(snapshotManager, evaluator, meterRegistry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(FlagClient.class)
    public FlagClient flagClient(
            ResilientSnapshotManager snapshotManager,
            FlagEvaluator evaluator,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        return new FlagClient(snapshotManager, evaluator, meterRegistry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagGuardAspect featureFlagGuardAspect(FlagShipClient flagShipClient) {
        return new FeatureFlagGuardAspect(flagShipClient);
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    @ConditionalOnMissingBean
    public FlagShipActuatorEndpoint flagShipActuatorEndpoint(ResilientSnapshotManager snapshotManager) {
        return new FlagShipActuatorEndpoint(snapshotManager);
    }
}
