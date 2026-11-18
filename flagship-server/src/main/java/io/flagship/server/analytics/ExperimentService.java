package io.flagship.server.analytics;

import io.flagship.server.entity.FlagEnvironmentConfig;
import io.flagship.server.repository.ExposureEventRepository;
import io.flagship.server.repository.FlagEnvironmentConfigRepository;
import io.flagship.server.repository.GoalEventRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExperimentService {

    private final ExposureEventRepository exposureRepo;
    private final GoalEventRepository goalRepo;
    private final FlagEnvironmentConfigRepository configRepo;

    public record VariationMetric(
        String variation,
        long rawExposures,
        long uniqueExposedUsers,
        long rawConversions,
        long uniqueConvertedUsers,
        double conversionRate,
        double totalGoalValue
    ) {}

    public record ExperimentReport(
        String projectKey,
        String envKey,
        String flagKey,
        List<VariationMetric> metrics,
        StatisticalCalculator.SrmResult srm,
        StatisticalCalculator.ZTestResult zTest,
        String baselineVariation,
        String treatmentVariation
    ) {}

    public ExperimentService(ExposureEventRepository exposureRepo,
                             GoalEventRepository goalRepo,
                             FlagEnvironmentConfigRepository configRepo) {
        this.exposureRepo = exposureRepo;
        this.goalRepo = goalRepo;
        this.configRepo = configRepo;
    }

    public ExperimentReport generateReport(String projectKey, String envKey, String flagKey) {
        List<Object[]> exposureData = exposureRepo.getExposureCountsByVariation(projectKey, envKey, flagKey);
        List<Object[]> goalData = goalRepo.getGoalCountsByVariation(projectKey, envKey, flagKey);

        Map<String, Long> expRaw = new HashMap<>();
        Map<String, Long> expUsers = new HashMap<>();
        for (Object[] row : exposureData) {
            String var = (String) row[0];
            expRaw.put(var, ((Number) row[1]).longValue());
            expUsers.put(var, ((Number) row[2]).longValue());
        }

        Map<String, Long> goalRaw = new HashMap<>();
        Map<String, Long> goalUsers = new HashMap<>();
        Map<String, Double> goalValues = new HashMap<>();
        for (Object[] row : goalData) {
            String var = (String) row[0];
            if (var != null) {
                goalRaw.put(var, ((Number) row[1]).longValue());
                goalUsers.put(var, ((Number) row[2]).longValue());
                goalValues.put(var, ((Number) row[3]).doubleValue());
            }
        }

        Set<String> allVariations = new TreeSet<>();
        allVariations.addAll(expUsers.keySet());
        allVariations.addAll(goalUsers.keySet());
        if (allVariations.isEmpty()) {
            allVariations.add("off");
            allVariations.add("on");
        }

        List<VariationMetric> metrics = new ArrayList<>();
        for (String var : allVariations) {
            long exposed = expUsers.getOrDefault(var, 0L);
            long rawExp = expRaw.getOrDefault(var, 0L);
            long convUsers = goalUsers.getOrDefault(var, 0L);
            long rawGoals = goalRaw.getOrDefault(var, 0L);
            double val = goalValues.getOrDefault(var, 0.0);
            double rate = exposed > 0 ? (double) convUsers / exposed : 0.0;

            metrics.add(new VariationMetric(var, rawExp, exposed, rawGoals, convUsers, rate, val));
        }

        // SRM calculation
        long[] observed = new long[metrics.size()];
        double[] weights = new double[metrics.size()];
        for (int i = 0; i < metrics.size(); i++) {
            observed[i] = metrics.get(i).uniqueExposedUsers();
            // Default 50/50 or proportional to rollout
            weights[i] = 1.0 / metrics.size();
        }
        StatisticalCalculator.SrmResult srm = StatisticalCalculator.calculateSrm(observed, weights);

        // Z-Test between first two variations (baseline e.g. "off" and treatment e.g. "on")
        StatisticalCalculator.ZTestResult zTest = null;
        String baseline = "off";
        String treatment = "on";
        if (metrics.size() >= 2) {
            VariationMetric v0 = metrics.stream().filter(m -> "off".equalsIgnoreCase(m.variation())).findFirst().orElse(metrics.get(0));
            VariationMetric v1 = metrics.stream().filter(m -> "on".equalsIgnoreCase(m.variation())).findFirst().orElse(metrics.get(1));
            baseline = v0.variation();
            treatment = v1.variation();
            zTest = StatisticalCalculator.calculateZTest(
                v0.uniqueExposedUsers(), v0.uniqueConvertedUsers(),
                v1.uniqueExposedUsers(), v1.uniqueConvertedUsers()
            );
        }

        return new ExperimentReport(projectKey, envKey, flagKey, metrics, srm, zTest, baseline, treatment);
    }
}
