package dev.aieng.p12;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ranking metrics for retrieval. "ranked" is the system's ordered result list; "relevant" the ground truth. */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    // region metrics
    /** Fraction of all relevant items that appear in the top k. The key metric for RAG: missing evidence = bad answer. */
    public static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) {
            return 1.0;
        }
        return (double) ranked.stream().limit(k).filter(relevant::contains).count() / relevant.size();
    }

    /** Fraction of the top k that is relevant. Matters when every retrieved chunk costs tokens. */
    public static double precisionAtK(List<String> ranked, Set<String> relevant, int k) {
        long n = Math.min(k, ranked.size());
        return n == 0 ? 0 : (double) ranked.stream().limit(k).filter(relevant::contains).count() / n;
    }

    /** Reciprocal rank of the first relevant item: 1 if first, 1/2 if second, 0 if absent. Averaged = MRR. */
    public static double reciprocalRank(List<String> ranked, Set<String> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    /**
     * nDCG@k with graded relevance: DCG = sum (2^rel - 1) / log2(rank + 1), normalised by the DCG of the ideal
     * ordering. Rewards putting the MOST relevant items at the very top.
     */
    public static double ndcgAtK(List<String> ranked, Map<String, Integer> grades, int k) {
        double dcg = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            dcg += gain(grades.getOrDefault(ranked.get(i), 0), i);
        }
        List<Integer> ideal = grades.values().stream().sorted((a, b) -> b - a).toList();
        double idcg = 0;
        for (int i = 0; i < Math.min(k, ideal.size()); i++) {
            idcg += gain(ideal.get(i), i);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }
    // endregion

    private static double gain(int grade, int position) {
        return (Math.pow(2, grade) - 1) / (Math.log(position + 2) / Math.log(2));
    }
}
