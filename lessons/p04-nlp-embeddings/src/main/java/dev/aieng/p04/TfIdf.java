package dev.aieng.p04;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TF-IDF vectors: the classic way to turn text into numbers. Each dimension is a vocabulary word; its value is
 * how often the word appears in the document (TF), down-weighted if it appears in many documents (IDF).
 * Sparse, exact-word, no notion of meaning - the baseline embeddings must beat.
 */
public final class TfIdf {

    private final Map<String, Integer> vocabulary = new LinkedHashMap<>();
    private double[] idf;

    // region fit
    public TfIdf fit(List<String> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String doc : documents) {
            for (String term : new java.util.HashSet<>(tokenize(doc))) {
                vocabulary.putIfAbsent(term, vocabulary.size());
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        idf = new double[vocabulary.size()];
        int n = documents.size();
        vocabulary.forEach((term, i) -> idf[i] = Math.log((1.0 + n) / (1 + documentFrequency.get(term))) + 1); // smoothed
        return this;
    }

    /** L2-normalised TF-IDF vector; unknown words are ignored (the "out of vocabulary" problem). */
    public double[] vector(String text) {
        double[] v = new double[vocabulary.size()];
        for (String term : tokenize(text)) {
            Integer i = vocabulary.get(term);
            if (i != null) {
                v[i] += 1;
            }
        }
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] *= idf[i];
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] = norm == 0 ? 0 : v[i] / norm;
        }
        return v;
    }
    // endregion

    /** Indexes of documents ordered by cosine similarity to the query. */
    public List<Integer> rank(List<String> documents, String query) {
        double[] q = vector(query);
        List<Integer> order = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int d = 0; d < documents.size(); d++) {
            order.add(d);
            double[] v = vector(documents.get(d));
            double s = 0;
            for (int i = 0; i < v.length; i++) {
                s += v[i] * q[i];
            }
            scores.add(s);
        }
        order.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        return order;
    }

    public int vocabularySize() {
        return vocabulary.size();
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
