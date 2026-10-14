package dev.aieng.p03;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A 2-D matrix with reverse-mode autodiff: the {@link Value} idea, vectorised. One node now represents a whole
 * matrix operation, so the graph has a handful of nodes instead of millions and each backward step is a matrix
 * operation - which is exactly why frameworks are fast.
 */
public final class Tensor {

    public final int rows;
    public final int cols;
    public final double[] data;
    public final double[] grad;
    private final List<Tensor> parents;
    private Runnable backward = () -> { };

    private Tensor(int rows, int cols, double[] data, List<Tensor> parents) {
        if (data.length != rows * cols) {
            throw new IllegalArgumentException("shape " + rows + "x" + cols + " needs " + rows * cols + " values");
        }
        this.rows = rows;
        this.cols = cols;
        this.data = data;
        this.grad = new double[data.length];
        this.parents = parents;
    }

    public static Tensor of(int rows, int cols, double... data) {
        return new Tensor(rows, cols, data, List.of());
    }

    public static Tensor zeros(int rows, int cols) {
        return of(rows, cols, new double[rows * cols]);
    }

    /** Gaussian init with the given standard deviation (see {@link Init} for how to choose it). */
    public static Tensor randn(int rows, int cols, double std, Random rnd) {
        double[] d = new double[rows * cols];
        for (int i = 0; i < d.length; i++) {
            d[i] = rnd.nextGaussian() * std;
        }
        return of(rows, cols, d);
    }

    public double get(int r, int c) {
        return data[r * cols + c];
    }

    // region matmul
    /**
     * (n x k) @ (k x m). Backward uses the two identities every framework implements:
     * dL/dA = dL/dC @ B^T and dL/dB = A^T @ dL/dC.
     */
    public Tensor matmul(Tensor b) {
        if (cols != b.rows) {
            throw new IllegalArgumentException("matmul shape mismatch " + rows + "x" + cols + " @ " + b.rows + "x" + b.cols);
        }
        int n = rows, k = cols, m = b.cols;
        double[] out = new double[n * m];
        for (int i = 0; i < n; i++) {
            for (int p = 0; p < k; p++) {
                double a = data[i * k + p];
                for (int j = 0; j < m; j++) {
                    out[i * m + j] += a * b.data[p * m + j];
                }
            }
        }
        Tensor c = new Tensor(n, m, out, List.of(this, b));
        c.backward = () -> {
            for (int i = 0; i < n; i++) {
                for (int p = 0; p < k; p++) {
                    double sum = 0;
                    for (int j = 0; j < m; j++) {
                        double g = c.grad[i * m + j];
                        sum += g * b.data[p * m + j];          // dA = dC @ B^T
                        b.grad[p * m + j] += data[i * k + p] * g; // dB = A^T @ dC
                    }
                    grad[i * k + p] += sum;
                }
            }
        };
        return c;
    }
    // endregion

    /** Adds a 1 x cols bias row to every row ("broadcasting"). Its gradient is the column-sum of the output grad. */
    public Tensor addRow(Tensor bias) {
        if (bias.rows != 1 || bias.cols != cols) {
            throw new IllegalArgumentException("bias must be 1x" + cols);
        }
        double[] out = data.clone();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r * cols + c] += bias.data[c];
            }
        }
        Tensor t = new Tensor(rows, cols, out, List.of(this, bias));
        t.backward = () -> {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    double g = t.grad[r * cols + c];
                    grad[r * cols + c] += g;
                    bias.grad[c] += g;
                }
            }
        };
        return t;
    }

    public Tensor relu() {
        double[] out = new double[data.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0, data[i]);
        }
        Tensor t = new Tensor(rows, cols, out, List.of(this));
        t.backward = () -> {
            for (int i = 0; i < out.length; i++) {
                grad[i] += out[i] > 0 ? t.grad[i] : 0;
            }
        };
        return t;
    }

    public Tensor tanh() {
        double[] out = new double[data.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.tanh(data[i]);
        }
        Tensor t = new Tensor(rows, cols, out, List.of(this));
        t.backward = () -> {
            for (int i = 0; i < out.length; i++) {
                grad[i] += (1 - out[i] * out[i]) * t.grad[i];
            }
        };
        return t;
    }

    // region loss
    /**
     * Mean softmax cross-entropy over rows, fused into one node. Fusing gives the famous simple gradient
     * dL/dlogits = (softmax(logits) - onehot(target)) / batch, and avoids computing log(softmax) unstably.
     */
    public Tensor softmaxCrossEntropy(int[] targets) {
        double[] probs = new double[data.length];
        double loss = 0;
        for (int r = 0; r < rows; r++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < cols; c++) {
                max = Math.max(max, data[r * cols + c]);
            }
            double sum = 0;
            for (int c = 0; c < cols; c++) {
                probs[r * cols + c] = Math.exp(data[r * cols + c] - max);
                sum += probs[r * cols + c];
            }
            for (int c = 0; c < cols; c++) {
                probs[r * cols + c] /= sum;
            }
            loss -= Math.log(probs[r * cols + targets[r]]);
        }
        Tensor t = new Tensor(1, 1, new double[] {loss / rows}, List.of(this));
        t.backward = () -> {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    double y = c == targets[r] ? 1 : 0;
                    grad[r * cols + c] += (probs[r * cols + c] - y) / rows * t.grad[0];
                }
            }
        };
        return t;
    }
    // endregion

    /** Sum of all elements (handy for gradient checks: d(sum)/dx = 1 for every x). */
    public Tensor sum() {
        double s = 0;
        for (double v : data) {
            s += v;
        }
        Tensor t = new Tensor(1, 1, new double[] {s}, List.of(this));
        t.backward = () -> {
            for (int i = 0; i < grad.length; i++) {
                grad[i] += t.grad[0];
            }
        };
        return t;
    }

    public void backward() {
        List<Tensor> order = new ArrayList<>();
        topo(this, new HashSet<>(), order);
        java.util.Arrays.fill(grad, 1);
        for (int i = order.size() - 1; i >= 0; i--) {
            order.get(i).backward.run();
        }
    }

    public void zeroGrad() {
        java.util.Arrays.fill(grad, 0);
    }

    private static void topo(Tensor t, Set<Tensor> seen, List<Tensor> order) {
        if (seen.add(t)) {
            for (Tensor p : t.parents) {
                topo(p, seen, order); // graphs here are a few dozen nodes deep, recursion is fine
            }
            order.add(t);
        }
    }

    /** Index of the largest value in each row: the predicted class. */
    public int[] argmaxRows() {
        int[] out = new int[rows];
        for (int r = 0; r < rows; r++) {
            int best = 0;
            for (int c = 1; c < cols; c++) {
                if (get(r, c) > get(r, best)) {
                    best = c;
                }
            }
            out[r] = best;
        }
        return out;
    }
}
