package dev.aieng.p05;

/**
 * Scaled dot-product attention (Vaswani et al., 2017):
 * Attention(Q, K, V) = softmax(Q K^T / sqrt(d_k) + mask) V.
 * Each query row asks "which positions are relevant to me?", scores every key, and takes a weighted average of
 * the values. That single operation is what lets a token's representation depend on the whole context.
 */
public final class Attention {

    public record Result(double[][] output, double[][] weights) {
    }

    private Attention() {
    }

    // region attention
    /**
     * @param q (n x d_k) queries, one per position
     * @param k (m x d_k) keys
     * @param v (m x d_v) values
     * @param causal if true, query i may only attend to keys 0..i (plus any earlier cached keys: m - n offset)
     */
    public static Result scaledDotProduct(double[][] q, double[][] k, double[][] v, boolean causal) {
        int n = q.length;
        int m = k.length;
        int offset = m - n; // with a KV cache, the n new queries sit at the END of m positions
        double scale = 1 / Math.sqrt(q[0].length);
        double[][] scores = M.matmul(q, M.transpose(k));
        double[][] weights = new double[n][];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                scores[i][j] *= scale;
                if (causal && j > i + offset) {
                    scores[i][j] = Double.NEGATIVE_INFINITY; // future positions get probability exactly 0
                }
            }
            weights[i] = M.softmax(scores[i]);
        }
        return new Result(M.matmul(weights, v), weights);
    }
    // endregion
}
