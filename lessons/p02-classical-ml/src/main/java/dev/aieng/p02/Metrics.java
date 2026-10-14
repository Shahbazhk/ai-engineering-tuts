package dev.aieng.p02;

/** Evaluation metrics. Choosing the right one is a product decision, not a technical detail. */
public final class Metrics {

    private Metrics() {
    }

    // region confusion
    public record Confusion(int tp, int fp, int tn, int fn) {

        public static Confusion of(double[] actual, int[] predicted) {
            int tp = 0, fp = 0, tn = 0, fn = 0;
            for (int i = 0; i < actual.length; i++) {
                boolean positive = actual[i] == 1;
                boolean predictedPositive = predicted[i] == 1;
                if (positive && predictedPositive) {
                    tp++;
                } else if (!positive && predictedPositive) {
                    fp++;
                } else if (!positive) {
                    tn++;
                } else {
                    fn++;
                }
            }
            return new Confusion(tp, fp, tn, fn);
        }

        public double accuracy() {
            return (double) (tp + tn) / (tp + fp + tn + fn);
        }

        /** Of everything flagged positive, how much was right? Cost of false alarms. */
        public double precision() {
            return tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        }

        /** Of all real positives, how many did we find? Cost of misses. */
        public double recall() {
            return tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        }

        /** Harmonic mean of precision and recall: high only when both are high. */
        public double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }
    }
    // endregion

    // region auc
    /**
     * ROC AUC = probability that a random positive is scored higher than a random negative (ties count half).
     * Threshold-free, so it measures ranking quality. O(P x N) here for clarity; sort-based versions are O(n log n).
     */
    public static double rocAuc(double[] actual, double[] scores) {
        double wins = 0;
        long pairs = 0;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != 1) {
                continue;
            }
            for (int j = 0; j < actual.length; j++) {
                if (actual[j] != 0) {
                    continue;
                }
                pairs++;
                if (scores[i] > scores[j]) {
                    wins += 1;
                } else if (scores[i] == scores[j]) {
                    wins += 0.5;
                }
            }
        }
        return pairs == 0 ? Double.NaN : wins / pairs;
    }
    // endregion

    public static double mse(double[] actual, double[] predicted) {
        double s = 0;
        for (int i = 0; i < actual.length; i++) {
            s += (actual[i] - predicted[i]) * (actual[i] - predicted[i]);
        }
        return s / actual.length;
    }

    /** R^2 = 1 - SS_res / SS_tot: fraction of variance explained. 1 is perfect, 0 is "predict the mean". */
    public static double r2(double[] actual, double[] predicted) {
        double mean = 0;
        for (double a : actual) {
            mean += a / actual.length;
        }
        double ssTot = 0;
        double ssRes = 0;
        for (int i = 0; i < actual.length; i++) {
            ssTot += (actual[i] - mean) * (actual[i] - mean);
            ssRes += (actual[i] - predicted[i]) * (actual[i] - predicted[i]);
        }
        return 1 - ssRes / ssTot;
    }
}
