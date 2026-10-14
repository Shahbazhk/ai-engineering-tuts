package dev.aieng.p02;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Feature matrix x (one row per example) and target vector y. Immutable by convention. */
public record Dataset(double[][] x, double[] y) {

    public record Split(Dataset train, Dataset test) {
    }

    public Dataset {
        if (x.length != y.length) {
            throw new IllegalArgumentException(x.length + " rows but " + y.length + " targets");
        }
    }

    public int size() {
        return y.length;
    }

    public int features() {
        return x.length == 0 ? 0 : x[0].length;
    }

    // region split
    /**
     * Shuffles once with a fixed seed, then cuts. The seed matters: a reproducible split is the only way to
     * compare two models fairly. The test set must not be touched until the very end.
     */
    public Split split(double testFraction, long seed) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            idx.add(i);
        }
        Collections.shuffle(idx, new Random(seed));
        int testSize = (int) Math.round(size() * testFraction);
        return new Split(subset(idx.subList(testSize, size())), subset(idx.subList(0, testSize)));
    }
    // endregion

    public Dataset subset(List<Integer> rows) {
        double[][] sx = new double[rows.size()][];
        double[] sy = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            sx[i] = x[rows.get(i)];
            sy[i] = y[rows.get(i)];
        }
        return new Dataset(sx, sy);
    }

    // region standardize
    /**
     * Feature scaling: (x - mean) / std per column, with mean and std learned from THIS dataset (the training
     * set). Apply the returned scaler to validation and test data - never refit on them, or you leak information.
     */
    public Scaler fitScaler() {
        int f = features();
        double[] mean = new double[f];
        double[] std = new double[f];
        for (double[] row : x) {
            for (int j = 0; j < f; j++) {
                mean[j] += row[j] / size();
            }
        }
        for (double[] row : x) {
            for (int j = 0; j < f; j++) {
                std[j] += (row[j] - mean[j]) * (row[j] - mean[j]) / size();
            }
        }
        for (int j = 0; j < f; j++) {
            std[j] = Math.sqrt(std[j]);
            if (std[j] == 0) {
                std[j] = 1; // constant column: leave it centred, avoid division by zero
            }
        }
        return new Scaler(mean, std);
    }

    public record Scaler(double[] mean, double[] std) {
        public Dataset apply(Dataset d) {
            double[][] out = new double[d.size()][];
            for (int i = 0; i < d.size(); i++) {
                out[i] = new double[mean.length];
                for (int j = 0; j < mean.length; j++) {
                    out[i][j] = (d.x()[i][j] - mean[j]) / std[j];
                }
            }
            return new Dataset(out, d.y());
        }
    }
    // endregion

    /** y = 3x + 2 + noise. */
    public static Dataset linear(int n, double noise, long seed) {
        Random rnd = new Random(seed);
        double[][] x = new double[n][1];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i][0] = rnd.nextDouble() * 10;
            y[i] = 3 * x[i][0] + 2 + rnd.nextGaussian() * noise;
        }
        return new Dataset(x, y);
    }

    /** Two Gaussian blobs in 2D, labels 0 and 1. {@code gap} controls how separable they are. */
    public static Dataset blobs(int n, double gap, long seed) {
        Random rnd = new Random(seed);
        double[][] x = new double[n][2];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            int label = i % 2;
            x[i][0] = rnd.nextGaussian() + label * gap;
            x[i][1] = rnd.nextGaussian() + label * gap;
            y[i] = label;
        }
        return new Dataset(x, y);
    }

    /** Points inside a circle of radius 1 are class 1: not linearly separable. */
    public static Dataset circle(int n, long seed) {
        Random rnd = new Random(seed);
        double[][] x = new double[n][2];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i][0] = rnd.nextDouble() * 4 - 2;
            x[i][1] = rnd.nextDouble() * 4 - 2;
            y[i] = x[i][0] * x[i][0] + x[i][1] * x[i][1] < 1 ? 1 : 0;
        }
        return new Dataset(x, y);
    }
}
