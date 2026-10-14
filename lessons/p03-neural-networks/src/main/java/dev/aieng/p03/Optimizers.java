package dev.aieng.p03;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parameter update rules. All of them consume the gradients that backward() left in each tensor. */
public final class Optimizers {

    private Optimizers() {
    }

    public interface Optimizer {
        void step(List<Tensor> params);

        default void zeroGrad(List<Tensor> params) {
            params.forEach(Tensor::zeroGrad);
        }
    }

    // region sgd
    /** SGD with momentum: v = mu * v + g; w -= lr * v. */
    public static Optimizer sgd(double learningRate, double momentum) {
        Map<Tensor, double[]> velocity = new HashMap<>();
        return params -> {
            for (Tensor p : params) {
                double[] v = velocity.computeIfAbsent(p, k -> new double[p.data.length]);
                for (int i = 0; i < p.data.length; i++) {
                    v[i] = momentum * v[i] + p.grad[i];
                    p.data[i] -= learningRate * v[i];
                }
            }
        };
    }
    // endregion

    // region adam
    /**
     * Adam (Kingma & Ba, 2015): keep running averages of the gradient (m, "direction") and of its square
     * (v, "scale"), correct their start-up bias, and step by m / sqrt(v). Each parameter effectively gets its own
     * learning rate. AdamW additionally decays weights directly: w -= lr * weightDecay * w.
     */
    public static Optimizer adamW(double learningRate, double beta1, double beta2, double weightDecay) {
        double eps = 1e-8;
        Map<Tensor, double[][]> state = new HashMap<>();
        int[] t = {0};
        return params -> {
            t[0]++;
            double c1 = 1 - Math.pow(beta1, t[0]);
            double c2 = 1 - Math.pow(beta2, t[0]);
            for (Tensor p : params) {
                double[][] s = state.computeIfAbsent(p, k -> new double[][] {new double[p.data.length], new double[p.data.length]});
                double[] m = s[0];
                double[] v = s[1];
                for (int i = 0; i < p.data.length; i++) {
                    double g = p.grad[i];
                    m[i] = beta1 * m[i] + (1 - beta1) * g;
                    v[i] = beta2 * v[i] + (1 - beta2) * g * g;
                    double mHat = m[i] / c1;
                    double vHat = v[i] / c2;
                    p.data[i] -= learningRate * (mHat / (Math.sqrt(vHat) + eps) + weightDecay * p.data[i]);
                }
            }
        };
    }
    // endregion

    public static Optimizer adam(double learningRate) {
        return adamW(learningRate, 0.9, 0.999, 0);
    }
}
