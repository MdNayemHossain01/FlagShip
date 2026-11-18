package io.flagship.starter.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.flagship.core.context.EvaluationContext;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.starter.autoconfigure.FlagShipProperties;
import io.flagship.starter.client.FlagShipClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfflineResilienceTest {

    @TempDir
    Path tempDir;

    @Test
    void testOfflineSnapshotRestorationAndEvaluation() throws Exception {
        Path snapshotPath = tempDir.resolve("snapshot.json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // 1. Prepare cached snapshot with a flag on disk
        FlagDefinition flag = new FlagDefinition(
            "offline-flag", FlagType.STRING, true, "off", "cached-value",
            Map.of("cached-value", "SUCCESS_OFFLINE_PAYLOAD"),
            Map.of(), null, null, "s"
        );
        ResilientSnapshotManager.FlagSnapshot diskData = new ResilientSnapshotManager.FlagSnapshot(
            42L, Instant.now(), Map.of("offline-flag", flag)
        );
        mapper.writeValue(snapshotPath.toFile(), diskData);
        assertTrue(Files.exists(snapshotPath));

        // 2. Configure properties pointing to dummy server with local snapshot
        FlagShipProperties props = new FlagShipProperties();
        props.setServerUrl("http://127.0.0.1:9"); // Discard port (fails fast)
        props.setStartupTimeout(Duration.ofMillis(200));
        props.setSnapshotFile(snapshotPath);

        ResilientSnapshotManager manager = new ResilientSnapshotManager(props, mapper);
        manager.loadFromDisk();

        // Verify snapshot was loaded from disk
        assertEquals(42L, manager.getSnapshot().revision());
        assertEquals(1, manager.getSnapshot().flags().size());
        assertFalse(manager.isSseConnected());

        // 3. Verify FlagShipClient evaluates successfully using the cached snapshot without throwing exceptions
        FlagShipClient client = new FlagShipClient(manager, new FlagEvaluator(), new SimpleMeterRegistry(), mapper);
        EvaluationContext ctx = EvaluationContext.builder("user-offline").build();

        String val = client.getString("offline-flag", "default", ctx);
        assertEquals("SUCCESS_OFFLINE_PAYLOAD", val);

        // 4. Test missing flag fallback (never throws)
        String fallbackVal = client.getString("non-existent-flag", "default-fallback", ctx);
        assertEquals("default-fallback", fallbackVal);

        manager.destroy();
    }
}
