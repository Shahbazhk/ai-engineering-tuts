package dev.aieng.p02;

/**
 * Binary classifier: p(y=1 | x) = sigmoid(w . x + b), trained with binary cross-entropy and optional L2
 * regularisation. Structurally it is a one-neuron neural network - Phase 3 stacks many of them.
 */
public final class LogisticRegression {

    private double[] w;
    private double b;

    // region fit
    /**
     * The gradient of binary cross-entropy through a sigmoid simplifies beautifully to (p - y) * x - the same
     * "prediction minus target" shape as linear regression. The L2 term adds lambda * w, pulling weights to 0.
     */
    public LogisticRegression fit(Dataset data, double learningRate, int epochs, double l2) {
        int n = data.size();
        int f = data.features();
        w = new double[f];
        b = 0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gw = new double[f];
            double gb = 0;
            for (int i = 0; i < n; i++) {
                double error = probability(data.x()[i]) - data.y()[i];
                for (int j = 0; j < f; j++) {
                    gw[j] += error * data.x()[i][j] / n;
                }
                gb += error / n;
            }
            for (int j = 0; j < f; j++) {
                w[j] -= learningRate * (gw[j] + l2 * w[j]);
            }
            b -= learningRate * gb;
        }
        return this;
    }
    // endregion

    public double probability(double[] x) {
        double z = b;
        for (int j = 0; j < w.length; j++) {
            z += w[j] * x[j];
        }
        return 1 / (1 + Math.exp(-z));
    }

    public int predict(double[] x, double threshold) {
        return probability(x) >= threshold ? 1 : 0;
    }

    /** Mean binary cross-entropy: -[y log p + (1 - y) log(1 - p)], clipped to avoid log(0). */
    public double loss(Dataset data) {
        double sum = 0;
        for (int i = 0; i < data.size(); i++) {
            double p = Math.min(Math.max(probability(data.x()[i]), 1e-12), 1 - 1e-12);
            sum -= data.y()[i] * Math.log(p) + (1 - data.y()[i]) * Math.log(1 - p);
        }
        return sum / data.size();
    }

    public double[] weights() {
        return w.clone();
    }
}
