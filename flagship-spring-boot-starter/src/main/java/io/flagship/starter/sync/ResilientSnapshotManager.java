package io.flagship.starter.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.flagship.core.model.FlagDefinition;
import io.flagship.starter.autoconfigure.FlagShipProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ResilientSnapshotManager implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ResilientSnapshotManager.class);

    public record FlagSnapshot(long revision, Instant syncedAt, Map<String, FlagDefinition> flags) {
        public static FlagSnapshot empty() {
            return new FlagSnapshot(0L, Instant.EPOCH, Collections.emptyMap());
        }
    }

    public record EventRecord(String type, String flagKey, String variation, String userKey, String goalName, double value, Instant timestamp) {}

    private final FlagShipProperties props;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;
    private final AtomicReference<FlagSnapshot> activeSnapshot = new AtomicReference<>(FlagSnapshot.empty());
    private final AtomicBoolean sseConnected = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulSyncTimestamp = new AtomicLong(0L);
    private final BlockingQueue<EventRecord> eventQueue = new LinkedBlockingQueue<>(10_000);
    private volatile boolean running = true;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3,
        Thread.ofVirtual().name("flagship-sync-", 0).factory());

    public ResilientSnapshotManager(FlagShipProperties props, ObjectMapper objectMapper) {
        this.props = props;
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        } else {
            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());
            this.objectMapper = om;
        }
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null && running) {
            try {
                Duration connectTimeout = props.getStartupTimeout();
                if (connectTimeout == null || connectTimeout.compareTo(Duration.ofSeconds(2)) > 0) {
                    connectTimeout = Duration.ofSeconds(2);
                }
                this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            } catch (Exception ex) {
                log.debug("HTTP client unavailable (operating in offline sandbox): {}", ex.getMessage());
            }
        }
        return httpClient;
    }

    @Override
    public void afterPropertiesSet() {
        // Step 1: Load cached snapshot from local disk first (0 latency offline resilience)
        loadFromDisk();

        // Step 2: Attempt initial sync with bounded timeout (max 2 seconds)
        try {
            long boundMs = Math.min(props.getStartupTimeout().toMillis(), 2000L);
            CompletableFuture.runAsync(this::pollOnce, scheduler)
                .get(boundMs, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("FlagShip server unreachable during startup (bounded {}ms); operating offline with snapshot rev={}",
                props.getStartupTimeout().toMillis(), activeSnapshot.get().revision());
        }

        // Step 3: Launch SSE maintenance loop in background
        scheduler.submit(this::maintainSseLoop);

        // Step 4: Schedule fallback polling in case SSE drops
        scheduler.scheduleWithFixedDelay(this::fallbackPollIfDisconnected,
            props.getPollInterval().toSeconds(), props.getPollInterval().toSeconds(), TimeUnit.SECONDS);

        // Step 5: Schedule periodic batch flush of experiment events
        scheduler.scheduleWithFixedDelay(this::flushEventQueue, 3, 5, TimeUnit.SECONDS);
    }

    public FlagSnapshot getSnapshot() {
        return activeSnapshot.get();
    }

    public void updateSnapshot(FlagSnapshot snapshot) {
        if (snapshot != null) {
            this.activeSnapshot.set(snapshot);
            this.lastSuccessfulSyncTimestamp.set(System.currentTimeMillis());
        }
    }

    public boolean isSseConnected() {
        return sseConnected.get();
    }

    public long getLastSyncTimeMillis() {
        return lastSuccessfulSyncTimestamp.get();
    }

    public void recordExposure(String flagKey, String variation, String userKey) {
        eventQueue.offer(new EventRecord("EXPOSURE", flagKey, variation, userKey, null, 1.0, Instant.now()));
    }

    public void recordGoal(String flagKey, String goalName, String userKey, double value) {
        eventQueue.offer(new EventRecord("GOAL", flagKey, null, userKey, goalName, value, Instant.now()));
    }

    public void loadFromDisk() {
        try {
            Path file = props.getSnapshotFile();
            if (file != null && Files.exists(file)) {
                FlagSnapshot diskSnapshot = objectMapper.readValue(file.toFile(), FlagSnapshot.class);
                if (diskSnapshot != null && diskSnapshot.flags() != null) {
                    activeSnapshot.set(diskSnapshot);
                    lastSuccessfulSyncTimestamp.set(diskSnapshot.syncedAt().toEpochMilli());
                    log.info("Loaded FlagShip offline snapshot rev={} from disk ({} flags)",
                        diskSnapshot.revision(), diskSnapshot.flags().size());
                }
            }
        } catch (Exception e) {
            log.debug("No local FlagShip snapshot found or unable to parse: {}", e.getMessage());
        }
    }

    private void applyAndPersist(String jsonPayload) {
        try {
            FlagSnapshot incoming = objectMapper.readValue(jsonPayload, FlagSnapshot.class);
            FlagSnapshot current = activeSnapshot.get();
            if (incoming != null && incoming.revision() >= current.revision()) {
                FlagSnapshot updated = new FlagSnapshot(incoming.revision(), Instant.now(), incoming.flags());
                activeSnapshot.set(updated);
                lastSuccessfulSyncTimestamp.set(System.currentTimeMillis());
                persistToDiskAsync(updated);
                log.info("FlagShip snapshot updated to rev={} ({} flags)", updated.revision(), updated.flags().size());
            }
        } catch (Exception e) {
            log.debug("Ignored invalid snapshot payload: {}", e.getMessage());
        }
    }

    private void persistToDiskAsync(FlagSnapshot snapshot) {
        if (!running) return;
        scheduler.submit(() -> {
            try {
                Path target = props.getSnapshotFile();
                if (target != null) {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");
                    objectMapper.writeValue(tempFile.toFile(), snapshot);
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                log.debug("Failed to write snapshot to disk: {}", ex.getMessage());
            }
        });
    }

    public void pollOnce() {
        if (!running) return;
        HttpClient client = getHttpClient();
        if (client == null) return;
        try {
            URI uri = URI.create(String.format("%s/api/v1/sdk/snapshot?project=%s&env=%s",
                props.getServerUrl(), props.getProjectKey(), props.getEnvironment()));
            HttpRequest req = HttpRequest.newBuilder(uri)
                .header("X-FlagShip-Key", props.getApiKey())
                .timeout(props.getStartupTimeout())
                .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && !res.body().isBlank()) {
                applyAndPersist(res.body());
            }
        } catch (Exception ignored) {
            // Never propagate connection errors
        }
    }

    private void fallbackPollIfDisconnected() {
        if (!sseConnected.get() && running) {
            pollOnce();
        }
    }

    private void maintainSseLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            HttpClient client = getHttpClient();
            if (client != null) {
                try {
                    URI uri = URI.create(String.format("%s/api/v1/sdk/stream?project=%s&env=%s",
                        props.getServerUrl(), props.getProjectKey(), props.getEnvironment()));
                    HttpRequest req = HttpRequest.newBuilder(uri)
                        .header("Accept", "text/event-stream")
                        .header("X-FlagShip-Key", props.getApiKey())
                        .timeout(Duration.ofSeconds(3))
                        .GET().build();

                    HttpResponse<InputStream> response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        sseConnected.set(true);
                        log.info("Connected to FlagShip live SSE stream at {}", uri);
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while (running && (line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String payload = line.substring(5).trim();
                                    if (!payload.isEmpty() && !payload.equals("heartbeat") && !payload.startsWith(":")) {
                                        applyAndPersist(payload);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    sseConnected.set(false);
                }
            }
            if (!running) break;
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void flushEventQueue() {
        if (!running || eventQueue.isEmpty()) return;
        HttpClient client = getHttpClient();
        if (client == null) return;

        List<EventRecord> batch = new ArrayList<>();
        eventQueue.drainTo(batch, 500);
        if (batch.isEmpty()) return;

        try {
            URI uri = URI.create(String.format("%s/api/v1/sdk/events?project=%s&env=%s",
                props.getServerUrl(), props.getProjectKey(), props.getEnvironment()));
            String json = objectMapper.writeValueAsString(batch);
            HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-FlagShip-Key", props.getApiKey())
                .timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            // Drop silently when disconnected
        }
    }

    @Override
    public void destroy() {
        running = false;
        flushEventQueue();
        scheduler.shutdownNow();
    }
}
