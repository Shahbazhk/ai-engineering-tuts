package dev.aieng.p01;

import java.util.Arrays;
import java.util.Random;

/**
 * Dense row-major matrix. Row-major means element (r, c) lives at data[r * cols + c], so walking along a row
 * is walking through contiguous memory - the reason the loop order in {@link #multiply} matters.
 */
public record Matrix(int rows, int cols, double[] data) {

    public Matrix {
        if (data.length != rows * cols) {
            throw new IllegalArgumentException("expected " + rows * cols + " values, got " + data.length);
        }
    }

    public static Matrix zeros(int rows, int cols) {
        return new Matrix(rows, cols, new double[rows * cols]);
    }

    public static Matrix identity(int n) {
        Matrix m = zeros(n, n);
        for (int i = 0; i < n; i++) {
            m.set(i, i, 1);
        }
        return m;
    }

    public static Matrix of(double[][] values) {
        int rows = values.length;
        int cols = values[0].length;
        double[] data = new double[rows * cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(values[r], 0, data, r * cols, cols);
        }
        return new Matrix(rows, cols, data);
    }

    public static Matrix random(int rows, int cols, Random rnd) {
        double[] data = new double[rows * cols];
        for (int i = 0; i < data.length; i++) {
            data[i] = rnd.nextGaussian();
        }
        return new Matrix(rows, cols, data);
    }

    public double get(int r, int c) {
        return data[r * cols + c];
    }

    public void set(int r, int c, double v) {
        data[r * cols + c] = v;
    }

    // region multiply
    /**
     * C = A x B, where A is (n x k), B is (k x m) and C is (n x m). C[i][j] = sum_p A[i][p] * B[p][j].
     * Loop order i-p-j (not the textbook i-j-p) reads B row by row, which is contiguous in memory.
     * On large matrices that alone is several times faster, because the CPU cache does the work.
     */
    public Matrix multiply(Matrix b) {
        if (cols != b.rows) {
            throw new IllegalArgumentException("shape mismatch: (" + rows + "x" + cols + ") x (" + b.rows + "x" + b.cols + ")");
        }
        double[] out = new double[rows * b.cols];
        for (int i = 0; i < rows; i++) {
            for (int p = 0; p < cols; p++) {
                double a = data[i * cols + p];
                int bRow = p * b.cols;
                int outRow = i * b.cols;
                for (int j = 0; j < b.cols; j++) {
                    out[outRow + j] += a * b.data[bRow + j];
                }
            }
        }
        return new Matrix(rows, b.cols, out);
    }
    // endregion

    /** Matrix-vector product: applies the linear map to x. A neural network layer is exactly this, plus a bias. */
    public double[] apply(double[] x) {
        if (x.length != cols) {
            throw new IllegalArgumentException("vector length " + x.length + " != cols " + cols);
        }
        double[] out = new double[rows];
        for (int r = 0; r < rows; r++) {
            double sum = 0;
            for (int c = 0; c < cols; c++) {
                sum += data[r * cols + c] * x[c];
            }
            out[r] = sum;
        }
        return out;
    }

    public Matrix transpose() {
        Matrix t = zeros(cols, rows);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                t.set(c, r, get(r, c));
            }
        }
        return t;
    }

    /** Textbook i-j-p order, kept only so the benchmark in the lesson can compare. */
    Matrix multiplyNaive(Matrix b) {
        Matrix out = zeros(rows, b.cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < b.cols; j++) {
                double sum = 0;
                for (int p = 0; p < cols; p++) {
                    sum += get(i, p) * b.get(p, j);
                }
                out.set(i, j, sum);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Matrix m && rows == m.rows && cols == m.cols && Arrays.equals(data, m.data);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * rows + cols) + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            sb.append(Arrays.toString(Arrays.copyOfRange(data, r * cols, (r + 1) * cols))).append('\n');
        }
        return sb.toString();
    }
}
