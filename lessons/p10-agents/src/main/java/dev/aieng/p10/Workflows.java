package dev.aieng.p10;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The five workflow patterns from Anthropic's "Building effective agents" (2024). Workflows follow code paths
 * you define; agents choose their own path. Prefer the simplest pattern that passes your evals.
 */
public final class Workflows {

    private Workflows() {
    }

    // region chain
    /** Prompt chaining: fixed sequence of steps; a gate between steps stops early on bad intermediate output. */
    public static String chain(String input, List<Function<String, String>> steps, Predicate<String> gate) {
        String current = input;
        for (int i = 0; i < steps.size(); i++) {
            current = steps.get(i).apply(current);
            if (!gate.test(current)) {
                throw new IllegalStateException("gate failed after step " + (i + 1) + ": " + current);
            }
        }
        return current;
    }
    // endregion

    // region route
    /** Routing: classify once, then hand off to a specialised handler (different prompt, tools or model). */
    public static String route(String input, Function<String, String> classifier,
                               Map<String, Function<String, String>> handlers, Function<String, String> fallback) {
        return handlers.getOrDefault(classifier.apply(input).strip().toUpperCase(), fallback).apply(input);
    }
    // endregion

    // region parallel
    /** Parallelisation: independent sub-tasks run concurrently on virtual threads, then aggregate. */
    public static String parallel(String input, List<Function<String, String>> workers, Function<List<String>, String> aggregate) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (Function<String, String> w : workers) {
                futures.add(pool.submit(() -> w.apply(input)));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            return aggregate.apply(results);
        } catch (Exception e) {
            throw new IllegalStateException("parallel step failed", e);
        }
    }
    // endregion

    // region orchestrator
    /** Orchestrator-workers: a planner decides the sub-tasks at run time; workers do them; a synthesiser combines. */
    public static String orchestrate(String task, Function<String, List<String>> planner, Function<String, String> worker,
                                     Function<List<String>, String> synthesiser, int maxSubtasks) {
        List<String> plan = planner.apply(task);
        if (plan.size() > maxSubtasks) {
            plan = plan.subList(0, maxSubtasks); // the model does not get to decide how much we spend
        }
        List<String> results = new ArrayList<>();
        for (String subtask : plan) {
            results.add(subtask + " => " + worker.apply(subtask));
        }
        return synthesiser.apply(results);
    }
    // endregion

    // region evaluator
    /** Evaluator-optimiser: generate, critique against criteria, revise; stop on approval or after maxRounds. */
    public record Critique(boolean approved, String feedback) {
    }

    public static String refine(String task, Function<String, String> generator, Function<String, Critique> evaluator,
                                int maxRounds) {
        String draft = generator.apply(task);
        for (int round = 1; round < maxRounds; round++) {
            Critique c = evaluator.apply(draft);
            if (c.approved()) {
                return draft;
            }
            draft = generator.apply(task + "\nPrevious draft:\n" + draft + "\nFix this feedback:\n" + c.feedback());
        }
        return draft;
    }
    // endregion
}
