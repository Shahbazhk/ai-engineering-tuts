package dev.aieng.p02;

/**
 * y_hat = w . x + b, fitted by minimising mean squared error with batch gradient descent.
 * The same three ingredients - model, loss, optimiser - define every model in this course.
 */
public final class LinearRegression {

    private double[] w;
    private double b;

    // region fit
    public LinearRegression fit(Dataset data, double learningRate, int epochs) {
        int n = data.size();
        int f = data.features();
        w = new double[f];
        b = 0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gw = new double[f];
            double gb = 0;
            for (int i = 0; i < n; i++) {
                double error = predict(data.x()[i]) - data.y()[i];   // y_hat - y
                for (int j = 0; j < f; j++) {
                    gw[j] += 2 * error * data.x()[i][j] / n;         // dMSE/dw_j
                }
                gb += 2 * error / n;                                 // dMSE/db
            }
            for (int j = 0; j < f; j++) {
                w[j] -= learningRate * gw[j];
            }
            b -= learningRate * gb;
        }
        return this;
    }
    // endregion

    /**
     * Closed-form least squares for one feature: w = cov(x, y) / var(x), b = mean(y) - w * mean(x).
     * Exact and instant - but there is no closed form for neural networks, which is why we learn the GD version.
     */
    public static double[] closedForm1d(Dataset data) {
        double mx = 0;
        double my = 0;
        int n = data.size();
        for (int i = 0; i < n; i++) {
            mx += data.x()[i][0] / n;
            my += data.y()[i] / n;
        }
        double cov = 0;
        double var = 0;
        for (int i = 0; i < n; i++) {
            cov += (data.x()[i][0] - mx) * (data.y()[i] - my);
            var += (data.x()[i][0] - mx) * (data.x()[i][0] - mx);
        }
        double w = cov / var;
        return new double[] {w, my - w * mx};
    }

    public double predict(double[] x) {
        double s = b;
        for (int j = 0; j < w.length; j++) {
            s += w[j] * x[j];
        }
        return s;
    }

    public double[] weights() {
        return w.clone();
    }

    public double bias() {
        return b;
    }
}
