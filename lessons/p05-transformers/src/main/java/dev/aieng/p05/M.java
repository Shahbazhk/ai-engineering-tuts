package dev.aieng.p05;

import java.util.Random;

/** Minimal dense-matrix helpers on double[rows][cols], kept tiny so the transformer code reads like the maths. */
final class M {

    private M() {
    }

    static double[][] matmul(double[][] a, double[][] b) {
        int n = a.length, k = b.length, m = b[0].length;
        if (a[0].length != k) {
            throw new IllegalArgumentException("shape mismatch " + n + "x" + a[0].length + " @ " + k + "x" + m);
        }
        double[][] out = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int p = 0; p < k; p++) {
                double v = a[i][p];
                for (int j = 0; j < m; j++) {
                    out[i][j] += v * b[p][j];
                }
            }
        }
        return out;
    }

    static double[][] transpose(double[][] a) {
        double[][] t = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                t[j][i] = a[i][j];
            }
        }
        return t;
    }

    static double[][] add(double[][] a, double[][] b) {
        double[][] out = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                out[i][j] = a[i][j] + b[i][j];
            }
        }
        return out;
    }

    static double[] softmax(double[] z) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : z) {
            max = Math.max(max, v);
        }
        double[] out = new double[z.length];
        double sum = 0;
        for (int i = 0; i < z.length; i++) {
            out[i] = max == Double.NEGATIVE_INFINITY ? 0 : Math.exp(z[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < z.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    static double[][] random(int rows, int cols, double std, Random rnd) {
        double[][] w = new double[rows][cols];
        for (double[] row : w) {
            for (int j = 0; j < cols; j++) {
                row[j] = rnd.nextGaussian() * std;
            }
        }
        return w;
    }

    static double[][] slice(double[][] a, int fromCol, int toCol) {
        double[][] out = new double[a.length][toCol - fromCol];
        for (int i = 0; i < a.length; i++) {
            System.arraycopy(a[i], fromCol, out[i], 0, toCol - fromCol);
        }
        return out;
    }

    static double[][] row(double[] r) {
        return new double[][] {r};
    }
}
