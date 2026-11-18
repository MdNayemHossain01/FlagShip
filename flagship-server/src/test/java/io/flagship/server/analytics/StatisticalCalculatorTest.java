package io.flagship.server.analytics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalCalculatorTest {

    @Test
    void testSrmCalculation_NormalTraffic_NoSrm() {
        // 50/50 expected weights, observed 505 and 495
        long[] observed = new long[]{505, 495};
        double[] weights = new double[]{0.5, 0.5};

        StatisticalCalculator.SrmResult result = StatisticalCalculator.calculateSrm(observed, weights);
        assertFalse(result.srmDetected(), "Should NOT detect SRM for normal traffic variation");
        assertTrue(result.pValue() > 0.05, "p-value should be well above alpha 0.05");
        assertTrue(result.explanation().contains("PASS"));
    }

    @Test
    void testSrmCalculation_BiasedTraffic_SrmDetected() {
        // 50/50 expected weights, but observed 700 and 300 (massive bias!)
        long[] observed = new long[]{700, 300};
        double[] weights = new double[]{0.5, 0.5};

        StatisticalCalculator.SrmResult result = StatisticalCalculator.calculateSrm(observed, weights);
        assertTrue(result.srmDetected(), "MUST detect SRM for heavily skewed traffic");
        assertTrue(result.pValue() < 0.001, "p-value must be < 0.001");
        assertTrue(result.chiSquare() > 10.83, "Chi-square statistic must exceed threshold");
        assertTrue(result.explanation().contains("CRITICAL"));
    }

    @Test
    void testTwoProportionZTest_StatisticallySignificantUplift() {
        // Baseline: 500 exposed, 50 converted (10.0%)
        // Treatment: 500 exposed, 75 converted (15.0%)
        StatisticalCalculator.ZTestResult result = StatisticalCalculator.calculateZTest(500, 50, 500, 75);

        assertEquals(0.10, result.baselineRate(), 0.001);
        assertEquals(0.15, result.treatmentRate(), 0.001);
        assertEquals(50.0, result.relativeUpliftPercent(), 0.01);
        assertTrue(result.zScore() > 1.96, "Z-score should exceed critical value 1.96");
        assertTrue(result.pValue() < 0.05, "p-value should be statistically significant (< 0.05)");
        assertTrue(result.significant());
        assertTrue(result.ciLower95() > 0, "Lower bound of 95% CI should be strictly positive");
    }

    @Test
    void testTwoProportionZTest_NotStatisticallySignificant() {
        // Baseline: 100 exposed, 10 converted (10%)
        // Treatment: 100 exposed, 11 converted (11%) -> likely noise
        StatisticalCalculator.ZTestResult result = StatisticalCalculator.calculateZTest(100, 10, 100, 11);

        assertFalse(result.significant());
        assertTrue(result.pValue() > 0.5, "p-value should be high for 1% difference with small N");
        assertTrue(result.ciLower95() < 0 && result.ciUpper95() > 0, "Confidence interval should span zero");
    }
}
