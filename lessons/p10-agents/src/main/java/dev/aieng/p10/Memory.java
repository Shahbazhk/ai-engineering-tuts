package dev.aieng.p10;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Agent memory in two tiers: a bounded short-term window with summarisation, and a searchable long-term store. */
public final class Memory {

    private Memory() {
    }

    // region shortterm
    /** Keeps the last N messages verbatim; older ones are folded into a running summary (one model call). */
    public static final class ShortTerm {
        private final int window;
        private final Function<String, String> summarizer;
        private final Deque<Llm.Message> recent = new ArrayDeque<>();
        private String summary = "";

        public ShortTerm(int window, Function<String, String> summarizer) {
            this.window = window;
            this.summarizer = summarizer;
        }

        public void add(Llm.Message m) {
            recent.addLast(m);
            if (recent.size() > window) {
                Llm.Message evicted = recent.removeFirst();
                summary = summarizer.apply("Previous summary: " + summary + "\nAdd: " + evicted.role() + ": " + evicted.content());
            }
        }

        public List<Llm.Message> context() {
            List<Llm.Message> out = new ArrayList<>();
            if (!summary.isBlank()) {
                out.add(Llm.Message.system("Summary of earlier conversation: " + summary));
            }
            out.addAll(recent);
            return out;
        }
    }
    // endregion

    // region longterm
    /**
     * Long-term memory: facts saved explicitly ("remember that...") and recalled by relevance. Keyword overlap
     * here; an embedding model + vector store in production (Phases 4 and 8). Scoped per user - never shared.
     */
    public static final class LongTerm {
        record Fact(String userId, String text) {
        }

        private final List<Fact> facts = new ArrayList<>();

        public void remember(String userId, String fact) {
            facts.add(new Fact(userId, fact));
        }

        public List<String> recall(String userId, String query, int k) {
            Set<String> q = words(query);
            return facts.stream()
                    .filter(f -> f.userId().equals(userId))
                    .map(f -> new Object[] {f.text(), overlap(q, words(f.text()))})
                    .filter(x -> (int) x[1] > 0)
                    .sorted(Comparator.comparingInt((Object[] x) -> (int) x[1]).reversed())
                    .limit(k)
                    .map(x -> (String) x[0])
                    .toList();
        }

        private static Set<String> words(String s) {
            return Set.of(s.toLowerCase(Locale.ROOT).split("\\W+"));
        }

        private static int overlap(Set<String> a, Set<String> b) {
            return (int) a.stream().filter(w -> w.length() > 2 && b.contains(w)).count();
        }
    }
    // endregion
}
