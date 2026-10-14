package dev.aieng.p01;

/**
 * Softmax, entropy and cross-entropy - the functions that turn model scores into probabilities and measure
 * how wrong they are. Every LLM training step minimises {@link #crossEntropy}.
 */
public final class Information {

    private Information() {
    }

    // region softmax
    /**
     * softmax(z)_i = exp(z_i) / sum_j exp(z_j). Subtracting max(z) first changes nothing mathematically
     * (it cancels in the ratio) but stops exp() overflowing to Infinity for large logits.
     */
    public static double[] softmax(double[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double z : logits) {
            max = Math.max(max, z);
        }
        double[] out = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            out[i] = Math.exp(logits[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    /** Softmax with temperature: divide logits by T. T < 1 sharpens the distribution, T > 1 flattens it. */
    public static double[] softmax(double[] logits, double temperature) {
        double[] scaled = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            scaled[i] = logits[i] / temperature;
        }
        return softmax(scaled);
    }

    /** log(softmax(z)) computed stably as z_i - logsumexp(z). Use this, not Math.log(softmax(z)). */
    public static double[] logSoftmax(double[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double z : logits) {
            max = Math.max(max, z);
        }
        double sum = 0;
        for (double z : logits) {
            sum += Math.exp(z - max);
        }
        double logSumExp = max + Math.log(sum);
        double[] out = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            out[i] = logits[i] - logSumExp;
        }
        return out;
    }
    // endregion

    // region entropy
    /** H(p) = -sum p_i log p_i (nats). Zero for a certain outcome, log(n) for a uniform one. */
    public static double entropy(double[] p) {
        double h = 0;
        for (double pi : p) {
            if (pi > 0) {
                h -= pi * Math.log(pi);
            }
        }
        return h;
    }

    /**
     * Cross-entropy loss for one example whose correct class is {@code target}: -log softmax(logits)[target].
     * This is the loss that trains every classifier and every LLM ("next-token prediction").
     */
    public static double crossEntropy(double[] logits, int target) {
        return -logSoftmax(logits)[target];
    }

    /** KL(p || q) = sum p_i log(p_i / q_i) >= 0: the extra cost of using q when the truth is p. */
    public static double klDivergence(double[] p, double[] q) {
        double kl = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0) {
                kl += p[i] * Math.log(p[i] / q[i]);
            }
        }
        return kl;
    }

    /** Perplexity = exp(average cross-entropy). "The model is as confused as if choosing among N options." */
    public static double perplexity(double averageCrossEntropy) {
        return Math.exp(averageCrossEntropy);
    }
    // endregion
}
