package dev.aieng.p14;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An AI gateway in miniature: pick a route per request (cheap model for easy requests, strong model for hard
 * ones), and fall back along a chain when a model fails or its circuit is open. Every decision is recorded so it
 * can be logged and measured.
 */
public final class ModelRouter {

    public record Model(String name, Function<String, String> call, Resilience.CircuitBreaker breaker) {
    }

    public record Route(String name, Predicate<String> matches, List<Model> chain) {
    }

    public record Result(String text, String route, String model, List<String> attempts) {
    }

    private final List<Route> routes;

    public ModelRouter(List<Route> routes) {
        this.routes = routes;
    }

    // region route
    public Result handle(String prompt) {
        Route route = routes.stream().filter(r -> r.matches().test(prompt)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no route matches"));
        List<String> attempts = new ArrayList<>();
        for (Model m : route.chain()) {
            try {
                String text = m.breaker().call(() -> m.call().apply(prompt));
                attempts.add(m.name() + ":ok");
                return new Result(text, route.name(), m.name(), attempts);
            } catch (RuntimeException e) {
                attempts.add(m.name() + ":" + e.getMessage()); // fall through to the next model in the chain
            }
        }
        throw new IllegalStateException("all models failed for route " + route.name() + ": " + attempts);
    }
    // endregion

    /** A deliberately simple complexity heuristic. In production: a small classifier, or the cheap model's confidence. */
    public static boolean looksHard(String prompt) {
        String p = prompt.toLowerCase();
        return prompt.length() > 600 || p.contains("compare") || p.contains("analyse") || p.contains("step by step");
    }
}
