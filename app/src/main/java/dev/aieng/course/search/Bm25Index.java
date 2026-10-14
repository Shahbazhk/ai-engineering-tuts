package dev.aieng.course.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Okapi BM25 over an in-memory inverted index - the same algorithm Lucene, Elasticsearch and OpenSearch use
 * by default, built from scratch in Phase 8. Documents are opaque ints; callers map them back.
 *
 * score(q, d) = sum over terms t in q of IDF(t) * f(t,d) * (k1 + 1) / (f(t,d) + k1 * (1 - b + b * |d| / avgdl))
 */
public final class Bm25Index {

    public record Scored(int doc, double score) {
    }

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "and", "or", "of", "to", "in", "is", "it",
            "for", "on", "with", "as", "by", "be", "are", "this", "that", "at", "from", "what", "how", "why", "do",
            "does", "i", "you", "we", "can", "when", "which", "should", "into", "vs", "not", "if", "then", "than");

    private final Map<String, Map<Integer, Integer>> postings = new HashMap<>();
    private final List<Integer> lengths = new ArrayList<>();
    private long totalLength;

    /** Adds a document and returns its id (0, 1, 2, ...). */
    public int add(String text) {
        int doc = lengths.size();
        List<String> tokens = tokenize(text);
        for (String t : tokens) {
            postings.computeIfAbsent(t, k -> new HashMap<>()).merge(doc, 1, Integer::sum);
        }
        lengths.add(tokens.size());
        totalLength += tokens.size();
        return doc;
    }

    public List<Scored> search(String query, int k) {
        int n = lengths.size();
        if (n == 0) {
            return List.of();
        }
        double avgdl = (double) totalLength / n;
        Map<Integer, Double> scores = new HashMap<>();
        for (String term : Set.copyOf(tokenize(query))) {
            Map<Integer, Integer> docs = postings.get(term);
            if (docs == null) {
                continue;
            }
            double idf = Math.log(1 + (n - docs.size() + 0.5) / (docs.size() + 0.5));
            docs.forEach((doc, tf) -> {
                double norm = tf + K1 * (1 - B + B * lengths.get(doc) / avgdl);
                scores.merge(doc, idf * tf * (K1 + 1) / norm, Double::sum);
            });
        }
        PriorityQueue<Scored> top = new PriorityQueue<>((a, b) -> Double.compare(a.score(), b.score()));
        scores.forEach((doc, s) -> {
            top.add(new Scored(doc, s));
            if (top.size() > k) {
                top.poll();
            }
        });
        List<Scored> out = new ArrayList<>(top);
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    public int size() {
        return lengths.size();
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() > 1 && !STOPWORDS.contains(raw)) {
                out.add(stem(raw));
            }
        }
        return out;
    }

    /** Deliberately tiny suffix stripper so "embeddings" matches "embedding". ponytail: swap for Porter if recall suffers. */
    static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
