package io.flagship.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.context.EvaluationContext;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.starter.sync.ResilientSnapshotManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * FlagShipClient is the primary client for evaluating feature flags in Spring Boot applications.
 * All evaluations run strictly in-memory against local snapshots with 0-network latency (typically < 10 microseconds).
 * All failures gracefully fallback without throwing exceptions.
 */
public class FlagShipClient {
    private static final Logger log = LoggerFactory.getLogger(FlagShipClient.class);

    private final ResilientSnapshotManager snapshotManager;
    private final FlagEvaluator evaluator;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public FlagShipClient(
            ResilientSnapshotManager snapshotManager,
            FlagEvaluator evaluator,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.snapshotManager = snapshotManager;
        this.evaluator = evaluator;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Evaluate boolean feature flag with default false fallback.
     */
    public boolean isEnabled(String flagKey, EvaluationContext context) {
        return isEnabled(flagKey, context, false);
    }

    /**
     * Evaluate boolean feature flag with explicit default fallback.
     */
    public boolean isEnabled(String flagKey, EvaluationContext context, boolean defaultValue) {
        return evaluate(flagKey, context, defaultValue);
    }

    /**
     * Evaluate string feature flag.
     */
    public String getString(String flagKey, String defaultValue, EvaluationContext context) {
        Object val = evaluate(flagKey, context, defaultValue);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    /**
     * Evaluate number feature flag (Integer, Double, Long).
     */
    public Number getNumber(String flagKey, Number defaultValue, EvaluationContext context) {
        Object val = evaluate(flagKey, context, defaultValue);
        if (val instanceof Number n) {
            return n;
        }
        if (val != null) {
            try {
                if (val.toString().contains(".")) {
                    return Double.parseDouble(val.toString());
                }
                return Long.parseLong(val.toString());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    /**
     * Evaluate JSON feature flag returning raw JSON string.
     */
    public String getJson(String flagKey, EvaluationContext context) {
        return getJson(flagKey, "{}", context);
    }

    /**
     * Evaluate JSON feature flag with default JSON string fallback.
     */
    public String getJson(String flagKey, String defaultJson, EvaluationContext context) {
        Object val = evaluate(flagKey, context, defaultJson);
        if (val instanceof String s) {
            return s;
        }
        if (val instanceof Map || val != null) {
            try {
                return objectMapper.writeValueAsString(val);
            } catch (Exception e) {
                return defaultJson;
            }
        }
        return defaultJson;
    }

    /**
     * Evaluate any typed flag value.
     */
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String flagKey, EvaluationContext context, T defaultValue) {
        try {
            FlagDefinition flag = snapshotManager.getSnapshot().flags().get(flagKey);
            FlagEvaluator.EvaluationResult<T> result = evaluator.evaluate(flag, context, defaultValue);

            if (meterRegistry != null) {
                meterRegistry.counter("flagship.evaluations.total",
                    "flag", flagKey != null ? flagKey : "unknown",
                    "variation", result.variationName() != null ? result.variationName() : "unknown",
                    "reason", result.reason() != null ? result.reason().name() : "UNKNOWN"
                ).increment();
            }

            // Track exposure asynchronously if flag is present and matched
            if (flag != null && result.reason() != FlagEvaluator.MatchReason.FLAG_NOT_FOUND) {
                snapshotManager.recordExposure(flagKey, result.variationName(), context.getUserKey());
            }

            return result.value();
        } catch (Exception ex) {
            log.debug("Evaluation fallback for flag {}: {}", flagKey, ex.getMessage());
            return defaultValue;
        }
    }

    /**
     * Evaluate flag with complete evaluation metadata (matched variation, rule ID, reason).
     */
    public <T> FlagEvaluator.EvaluationResult<T> evaluateDetail(String flagKey, EvaluationContext context, T defaultValue) {
        try {
            FlagDefinition flag = snapshotManager.getSnapshot().flags().get(flagKey);
            return evaluator.evaluate(flag, context, defaultValue);
        } catch (Exception ex) {
            return new FlagEvaluator.EvaluationResult<>(flagKey, "error-fallback", defaultValue,
                    FlagEvaluator.MatchReason.FLAG_NOT_FOUND, null);
        }
    }

    /**
     * Track user conversion / goal event for experiments.
     */
    public void trackGoal(String flagKey, String goalName, double value, EvaluationContext context) {
        snapshotManager.recordGoal(flagKey, goalName, context.getUserKey(), value);
    }

    public ResilientSnapshotManager getSnapshotManager() {
        return snapshotManager;
    }
}
