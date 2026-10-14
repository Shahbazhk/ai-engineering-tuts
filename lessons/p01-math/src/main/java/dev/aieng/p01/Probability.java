package dev.aieng.p01;

import java.util.Random;

/** The probability tools language models use at every step: distributions, expectation, sampling, Bayes. */
public final class Probability {

    private Probability() {
    }

    public static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    /** Population variance: E[(X - mu)^2]. */
    public static double variance(double[] xs) {
        double mu = mean(xs);
        double s = 0;
        for (double x : xs) {
            s += (x - mu) * (x - mu);
        }
        return s / xs.length;
    }

    // region sample
    /**
     * Draws index i with probability p[i] by inverse-CDF sampling: pick u in [0, 1) and walk the cumulative sum
     * until it passes u. This is literally how an LLM picks its next token from the softmax distribution.
     */
    public static int sampleCategorical(double[] p, Random rnd) {
        double u = rnd.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < p.length; i++) {
            cumulative += p[i];
            if (u < cumulative) {
                return i;
            }
        }
        return p.length - 1; // guards against rounding when p sums to 0.9999999
    }
    // endregion

    /** Bayes: P(A|B) = P(B|A) P(A) / P(B), with P(B) expanded by the law of total probability. */
    public static double bayes(double pBGivenA, double pA, double pBGivenNotA) {
        double pB = pBGivenA * pA + pBGivenNotA * (1 - pA);
        return pBGivenA * pA / pB;
    }

    /** Standard normal sample via the Box-Muller transform (what Random.nextGaussian does internally, roughly). */
    public static double gaussian(Random rnd) {
        double u1 = 1 - rnd.nextDouble(); // avoid log(0)
        double u2 = rnd.nextDouble();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }
}
