package dev.aieng.p06;

import java.util.Comparator;
import java.util.List;

/**
 * Back-of-envelope cost and latency model for choosing between LLMs. Prices change constantly: the numbers in
 * {@link #main} are illustrative placeholders - always plug in the provider's current price page.
 */
public final class TokenCostEstimator {

    /** Prices in USD per million tokens; throughput in output tokens per second; ttft in milliseconds. */
    public record Model(String name, double inputPerMillion, double outputPerMillion, double tokensPerSecond, double ttftMillis) {
    }

    public record Workload(long requestsPerDay, int inputTokens, int outputTokens, double cachedInputFraction,
                           double cachedInputDiscount) {
    }

    public record Estimate(Model model, double costPerRequest, double monthlyCost, double latencySeconds) {
    }

    private TokenCostEstimator() {
    }

    // region estimate
    public static Estimate estimate(Model m, Workload w) {
        double cachedInput = w.inputTokens() * w.cachedInputFraction();
        double freshInput = w.inputTokens() - cachedInput;
        double inputCost = (freshInput + cachedInput * (1 - w.cachedInputDiscount())) * m.inputPerMillion() / 1e6;
        double outputCost = w.outputTokens() * m.outputPerMillion() / 1e6;
        double perRequest = inputCost + outputCost;
        // latency ~ time to first token + output tokens / decode speed (Phase 5, lesson 5)
        double latency = m.ttftMillis() / 1000 + w.outputTokens() / m.tokensPerSecond();
        return new Estimate(m, perRequest, perRequest * w.requestsPerDay() * 30, latency);
    }
    // endregion

    /** English prose averages ~4 characters per token for most tokenizers; code and other languages vary widely. */
    public static int roughTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }

    public static List<Estimate> compare(List<Model> models, Workload w) {
        return models.stream().map(m -> estimate(m, w)).sorted(Comparator.comparingDouble(Estimate::monthlyCost)).toList();
    }

    public static void main(String[] args) {
        Workload support = new Workload(20_000, 3_000, 350, 0.6, 0.9);
        List<Model> candidates = List.of(
                new Model("frontier-large (hosted)", 3.00, 15.00, 60, 600),
                new Model("efficient-small (hosted)", 0.15, 0.60, 120, 300),
                new Model("llama3.2-3b (self-hosted, amortised GPU)", 0.05, 0.05, 40, 150));
        for (Estimate e : compare(candidates, support)) {
            System.out.printf("%-42s $%.5f/request  $%,10.2f/month  ~%.1fs latency%n",
                    e.model().name(), e.costPerRequest(), e.monthlyCost(), e.latencySeconds());
        }
    }
}
