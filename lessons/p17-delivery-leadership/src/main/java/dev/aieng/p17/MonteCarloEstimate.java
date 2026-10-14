package dev.aieng.p17;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Schedule estimation under uncertainty. PERT gives one "expected" number per task; Monte Carlo simulates the whole
 * plan thousands of times and reports a distribution, including the chance that an AI experiment fails and must be
 * repeated. Stakeholders get "80% likely by week 11" instead of a single date that is quietly a coin flip.
 */
public final class MonteCarloEstimate {

    /** Three-point estimate in days, plus the probability the task must be redone (e.g. an eval gate not met). */
    public record Task(String name, double optimistic, double mostLikely, double pessimistic, double reworkProbability) {
        /** Classic PERT mean: (o + 4m + p) / 6. */
        public double pertMean() {
            return (optimistic + 4 * mostLikely + pessimistic) / 6;
        }
    }

    public record Forecast(double pertTotal, double p50, double p80, double p95) {
    }

    private MonteCarloEstimate() {
    }

    // region simulate
    public static Forecast forecast(List<Task> tasks, int runs, long seed) {
        Random rnd = new Random(seed);
        double[] totals = new double[runs];
        for (int r = 0; r < runs; r++) {
            double total = 0;
            for (Task t : tasks) {
                total += triangular(t.optimistic(), t.mostLikely(), t.pessimistic(), rnd);
                // An AI experiment that misses its eval threshold is iterated: another (shorter) cycle of work.
                while (rnd.nextDouble() < t.reworkProbability()) {
                    total += 0.5 * triangular(t.optimistic(), t.mostLikely(), t.pessimistic(), rnd);
                }
            }
            totals[r] = total;
        }
        Arrays.sort(totals);
        double pert = tasks.stream().mapToDouble(Task::pertMean).sum();
        return new Forecast(pert, percentile(totals, 0.50), percentile(totals, 0.80), percentile(totals, 0.95));
    }

    /** Sample a triangular distribution by inverse CDF - the same technique as token sampling in Phase 1. */
    static double triangular(double a, double c, double b, Random rnd) {
        double u = rnd.nextDouble();
        double f = (c - a) / (b - a);
        return u < f ? a + Math.sqrt(u * (b - a) * (c - a)) : b - Math.sqrt((1 - u) * (b - a) * (b - c));
    }
    // endregion

    static double percentile(double[] sorted, double p) {
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
    }

    public static void main(String[] args) {
        List<Task> plan = List.of(
                new Task("Discovery + eval set (50 cases)", 5, 8, 15, 0.0),
                new Task("Ingestion pipeline for 2 sources", 8, 12, 25, 0.1),
                new Task("RAG prototype reaches recall@8 >= 0.85", 5, 10, 25, 0.4),
                new Task("Guardrails + permission tests", 4, 6, 12, 0.1),
                new Task("Pilot + feedback loop", 10, 10, 15, 0.2));
        Forecast f = forecast(plan, 20_000, 1);
        System.out.printf("PERT sum %.0f days | P50 %.0f | P80 %.0f | P95 %.0f%n", f.pertTotal(), f.p50(), f.p80(), f.p95());
    }
}
