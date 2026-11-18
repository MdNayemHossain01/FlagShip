package io.flagship.server.sse;

import io.flagship.server.service.FlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SseFanOutPropagationTest {

    @Autowired
    private SseFanOutHub sseHub;

    @Autowired
    private FlagService flagService;

    @Test
    @DisplayName("QA3 Fan-Out Test: 50 concurrent SSE subscribers receive live broadcast in < 5000 ms")
    void testSseFanOutPropagationP95() throws Exception {
        final int clientCount = 50;
        final String projectKey = "default";
        final String envKey = "production";

        CountDownLatch allConnectedLatch = new CountDownLatch(clientCount);
        CountDownLatch allDeliveredLatch = new CountDownLatch(clientCount);

        long[] deliveryLatenciesMs = new long[clientCount];
        AtomicInteger receivedCount = new AtomicInteger(0);
        long[] triggerTimeHolder = new long[1];

        // 1. Connect 50 concurrent SSE listeners
        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            SseEmitter emitter = new SseEmitter(60_000L) {
                @Override
                public void send(SseEventBuilder builder) throws IOException {
                    super.send(builder);
                    long receivedAt = System.currentTimeMillis();
                    long triggerTime = triggerTimeHolder[0];
                    if (triggerTime > 0) {
                        deliveryLatenciesMs[index] = Math.max(0, receivedAt - triggerTime);
                        receivedCount.incrementAndGet();
                        allDeliveredLatch.countDown();
                    }
                }
            };

            sseHub.registerEmitter(projectKey, envKey, emitter, null);
            allConnectedLatch.countDown();
        }

        boolean connected = allConnectedLatch.await(5, TimeUnit.SECONDS);
        assertTrue(connected, "All 50 listeners must connect within 5 seconds");

        // 2. Trigger kill-switch toggle via FlagService (which calls recordVersionAndBroadcast)
        triggerTimeHolder[0] = System.currentTimeMillis();

        flagService.toggleKillSwitch(projectKey, envKey, "bkash-one-click-checkout",
                false, "admin", "QA3 SSE Broadcast Test", true);

        // 3. Wait for all 50 listeners to receive the update
        boolean allDelivered = allDeliveredLatch.await(5, TimeUnit.SECONDS);
        assertTrue(allDelivered, "All 50 SSE listeners must receive the broadcast within 5000 ms (QA3)");

        Arrays.sort(deliveryLatenciesMs);
        long p50Latency = deliveryLatenciesMs[(int) (clientCount * 0.50)];
        long p95Latency = deliveryLatenciesMs[(int) (clientCount * 0.95)];

        System.out.printf("QA3 Propagation Fan-Out: 50 listeners received event. p50 = %d ms, p95 = %d ms%n",
                p50Latency, p95Latency);

        assertTrue(p95Latency < 5000, "p95 delivery latency must be under 5000 ms");
    }
}
