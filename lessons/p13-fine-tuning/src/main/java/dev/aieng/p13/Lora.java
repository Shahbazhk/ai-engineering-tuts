package dev.aieng.p13;

import java.util.Random;

/**
 * LoRA (Hu et al., 2021): freeze the pretrained weight W0 (d_out x d_in) and learn a low-rank update
 * delta = (alpha / r) * B A, with B (d_out x r) and A (r x d_in), r << d. Only A and B are trained, so trainable
 * parameters, gradients and optimiser state shrink by orders of magnitude. B starts at zero, so training starts
 * exactly from the pretrained behaviour.
 */
public final class Lora {

    final double[][] w0;  // frozen
    final double[][] a;   // r x d_in, small random
    final double[][] b;   // d_out x r, zeros
    final double scale;

    public Lora(double[][] frozen, int rank, double alpha, long seed) {
        this.w0 = frozen;
        int dOut = frozen.length;
        int dIn = frozen[0].length;
        Random rnd = new Random(seed);
        this.a = new double[rank][dIn];
        for (double[] row : a) {
            for (int j = 0; j < dIn; j++) {
                row[j] = rnd.nextGaussian() / Math.sqrt(dIn);
            }
        }
        this.b = new double[dOut][rank];
        this.scale = alpha / rank;
    }

    // region forward
    /** y = W0 x + scale * B (A x). Note: B A is never materialised - two thin matrix-vector products instead. */
    public double[] forward(double[] x) {
        double[] y = mv(w0, x);
        double[] ax = mv(a, x);
        double[] bax = mv(b, ax);
        for (int i = 0; i < y.length; i++) {
            y[i] += scale * bax[i];
        }
        return y;
    }
    // endregion

    // region train
    /** One gradient-descent step on mean squared error over a batch; only A and B change. Returns the loss. */
    public double step(double[][] xs, double[][] targets, double lr) {
        int r = a.length;
        double[][] gA = new double[r][a[0].length];
        double[][] gB = new double[b.length][r];
        double loss = 0;
        for (int n = 0; n < xs.length; n++) {
            double[] x = xs[n];
            double[] ax = mv(a, x);
            double[] y = forward(x);
            double[] dy = new double[y.length];
            for (int i = 0; i < y.length; i++) {
                double e = y[i] - targets[n][i];
                loss += e * e / xs.length;
                dy[i] = 2 * e / xs.length;
            }
            // y = W0 x + s B (A x):  dL/dB = s * dy (A x)^T,  dL/dA = s * (B^T dy) x^T
            double[] btdy = new double[r];
            for (int i = 0; i < b.length; i++) {
                for (int k = 0; k < r; k++) {
                    gB[i][k] += scale * dy[i] * ax[k];
                    btdy[k] += b[i][k] * dy[i];
                }
            }
            for (int k = 0; k < r; k++) {
                for (int j = 0; j < x.length; j++) {
                    gA[k][j] += scale * btdy[k] * x[j];
                }
            }
        }
        for (int k = 0; k < r; k++) {
            for (int j = 0; j < a[0].length; j++) {
                a[k][j] -= lr * gA[k][j];
            }
        }
        for (int i = 0; i < b.length; i++) {
            for (int k = 0; k < r; k++) {
                b[i][k] -= lr * gB[i][k];
            }
        }
        return loss;
    }
    // endregion

    // region merge
    /** For serving: fold the adapter into the base weight, W = W0 + scale * B A. Zero extra inference cost. */
    public double[][] merged() {
        double[][] w = new double[w0.length][w0[0].length];
        for (int i = 0; i < w.length; i++) {
            for (int j = 0; j < w[0].length; j++) {
                double delta = 0;
                for (int k = 0; k < a.length; k++) {
                    delta += b[i][k] * a[k][j];
                }
                w[i][j] = w0[i][j] + scale * delta;
            }
        }
        return w;
    }

    public long trainableParameters() {
        return (long) a.length * a[0].length + (long) b.length * b[0].length;
    }

    public long fullParameters() {
        return (long) w0.length * w0[0].length;
    }
    // endregion

    static double[] mv(double[][] m, double[] x) {
        double[] out = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            double s = 0;
            for (int j = 0; j < x.length; j++) {
                s += m[i][j] * x[j];
            }
            out[i] = s;
        }
        return out;
    }

    public static void main(String[] args) {
        int d = 256;
        Random rnd = new Random(1);
        double[][] w0 = new double[d][d];
        double[][] target = new double[d][d];
        double[] u = rnd.doubles(d).map(v -> v - 0.5).toArray();
        double[] v = rnd.doubles(d).map(x -> x - 0.5).toArray();
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                w0[i][j] = rnd.nextGaussian() / Math.sqrt(d);
                target[i][j] = w0[i][j] + 0.5 * u[i] * v[j]; // the "new behaviour" is a rank-1 change
            }
        }
        Lora lora = new Lora(w0, 4, 8, 2);
        double[][] xs = new double[128][];
        double[][] ys = new double[128][];
        for (int n = 0; n < xs.length; n++) {
            xs[n] = rnd.doubles(d).map(x -> x * 2 - 1).toArray();
            ys[n] = mv(target, xs[n]);
        }
        System.out.printf("trainable %,d of %,d parameters (%.2f%%)%n", lora.trainableParameters(), lora.fullParameters(),
                100.0 * lora.trainableParameters() / lora.fullParameters());
        for (int epoch = 0; epoch <= 600; epoch++) {
            double loss = lora.step(xs, ys, 0.002);
            if (epoch % 150 == 0) {
                System.out.printf("epoch %3d loss %.6f%n", epoch, loss);
            }
        }
    }
}
