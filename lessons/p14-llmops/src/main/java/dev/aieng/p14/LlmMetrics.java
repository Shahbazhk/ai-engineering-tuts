package dev.aieng.p14;

import java.time.Duration;
import java.util.function.Supplier;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Micrometer instrumentation for model calls using the OpenTelemetry GenAI semantic-convention names, so the
 * same dashboards work whether metrics come from here, from Spring AI's built-in observations, or from other
 * services. Spring AI already emits these for ChatClient calls; this shows what is inside.
 */
public final class LlmMetrics {

    public record Usage(String text, int inputTokens, int outputTokens) {
    }

    private final MeterRegistry registry;

    public LlmMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // region record
    public Usage observe(String operation, String model, String feature, Supplier<Usage> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            Usage u = call.get();
            tokens(model, feature, "input").record(u.inputTokens());
            tokens(model, feature, "output").record(u.outputTokens());
            return u;
        } catch (RuntimeException e) {
            outcome = e.getClass().getSimpleName();
            throw e;
        } finally {
            sample.stop(Timer.builder("gen_ai.client.operation.duration")
                    .tag("gen_ai.operation.name", operation)
                    .tag("gen_ai.request.model", model)
                    .tag("feature", feature)            // cost attribution: which product feature spent it
                    .tag("outcome", outcome)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry));
        }
    }

    private DistributionSummary tokens(String model, String feature, String type) {
        return DistributionSummary.builder("gen_ai.client.token.usage")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", type)
                .tag("feature", feature)
                .register(registry);
    }
    // endregion

    public double totalTokens(String type) {
        return registry.find("gen_ai.client.token.usage").tag("gen_ai.token.type", type).summaries().stream()
                .mapToDouble(DistributionSummary::totalAmount).sum();
    }

    public Duration maxLatency(String model) {
        Timer t = registry.find("gen_ai.client.operation.duration").tag("gen_ai.request.model", model).timer();
        return t == null ? Duration.ZERO : Duration.ofNanos((long) t.max(java.util.concurrent.TimeUnit.NANOSECONDS));
    }
}
