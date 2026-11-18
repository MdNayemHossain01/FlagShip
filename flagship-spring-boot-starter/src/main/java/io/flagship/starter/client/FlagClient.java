package io.flagship.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.starter.sync.ResilientSnapshotManager;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Backward compatibility alias for FlagShipClient.
 */
public class FlagClient extends FlagShipClient {
    public FlagClient(ResilientSnapshotManager snapshotManager, FlagEvaluator evaluator, MeterRegistry meterRegistry) {
        super(snapshotManager, evaluator, meterRegistry, new ObjectMapper());
    }

    public FlagClient(ResilientSnapshotManager snapshotManager, FlagEvaluator evaluator, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        super(snapshotManager, evaluator, meterRegistry, objectMapper);
    }
}
