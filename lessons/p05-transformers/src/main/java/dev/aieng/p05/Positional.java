package dev.aieng.p05;

/**
 * Attention is order-blind: shuffle the tokens and every output is shuffled the same way. Positional information
 * has to be injected. Two schemes: the original sinusoidal encoding (added to embeddings) and RoPE (rotary
 * position embedding, used by Llama, Mistral, Qwen), which rotates queries and keys by a position-dependent angle.
 */
public final class Positional {

    private Positional() {
    }

    // region sinusoidal
    /** PE(pos, 2i) = sin(pos / 10000^(2i/d)), PE(pos, 2i+1) = cos(pos / 10000^(2i/d)). */
    public static double[][] sinusoidal(int positions, int dim) {
        double[][] pe = new double[positions][dim];
        for (int pos = 0; pos < positions; pos++) {
            for (int i = 0; i < dim; i += 2) {
                double angle = pos / Math.pow(10000, (double) i / dim);
                pe[pos][i] = Math.sin(angle);
                if (i + 1 < dim) {
                    pe[pos][i + 1] = Math.cos(angle);
                }
            }
        }
        return pe;
    }
    // endregion

    // region rope
    /**
     * RoPE: treat each pair (x[2i], x[2i+1]) as a 2-D point and rotate it by pos * theta_i. Because rotations
     * compose, q(pos_a) . k(pos_b) then depends only on the RELATIVE offset pos_a - pos_b.
     */
    public static double[] rope(double[] x, int pos) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i += 2) {
            double theta = pos / Math.pow(10000, (double) i / x.length);
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            out[i] = x[i] * cos - x[i + 1] * sin;
            out[i + 1] = x[i] * sin + x[i + 1] * cos;
        }
        return out;
    }
    // endregion
}
