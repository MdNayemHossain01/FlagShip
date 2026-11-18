package io.flagship.starter.actuator;

import io.flagship.starter.sync.ResilientSnapshotManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.Map;
import java.util.stream.Collectors;

@Endpoint(id = "flagship")
public class FlagShipActuatorEndpoint {
    private final ResilientSnapshotManager snapshotManager;

    public FlagShipActuatorEndpoint(ResilientSnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    @ReadOperation
    public Map<String, Object> status() {
        var snap = snapshotManager.getSnapshot();
        var summary = snap.flags().values().stream().collect(Collectors.toMap(
            f -> f.key(),
            f -> Map.of("enabled", f.enabled(), "type", f.type(), "defaultVariation", f.defaultVariation())
        ));
        return Map.of(
            "revision", snap.revision(),
            "lastSyncTime", snap.syncedAt().toString(),
            "sseConnected", snapshotManager.isSseConnected(),
            "loadedFlagCount", snap.flags().size(),
            "flags", summary
        );
    }
}
