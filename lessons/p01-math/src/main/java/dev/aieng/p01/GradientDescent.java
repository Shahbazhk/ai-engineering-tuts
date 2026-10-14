package dev.aieng.p01;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Minimises a function by repeatedly stepping against its gradient. Training a neural network with
 * billions of parameters is this loop - with the gradient supplied by backpropagation.
 */
public final class GradientDescent {

    public record Result(double[] x, double value, int steps, List<Double> history) {
    }

    private GradientDescent() {
    }

    // region minimize
    /**
     * x <- x - lr * v, where v is a running average of gradients (momentum). With momentum = 0 this is plain
     * gradient descent. Momentum smooths zig-zagging in narrow valleys and speeds up progress on gentle slopes.
     */
    public static Result minimize(Function<double[], Double> f, Function<double[], double[]> grad, double[] start,
                                  double learningRate, double momentum, int maxSteps, double tolerance) {
        double[] x = start.clone();
        double[] velocity = new double[x.length];
        List<Double> history = new ArrayList<>();
        for (int step = 1; step <= maxSteps; step++) {
            double[] g = grad.apply(x);
            for (int i = 0; i < x.length; i++) {
                velocity[i] = momentum * velocity[i] + g[i];
                x[i] -= learningRate * velocity[i];
            }
            double value = f.apply(x);
            history.add(value);
            if (!Double.isFinite(value)) {
                throw new ArithmeticException("diverged at step " + step + ": learning rate " + learningRate + " too high");
            }
            if (Vectors.norm(g) < tolerance) {
                return new Result(x, value, step, history);
            }
        }
        return new Result(x, f.apply(x), maxSteps, history);
    }
    // endregion

    /** f(x, y) = (1 - x)^2 + 100 (y - x^2)^2: a classic narrow curved valley, minimum at (1, 1). */
    public static double rosenbrock(double[] p) {
        double a = 1 - p[0];
        double b = p[1] - p[0] * p[0];
        return a * a + 100 * b * b;
    }

    public static double[] rosenbrockGradient(double[] p) {
        double x = p[0];
        double y = p[1];
        return new double[] {-2 * (1 - x) - 400 * x * (y - x * x), 200 * (y - x * x)};
    }

    public static void main(String[] args) {
        for (double momentum : new double[] {0.0, 0.9}) {
            Result r = minimize(GradientDescent::rosenbrock, GradientDescent::rosenbrockGradient,
                    new double[] {-1.5, 2}, 0.0005, momentum, 200_000, 1e-6);
            System.out.printf("momentum=%.1f -> x=(%.4f, %.4f) f=%.2e after %d steps%n",
                    momentum, r.x()[0], r.x()[1], r.value(), r.steps());
        }
    }
}
