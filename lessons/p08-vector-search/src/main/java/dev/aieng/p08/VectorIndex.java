package dev.aieng.p08;

import java.util.List;

/**
 * Nearest-neighbour index over L2-normalised float vectors, scored by dot product (= cosine similarity).
 * Three implementations with different speed/recall trade-offs: {@link BruteForceIndex} (exact),
 * {@link IvfIndex} (clustering) and {@link Hnsw} (graph).
 */
public interface VectorIndex {

    record Hit(int id, double score) {
    }

    void add(int id, float[] vector);

    List<Hit> search(float[] query, int k);

    int size();

    static float[] normalize(float[] v) {
        double n = 0;
        for (float x : v) {
            n += x * x;
        }
        n = Math.sqrt(n);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = n == 0 ? 0 : (float) (v[i] / n);
        }
        return out;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
