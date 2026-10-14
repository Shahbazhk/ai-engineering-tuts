package dev.aieng.p17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class DeliveryTest {

    @Test
    void pertMeanAndTriangularSampling() {
        assertEquals(9.0, new MonteCarloEstimate.Task("t", 4, 8, 18, 0).pertMean(), 1e-12);
        Random rnd = new Random(1);
        double sum = 0;
        for (int i = 0; i < 100_000; i++) {
            double x = MonteCarloEstimate.triangular(2, 5, 14, rnd);
            assertTrue(x >= 2 && x <= 14);
            sum += x;
        }
        assertEquals((2 + 5 + 14) / 3.0, sum / 100_000, 0.05, "triangular mean = (a + c + b) / 3");
    }

    @Test
    void reworkRiskPushesTheForecastBeyondPert() {
        List<MonteCarloEstimate.Task> noRisk = List.of(new MonteCarloEstimate.Task("a", 5, 10, 20, 0),
                new MonteCarloEstimate.Task("b", 5, 10, 20, 0));
        List<MonteCarloEstimate.Task> risky = List.of(new MonteCarloEstimate.Task("a", 5, 10, 20, 0.4),
                new MonteCarloEstimate.Task("b", 5, 10, 20, 0.4));
        var calm = MonteCarloEstimate.forecast(noRisk, 20_000, 2);
        var stormy = MonteCarloEstimate.forecast(risky, 20_000, 2);
        assertTrue(calm.p50() <= calm.p80() && calm.p80() <= calm.p95());
        assertEquals(calm.pertTotal(), stormy.pertTotal(), 1e-12, "PERT cannot see rework risk");
        assertTrue(stormy.p80() > calm.p80() + 3, "Monte Carlo can: " + calm.p80() + " vs " + stormy.p80());
    }

    @Test
    void businessCaseIncludesReviewTimeAndFindsTheQualityBar() {
        var a = new BusinessCase.Assumptions(10_000, 6, 0.5, 0.8, 1, 60, 0.05, 1_000, 30_000);
        BusinessCase.Result r = BusinessCase.evaluate(a);
        // used 5,000 tasks; saved (0.8 * 6 - 1) = 3.8 min each = 316.67 h = €19,000; cost 250 + 1,000
        assertEquals(19_000, r.monthlyBenefit(), 1e-6);
        assertEquals(1_250, r.monthlyCost(), 1e-6);
        assertEquals(30_000 / 17_750.0, r.paybackMonths(), 1e-9);
        double bar = BusinessCase.breakEvenUsableRate(a);
        assertEquals(0.2083, bar, 1e-3, "below ~21% usable outputs the feature loses money");
        var useless = new BusinessCase.Assumptions(10_000, 6, 0.5, 0.1, 1, 60, 0.05, 1_000, 30_000);
        assertTrue(Double.isInfinite(BusinessCase.evaluate(useless).paybackMonths()));
    }
}
