package dev.aieng.p08;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Recall@k and latency of the three indexes on clustered synthetic data (real embeddings are clustered too). */
public final class Benchmark {

    private Benchmark() {
    }

    /**
     * Synthetic "embedding-like" data: clusters in a low-dimensional latent space (real embeddings have low
     * intrinsic dimension), projected into {@code dim} dimensions with a fixed random matrix plus a little noise.
     * Isotropic high-dimensional noise would be a near worst case for every ANN index.
     */
    public static float[][] clusteredData(int n, int dim, int clusters, long seed) {
        int latent = Math.min(dim, 32);
        Random fixed = new Random(42); // same projection for data and queries
        double[][] projection = new double[dim][latent];
        for (double[] row : projection) {
            for (int j = 0; j < latent; j++) {
                row[j] = fixed.nextGaussian();
            }
        }
        double[][] centres = new double[clusters][latent];
        for (double[] c : centres) {
            for (int j = 0; j < latent; j++) {
                c[j] = fixed.nextGaussian() * 2;
            }
        }
        Random rnd = new Random(seed);
        float[][] data = new float[n][dim];
        for (int i = 0; i < n; i++) {
            double[] c = centres[rnd.nextInt(clusters)];
            double[] z = new double[latent];
            for (int j = 0; j < latent; j++) {
                z[j] = c[j] + rnd.nextGaussian() * 0.7;
            }
            for (int d = 0; d < dim; d++) {
                double v = 0;
                for (int j = 0; j < latent; j++) {
                    v += projection[d][j] * z[j];
                }
                data[i][d] = (float) (v + rnd.nextGaussian() * 0.05);
            }
        }
        return data;
    }

    // region recall
    /** Fraction of the true top-k (from brute force) that the approximate index also returned, averaged. */
    public static double recallAtK(VectorIndex exact, VectorIndex approx, float[][] queries, int k) {
        double total = 0;
        for (float[] q : queries) {
            Set<Integer> truth = new HashSet<>();
            exact.search(q, k).forEach(h -> truth.add(h.id()));
            long found = approx.search(q, k).stream().filter(h -> truth.contains(h.id())).count();
            total += (double) found / k;
        }
        return total / queries.length;
    }
    // endregion

    public static void main(String[] args) {
        int n = 20_000, dim = 64, k = 10;
        float[][] data = clusteredData(n, dim, 50, 1);
        float[][] queries = clusteredData(200, dim, 50, 2);
        BruteForceIndex exact = new BruteForceIndex();
        IvfIndex ivf = new IvfIndex(100, 8, 3);
        Hnsw hnsw = new Hnsw(16, 100, 64, 4);
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            exact.add(i, data[i]);
            ivf.add(i, data[i]);
        }
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            hnsw.add(i, data[i]);
        }
        long t2 = System.nanoTime();
        ivf.search(queries[0], k); // trigger k-means build
        System.out.printf("HNSW build %.1fs (%d layers); brute/IVF insert %.2fs%n", (t2 - t1) / 1e9, hnsw.layers(), (t1 - t0) / 1e9);
        for (VectorIndex idx : List.<VectorIndex>of(exact, ivf, hnsw)) {
            long s = System.nanoTime();
            for (float[] q : queries) {
                idx.search(q, k);
            }
            double ms = (System.nanoTime() - s) / 1e6 / queries.length;
            System.out.printf("%-16s recall@%d %.3f   %.3f ms/query%n", idx.getClass().getSimpleName(), k,
                    recallAtK(exact, idx, queries, k), ms);
        }
        for (int ef : new int[] {10, 32, 128}) {
            hnsw.setEfSearch(ef);
            System.out.printf("HNSW efSearch=%-4d recall@%d %.3f%n", ef, k, recallAtK(exact, hnsw, queries, k));
        }
    }
}
