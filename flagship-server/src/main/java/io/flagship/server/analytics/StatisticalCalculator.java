package io.flagship.server.analytics;

public final class StatisticalCalculator {

    public record SrmResult(
        double chiSquare,
        int degreesOfFreedom,
        double pValue,
        boolean srmDetected,
        String explanation
    ) {}

    public record ZTestResult(
        double baselineRate,
        double treatmentRate,
        double absoluteDifference,
        double relativeUpliftPercent,
        double zScore,
        double pValue,
        double ciLower95,
        double ciUpper95,
        boolean significant,
        String conclusion
    ) {}

    /**
     * Compute Sample Ratio Mismatch (SRM) using Chi-Square goodness-of-fit.
     * @param observedCounts observed user count per variation
     * @param expectedWeights expected rollout weight/percentage per variation
     */
    public static SrmResult calculateSrm(long[] observedCounts, double[] expectedWeights) {
        if (observedCounts == null || expectedWeights == null || observedCounts.length != expectedWeights.length || observedCounts.length < 2) {
            return new SrmResult(0.0, 1, 1.0, false, "Insufficient variations for SRM calculation");
        }

        long totalObserved = 0;
        double totalWeight = 0;
        for (int i = 0; i < observedCounts.length; i++) {
            totalObserved += observedCounts[i];
            totalWeight += expectedWeights[i];
        }

        if (totalObserved < 20 || totalWeight <= 0) {
            return new SrmResult(0.0, observedCounts.length - 1, 1.0, false, "Sample size too small (< 20 users) to detect SRM");
        }

        double chiSquare = 0.0;
        for (int i = 0; i < observedCounts.length; i++) {
            double expectedCount = totalObserved * (expectedWeights[i] / totalWeight);
            if (expectedCount > 0) {
                double diff = observedCounts[i] - expectedCount;
                chiSquare += (diff * diff) / expectedCount;
            }
        }

        int df = observedCounts.length - 1;
        double pValue = chiSquarePValue(chiSquare, df);
        boolean srmDetected = pValue < 0.001; // Standard threshold in experimentation platforms

        String explanation = srmDetected
            ? String.format("CRITICAL: Sample Ratio Mismatch detected (p = %.5f < 0.001, Chi2 = %.2f). Traffic split is biased. Do not trust conversion metrics!", pValue, chiSquare)
            : String.format("PASS: No SRM detected (p = %.4f >= 0.001, Chi2 = %.2f). Traffic distribution matches expected weights.", pValue, chiSquare);

        return new SrmResult(chiSquare, df, pValue, srmDetected, explanation);
    }

    /**
     * Compute Two-Proportion Z-Test between treatment and baseline variations.
     */
    public static ZTestResult calculateZTest(long exposedBaseline, long convertedBaseline,
                                             long exposedTreatment, long convertedTreatment) {
        if (exposedBaseline <= 0 || exposedTreatment <= 0) {
            return new ZTestResult(0, 0, 0, 0, 0, 1.0, 0, 0, false, "Insufficient sample size");
        }

        double pB = (double) convertedBaseline / exposedBaseline;
        double pT = (double) convertedTreatment / exposedTreatment;
        double diff = pT - pB;
        double uplift = pB > 0 ? (diff / pB) * 100.0 : 0.0;

        // Pooled proportion for hypothesis testing (H0: pB = pT)
        double pooled = (double) (convertedBaseline + convertedTreatment) / (exposedBaseline + exposedTreatment);
        double se = Math.sqrt(pooled * (1.0 - pooled) * (1.0 / exposedBaseline + 1.0 / exposedTreatment));

        double z = se > 0 ? diff / se : 0.0;
        double pValue = 2.0 * (1.0 - normalCdf(Math.abs(z)));

        // Unpooled standard error for 95% Confidence Interval
        double seDiff = Math.sqrt((pB * (1.0 - pB) / exposedBaseline) + (pT * (1.0 - pT) / exposedTreatment));
        double margin = 1.95996 * seDiff;
        double ciLower = diff - margin;
        double ciUpper = diff + margin;

        boolean significant = pValue < 0.05 && (exposedBaseline >= 30 && exposedTreatment >= 30);

        String conclusion;
        if (!significant) {
            conclusion = String.format("Not statistically significant (p = %.4f, Z = %.2f). Cannot reject null hypothesis at alpha = 0.05.", pValue, z);
        } else if (diff > 0) {
            conclusion = String.format("Statistically significant positive uplift (+%.2f%%, p = %.4f, 95%% CI: [%.3f, %.3f]).", uplift, pValue, ciLower, ciUpper);
        } else {
            conclusion = String.format("Statistically significant negative impact (%.2f%%, p = %.4f, 95%% CI: [%.3f, %.3f]).", uplift, pValue, ciLower, ciUpper);
        }

        return new ZTestResult(pB, pT, diff, uplift, z, pValue, ciLower, ciUpper, significant, conclusion);
    }

    /**
     * Standard normal cumulative distribution function (Φ).
     */
    public static double normalCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    /**
     * Approximation of the error function (Abramowitz and Stegun).
     */
    private static double erf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x = Math.abs(x);
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        return sign * y;
    }

    /**
     * Survival function P(Chi^2 > x) for degrees of freedom df.
     */
    private static double chiSquarePValue(double chiSquare, int df) {
        if (chiSquare <= 0) return 1.0;
        if (df == 1) {
            return 2.0 * (1.0 - normalCdf(Math.sqrt(chiSquare)));
        }
        // Wilson-Hilferty transformation approximation for general df
        double s = 2.0 / (9.0 * df);
        double z = (Math.pow(chiSquare / df, 1.0 / 3.0) - (1.0 - s)) / Math.sqrt(s);
        return 1.0 - normalCdf(z);
    }
}
