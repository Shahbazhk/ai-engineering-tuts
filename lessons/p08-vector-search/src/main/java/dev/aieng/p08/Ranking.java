package dev.aieng.p08;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/** Combining and diversifying ranked lists: Reciprocal Rank Fusion and Maximal Marginal Relevance. */
public final class Ranking {

    private Ranking() {
    }

    // region rrf
    /**
     * RRF (Cormack et al., 2009): score(d) = sum over lists of 1 / (k + rank(d)), rank starting at 1. Uses ranks
     * only, so BM25 scores (unbounded) and cosine scores (-1..1) can be fused without normalisation. k = 60 is
     * the conventional constant; it damps the influence of the very top positions.
     */
    public static List<Integer> rrf(List<List<Integer>> rankings, int k) {
        Map<Integer, Double> scores = new HashMap<>();
        for (List<Integer> ranking : rankings) {
            for (int r = 0; r < ranking.size(); r++) {
                scores.merge(ranking.get(r), 1.0 / (k + r + 1), Double::sum);
            }
        }
        List<Integer> fused = new ArrayList<>(scores.keySet());
        fused.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        return fused;
    }
    // endregion

    // region mmr
    /**
     * MMR (Carbonell & Goldstein, 1998): pick documents one at a time, maximising
     * lambda * sim(query, d) - (1 - lambda) * max sim(d, already picked). lambda = 1 is pure relevance;
     * lower values trade relevance for diversity, avoiding five near-identical chunks in a RAG prompt.
     */
    public static List<Integer> mmr(float[] query, List<Integer> candidates, IntFunction<float[]> vectorOf,
                                    int k, double lambda) {
        List<Integer> selected = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>(candidates);
        while (selected.size() < k && !remaining.isEmpty()) {
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int c : remaining) {
                double relevance = VectorIndex.dot(VectorIndex.normalize(query), VectorIndex.normalize(vectorOf.apply(c)));
                double redundancy = 0;
                for (int s : selected) {
                    redundancy = Math.max(redundancy,
                            VectorIndex.dot(VectorIndex.normalize(vectorOf.apply(c)), VectorIndex.normalize(vectorOf.apply(s))));
                }
                double score = lambda * relevance - (1 - lambda) * redundancy;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            selected.add(best);
            remaining.remove(Integer.valueOf(best));
        }
        return selected;
    }
    // endregion
}
