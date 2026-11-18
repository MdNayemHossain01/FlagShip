package io.flagship.core;

import io.flagship.core.context.EvaluationContext;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyAndPerformanceTest {

    @Test
    void qa4_oneMillionUsersStickinessAndRampFrom10To20Percent() {
        FlagEvaluator clientEvaluator = new FlagEvaluator();
        FlagEvaluator serverPreviewEvaluator = new FlagEvaluator();

        FlagDefinition flag10Percent = new FlagDefinition(
            "new-search", FlagType.BOOLEAN, true, "off", "off",
            Map.of("on", true, "off", false), Map.of(), List.of(),
            new FlagDefinition.RolloutConfig(List.of(
                new FlagDefinition.WeightedVariation("on", 10_000),
                new FlagDefinition.WeightedVariation("off", 90_000)
            )), "v1"
        );

        FlagDefinition flag20Percent = new FlagDefinition(
            "new-search", FlagType.BOOLEAN, true, "off", "off",
            Map.of("on", true, "off", false), Map.of(), List.of(),
            new FlagDefinition.RolloutConfig(List.of(
                new FlagDefinition.WeightedVariation("on", 20_000),
                new FlagDefinition.WeightedVariation("off", 80_000)
            )), "v1"
        );

        Set<String> usersInAt10Percent = new HashSet<>(110_000);
        int inAt20Count = 0;

        for (int i = 0; i < 1_000_000; i++) {
            String userKey = "user-" + i;
            EvaluationContext ctx = EvaluationContext.builder(userKey).build();

            boolean clientVal10 = clientEvaluator.evaluate(flag10Percent, ctx, false).value();
            boolean serverVal10 = serverPreviewEvaluator.evaluate(flag10Percent, ctx, false).value();
            assertEquals(serverVal10, clientVal10);

            if (clientVal10) {
                usersInAt10Percent.add(userKey);
            }

            boolean clientVal20 = clientEvaluator.evaluate(flag20Percent, ctx, false).value();
            if (clientVal20) {
                inAt20Count++;
            }
            if (clientVal10) {
                assertTrue(clientVal20, "Every user in 10% rollout must stay in at 20%");
            }
        }

        assertTrue(usersInAt10Percent.size() > 98_000 && usersInAt10Percent.size() < 102_000);
        assertTrue(inAt20Count > 198_000 && inAt20Count < 202_000);
    }

    @Test
    void qa1_p99EvaluationLatencyUnder50Microseconds() {
        FlagEvaluator evaluator = new FlagEvaluator();
        FlagDefinition flag = new FlagDefinition(
            "perf-flag", FlagType.BOOLEAN, true, "off", "off",
            Map.of("on", true, "off", false), Map.of(), List.of(),
            new FlagDefinition.RolloutConfig(List.of(new FlagDefinition.WeightedVariation("on", 50_000))), "s1"
        );
        EvaluationContext ctx = EvaluationContext.builder("user-999").attribute("country", "BD").build();

        for (int i = 0; i < 10_000; i++) evaluator.evaluate(flag, ctx, false);

        long[] latenciesNs = new long[100_000];
        for (int i = 0; i < latenciesNs.length; i++) {
            long start = System.nanoTime();
            evaluator.evaluate(flag, ctx, false);
            latenciesNs[i] = System.nanoTime() - start;
        }
        Arrays.sort(latenciesNs);
        long p99Ns = latenciesNs[(int) (latenciesNs.length * 0.99)];
        assertTrue(p99Ns < 50_000L, "p99 latency was " + p99Ns + " ns");
    }

    @Test
    void qa1_heapMemoryFootprintUnder20MBFor1000Flags() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Map<String, FlagDefinition> thousandFlags = new HashMap<>(1000);
        for (int i = 0; i < 1000; i++) {
            FlagDefinition flag = new FlagDefinition(
                "flag-" + i,
                FlagType.BOOLEAN,
                true,
                "off",
                "off",
                Map.of("on", true, "off", false),
                Map.of("on", Set.of("target-user-" + i)),
                List.of(),
                new FlagDefinition.RolloutConfig(List.of(
                    new FlagDefinition.WeightedVariation("on", 50_000),
                    new FlagDefinition.WeightedVariation("off", 50_000)
                )),
                "salt-" + i
            );
            thousandFlags.put(flag.key(), flag);
        }

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long deltaBytes = Math.max(0, memAfter - memBefore);
        double deltaMB = deltaBytes / (1024.0 * 1024.0);

        System.out.printf("QA1 Memory Footprint: 1,000 flags consume approx %.2f MB heap%n", deltaMB);
        assertEquals(1000, thousandFlags.size());
        assertTrue(deltaMB < 20.0, "1,000 flags must consume < 20 MB heap, but was " + deltaMB + " MB");
    }
}
