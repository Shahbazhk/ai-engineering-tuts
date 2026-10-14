package dev.aieng.p02;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Overfitting experiments: polynomial features, ridge regression and k-fold cross-validation. */
public final class Regularization {

    private Regularization() {
    }

    /** Expands one feature x into [x, x^2, ..., x^degree] - more capacity, more room to overfit. */
    public static Dataset polynomial(Dataset d, int degree) {
        double[][] out = new double[d.size()][degree];
        for (int i = 0; i < d.size(); i++) {
            double v = d.x()[i][0];
            double p = 1;
            for (int k = 0; k < degree; k++) {
                p *= v;
                out[i][k] = p;
            }
        }
        return new Dataset(out, d.y());
    }

    // region ridge
    /**
     * Ridge regression by gradient descent: minimise MSE + lambda * |w|^2. The penalty keeps weights small,
     * which makes the fitted curve smoother - trading a little bias for a large reduction in variance.
     */
    public static double[] ridge(Dataset d, double lambda, double learningRate, int epochs) {
        int f = d.features();
        double[] w = new double[f + 1]; // last entry is the bias, which is not penalised
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] g = new double[f + 1];
            for (int i = 0; i < d.size(); i++) {
                double error = predict(w, d.x()[i]) - d.y()[i];
                for (int j = 0; j < f; j++) {
                    g[j] += 2 * error * d.x()[i][j] / d.size();
                }
                g[f] += 2 * error / d.size();
            }
            for (int j = 0; j < f; j++) {
                w[j] -= learningRate * (g[j] + 2 * lambda * w[j]);
            }
            w[f] -= learningRate * g[f];
        }
        return w;
    }
    // endregion

    public static double predict(double[] w, double[] x) {
        double s = w[w.length - 1];
        for (int j = 0; j < x.length; j++) {
            s += w[j] * x[j];
        }
        return s;
    }

    public static double mse(double[] w, Dataset d) {
        double s = 0;
        for (int i = 0; i < d.size(); i++) {
            double e = predict(w, d.x()[i]) - d.y()[i];
            s += e * e;
        }
        return s / d.size();
    }

    /** Prints train vs test error as capacity (degree) and regularisation (lambda) change. */
    public static void main(String[] args) {
        Dataset all = noisySine(40, 0.25, 11);
        Dataset.Split split = all.split(0.5, 3);
        System.out.println("degree  lambda   train MSE   test MSE");
        for (int degree : new int[] {1, 3, 9}) {
            for (double lambda : new double[] {0, 0.01, 0.1}) {
                Dataset train = polynomial(split.train(), degree);
                Dataset.Scaler scaler = train.fitScaler();
                double[] w = ridge(scaler.apply(train), lambda, 0.05, 20_000);
                System.out.printf("%6d  %6.2f  %10.4f  %9.4f%n", degree, lambda,
                        mse(w, scaler.apply(train)), mse(w, scaler.apply(polynomial(split.test(), degree))));
            }
        }
    }

    /** y = sin(2 pi x) + noise on x in [0, 1]: a curve a straight line cannot fit, but a wiggly one overfits. */
    public static Dataset noisySine(int n, double noise, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        double[][] x = new double[n][1];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i][0] = rnd.nextDouble();
            y[i] = Math.sin(2 * Math.PI * x[i][0]) + rnd.nextGaussian() * noise;
        }
        return new Dataset(x, y);
    }

    // region kfold
    /**
     * k-fold cross-validation: train on k-1 folds, score on the held-out fold, average. Every example is used
     * for validation exactly once, so the estimate is far less noisy than a single split.
     */
    public static double kFold(Dataset d, int k, BiFunction<Dataset, Dataset, Double> trainAndScore) {
        List<Double> scores = new ArrayList<>();
        int foldSize = d.size() / k;
        for (int fold = 0; fold < k; fold++) {
            List<Integer> train = new ArrayList<>();
            List<Integer> validation = new ArrayList<>();
            for (int i = 0; i < d.size(); i++) {
                boolean inFold = i >= fold * foldSize && (fold == k - 1 ? i < d.size() : i < (fold + 1) * foldSize);
                (inFold ? validation : train).add(i);
            }
            scores.add(trainAndScore.apply(d.subset(train), d.subset(validation)));
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
    // endregion
}
