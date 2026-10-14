package dev.aieng.p07;

import java.util.ArrayList;
import java.util.List;

/**
 * Context engineering as a packing problem: fit the most valuable content into a fixed token budget, in an order
 * that helps both the model (important material at the start and end) and the provider's prompt cache (stable
 * content first, per-request content last).
 */
public final class ContextBudget {

    public enum Kind { SYSTEM, TOOLS, DOCUMENT, HISTORY, QUESTION }

    /** priority: higher wins when the budget is tight. required: never dropped (fails loudly if it cannot fit). */
    public record Part(Kind kind, String text, int priority, boolean required) {
        int tokens() {
            return estimateTokens(text);
        }
    }

    public record Packed(List<Part> parts, int tokens, List<Part> dropped) {
        public String render() {
            StringBuilder sb = new StringBuilder();
            parts.forEach(p -> sb.append(p.text()).append("\n\n"));
            return sb.toString().strip();
        }
    }

    private ContextBudget() {
    }

    // region pack
    public static Packed pack(List<Part> candidates, int budget) {
        List<Part> chosen = new ArrayList<>();
        List<Part> dropped = new ArrayList<>();
        int used = 0;
        for (Part p : candidates) {
            if (p.required()) {
                chosen.add(p);
                used += p.tokens();
            }
        }
        if (used > budget) {
            throw new IllegalStateException("required context alone needs " + used + " tokens > budget " + budget);
        }
        List<Part> optional = new ArrayList<>(candidates.stream().filter(p -> !p.required()).toList());
        optional.sort((a, b) -> Integer.compare(b.priority(), a.priority())); // greedy by priority
        for (Part p : optional) {
            if (used + p.tokens() <= budget) {
                chosen.add(p);
                used += p.tokens();
            } else {
                dropped.add(p);
            }
        }
        // Final order: stable prefix first (cacheable), then documents best-first, history, question last.
        chosen.sort((a, b) -> a.kind() != b.kind()
                ? Integer.compare(a.kind().ordinal(), b.kind().ordinal())
                : Integer.compare(b.priority(), a.priority()));
        return new Packed(chosen, used, dropped);
    }
    // endregion

    /** ~4 characters per token for English; use the model's real tokenizer (JTokkit, DJL) in production. */
    public static int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }
}
