package dev.aieng.p12;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

/**
 * Runs a dataset through a system under test, scores every case, aggregates with a bootstrap confidence interval,
 * and gates against an absolute threshold AND the previous baseline. This is what turns "the prompt looks better"
 * into a release decision.
 */
public final class EvalRunner {

    public record Case(String id, String input, String expected, List<String> tags) {
    }

    public record CaseResult(Case testCase, String output, double score) {
    }

    public record Report(double mean, double ciLow, double ciHigh, List<CaseResult> results, List<String> failures,
                         Map<String, Double> byTag) {
        public boolean passed() {
            return failures.isEmpty();
        }
    }

    private EvalRunner() {
    }

    // region run
    public static Report run(List<Case> dataset, Function<String, String> system,
                             ToDoubleBiFunction<String, Case> metric, double threshold, Double baseline, double tolerance) {
        List<CaseResult> results = new ArrayList<>();
        for (Case c : dataset) {
            String output = system.apply(c.input());
            results.add(new CaseResult(c, output, metric.applyAsDouble(output, c)));
        }
        double[] scores = results.stream().mapToDouble(CaseResult::score).toArray();
        double mean = mean(scores);
        double[] ci = bootstrap(scores, 1000, 7);
        List<String> failures = new ArrayList<>();
        if (mean < threshold) {
            failures.add("mean %.3f below threshold %.3f".formatted(mean, threshold));
        }
        if (baseline != null && mean < baseline - tolerance) {
            failures.add("regression: mean %.3f vs baseline %.3f (tolerance %.3f)".formatted(mean, baseline, tolerance));
        }
        Map<String, Double> byTag = new java.util.TreeMap<>();
        results.stream().flatMap(r -> r.testCase().tags().stream().map(t -> Map.entry(t, r.score())))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        java.util.stream.Collectors.averagingDouble(Map.Entry::getValue)))
                .forEach(byTag::put);
        return new Report(mean, ci[0], ci[1], results, failures, byTag);
    }
    // endregion

    // region bootstrap
    /** 95% bootstrap CI of the mean: resample cases with replacement, take the 2.5th and 97.5th percentiles. */
    static double[] bootstrap(double[] scores, int resamples, long seed) {
        Random rnd = new Random(seed);
        double[] means = new double[resamples];
        for (int r = 0; r < resamples; r++) {
            double sum = 0;
            for (int i = 0; i < scores.length; i++) {
                sum += scores[rnd.nextInt(scores.length)];
            }
            means[r] = sum / scores.length;
        }
        java.util.Arrays.sort(means);
        return new double[] {means[(int) (resamples * 0.025)], means[(int) (resamples * 0.975) - 1]};
    }
    // endregion

    static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return xs.length == 0 ? 0 : s / xs.length;
    }
}
