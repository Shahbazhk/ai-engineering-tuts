package dev.aieng.p01;

import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

/**
 * Derivatives by finite differences. Too slow to train with (one function call per parameter), but the
 * gold standard for checking hand-written or automatic gradients - "gradient checking".
 */
public final class Gradients {

    private static final double H = 1e-5;

    private Gradients() {
    }

    // region numeric
    /** f'(x) ~ (f(x + h) - f(x - h)) / 2h. The centred difference has error O(h^2) instead of O(h). */
    public static double derivative(DoubleUnaryOperator f, double x) {
        return (f.applyAsDouble(x + H) - f.applyAsDouble(x - H)) / (2 * H);
    }

    /** The gradient: one partial derivative per input, each computed by nudging that input alone. */
    public static double[] gradient(ToDoubleFunction<double[]> f, double[] x) {
        double[] grad = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double original = x[i];
            x[i] = original + H;
            double plus = f.applyAsDouble(x);
            x[i] = original - H;
            double minus = f.applyAsDouble(x);
            x[i] = original;
            grad[i] = (plus - minus) / (2 * H);
        }
        return grad;
    }
    // endregion

    /**
     * Relative error between an analytic gradient and a numeric one. Below ~1e-6 is a pass; above ~1e-3 means
     * the analytic gradient has a bug.
     */
    public static double relativeError(double[] analytic, double[] numeric) {
        double diff = Vectors.norm(Vectors.subtract(analytic, numeric));
        double scale = Vectors.norm(analytic) + Vectors.norm(numeric);
        return scale == 0 ? 0 : diff / scale;
    }

    // region chain
    /**
     * Chain rule worked by hand for f(x) = sigmoid(w * x + b) with respect to w:
     * df/dw = sigmoid'(z) * dz/dw = sigmoid(z) * (1 - sigmoid(z)) * x, where z = w * x + b.
     * Backpropagation (Phase 3) is this rule applied mechanically through a whole graph.
     */
    public static double sigmoidNeuronGradW(double w, double b, double x) {
        double z = w * x + b;
        double s = sigmoid(z);
        return s * (1 - s) * x;
    }
    // endregion

    public static double sigmoid(double z) {
        return 1 / (1 + Math.exp(-z));
    }
}
