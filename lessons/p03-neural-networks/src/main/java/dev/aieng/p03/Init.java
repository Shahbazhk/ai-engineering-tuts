package dev.aieng.p03;

import java.util.Random;

/**
 * Why weight initialisation matters: push data through a deep stack of tanh layers and watch the activations.
 * Too-small weights shrink the signal to zero (vanishing), too-large ones saturate tanh at +/-1 where its
 * gradient is zero. Xavier/Glorot init (std = 1/sqrt(fan_in)) keeps the variance roughly constant.
 */
public final class Init {

    private Init() {
    }

    // region experiment
    /** Returns the standard deviation of the activations after each of {@code depth} tanh layers. */
    public static double[] activationStd(double weightStd, int width, int depth, long seed) {
        Random rnd = new Random(seed);
        Tensor x = Tensor.randn(256, width, 1.0, rnd);
        double[] stds = new double[depth];
        for (int layer = 0; layer < depth; layer++) {
            x = x.matmul(Tensor.randn(width, width, weightStd, rnd)).tanh();
            stds[layer] = std(x.data);
        }
        return stds;
    }

    public static double xavier(int fanIn) {
        return 1 / Math.sqrt(fanIn);
    }

    /** He/Kaiming init for ReLU: ReLU zeroes half the signal, so the variance needs doubling. */
    public static double he(int fanIn) {
        return Math.sqrt(2.0 / fanIn);
    }
    // endregion

    static double std(double[] v) {
        double mean = 0;
        for (double x : v) {
            mean += x / v.length;
        }
        double var = 0;
        for (double x : v) {
            var += (x - mean) * (x - mean) / v.length;
        }
        return Math.sqrt(var);
    }

    public static void main(String[] args) {
        int width = 256;
        for (double std : new double[] {0.01, xavier(width), 0.5}) {
            double[] s = activationStd(std, width, 10, 1);
            System.out.printf("weight std %.4f -> layer 1: %.4f, layer 5: %.4f, layer 10: %.4f%n", std, s[0], s[4], s[9]);
        }
    }
}
