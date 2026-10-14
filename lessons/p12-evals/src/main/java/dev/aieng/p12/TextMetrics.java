package dev.aieng.p12;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cheap, deterministic answer metrics. Use them first; reach for an LLM judge only where they fail. */
public final class TextMetrics {

    private TextMetrics() {
    }

    // region text
    /** Lowercase, strip accents and punctuation, collapse whitespace, drop articles (SQuAD-style normalisation). */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\b(a|an|the)\\b", " ");
        return n.replaceAll("\\s+", " ").strip();
    }

    public static boolean exactMatch(String predicted, String expected) {
        return normalize(predicted).equals(normalize(expected));
    }

    /** Token-overlap F1: partial credit for answers that are close but not identical. */
    public static double tokenF1(String predicted, String expected) {
        List<String> p = tokens(predicted);
        List<String> e = tokens(expected);
        if (p.isEmpty() || e.isEmpty()) {
            return p.equals(e) ? 1 : 0;
        }
        Map<String, Integer> counts = new HashMap<>();
        e.forEach(t -> counts.merge(t, 1, Integer::sum));
        int common = 0;
        for (String t : p) {
            if (counts.getOrDefault(t, 0) > 0) {
                common++;
                counts.merge(t, -1, Integer::sum);
            }
        }
        if (common == 0) {
            return 0;
        }
        double precision = (double) common / p.size();
        double recall = (double) common / e.size();
        return 2 * precision * recall / (precision + recall);
    }

    /** Fraction of required facts (keywords or short phrases) present in the answer. */
    public static double keyFactRecall(String answer, List<String> facts) {
        String a = normalize(answer);
        return facts.isEmpty() ? 1 : (double) facts.stream().filter(f -> a.contains(normalize(f))).count() / facts.size();
    }
    // endregion

    private static List<String> tokens(String s) {
        String n = normalize(s);
        return n.isEmpty() ? List.of() : new ArrayList<>(List.of(n.split(" ")));
    }
}
