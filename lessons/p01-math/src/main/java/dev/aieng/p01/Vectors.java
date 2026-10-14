package dev.aieng.p01;

/**
 * Vector operations on plain double[]. Everything in machine learning - embeddings, weights, gradients,
 * attention scores - is built from these five functions.
 */
public final class Vectors {

    private Vectors() {
    }

    // region core
    /** a . b = sum a_i * b_i. Large when vectors point the same way. */
    public static double dot(double[] a, double[] b) {
        requireSameLength(a, b);
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** Euclidean (L2) length: sqrt(a . a). */
    public static double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    /** cos(theta) = a . b / (|a| |b|), in [-1, 1]. Ignores magnitude, keeps direction. */
    public static double cosine(double[] a, double[] b) {
        double denominator = norm(a) * norm(b);
        return denominator == 0 ? 0 : dot(a, b) / denominator;
    }

    /** Straight-line distance |a - b|. */
    public static double euclidean(double[] a, double[] b) {
        requireSameLength(a, b);
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /** a / |a|. After normalising, dot product == cosine similarity - the trick every vector DB uses. */
    public static double[] normalize(double[] a) {
        double n = norm(a);
        return n == 0 ? a.clone() : scale(a, 1 / n);
    }
    // endregion

    public static double[] add(double[] a, double[] b) {
        requireSameLength(a, b);
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    public static double[] subtract(double[] a, double[] b) {
        return add(a, scale(b, -1));
    }

    public static double[] scale(double[] a, double s) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] * s;
        }
        return out;
    }

    private static void requireSameLength(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        }
    }
}
