package dev.aieng.p08;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Inverted-file index (IVF): cluster the vectors with k-means into {@code nlist} cells; at query time only scan
 * the {@code nprobe} cells whose centroids are closest to the query. nprobe = nlist is exact; small nprobe is fast
 * but can miss neighbours that sit just across a cell boundary. Used by FAISS, Milvus and pgvector's ivfflat.
 */
public final class IvfIndex implements VectorIndex {

    private final int nlist;
    private final int nprobe;
    private final long seed;
    private final List<Integer> pendingIds = new ArrayList<>();
    private final List<float[]> pendingVectors = new ArrayList<>();
    private float[][] centroids;
    private List<List<Integer>> cellIds;
    private List<List<float[]>> cellVectors;

    public IvfIndex(int nlist, int nprobe, long seed) {
        this.nlist = nlist;
        this.nprobe = nprobe;
        this.seed = seed;
    }

    @Override
    public void add(int id, float[] vector) {
        pendingIds.add(id);
        pendingVectors.add(VectorIndex.normalize(vector));
        centroids = null; // ponytail: rebuilds on next search; real systems train once and assign incrementally
    }

    // region build
    /** Lloyd's k-means on the unit sphere ("spherical k-means"): assign to the most similar centroid, re-average. */
    private void build() {
        Random rnd = new Random(seed);
        int dim = pendingVectors.getFirst().length;
        centroids = new float[nlist][];
        for (int c = 0; c < nlist; c++) {
            centroids[c] = pendingVectors.get(rnd.nextInt(pendingVectors.size())).clone();
        }
        int[] assignment = new int[pendingVectors.size()];
        for (int iter = 0; iter < 15; iter++) {
            for (int i = 0; i < pendingVectors.size(); i++) {
                assignment[i] = nearestCentroids(pendingVectors.get(i), 1)[0];
            }
            float[][] sums = new float[nlist][dim];
            int[] counts = new int[nlist];
            for (int i = 0; i < pendingVectors.size(); i++) {
                counts[assignment[i]]++;
                for (int d = 0; d < dim; d++) {
                    sums[assignment[i]][d] += pendingVectors.get(i)[d];
                }
            }
            for (int c = 0; c < nlist; c++) {
                if (counts[c] > 0) {
                    centroids[c] = VectorIndex.normalize(sums[c]); // empty cells keep their old centroid
                }
            }
        }
        cellIds = new ArrayList<>();
        cellVectors = new ArrayList<>();
        for (int c = 0; c < nlist; c++) {
            cellIds.add(new ArrayList<>());
            cellVectors.add(new ArrayList<>());
        }
        for (int i = 0; i < pendingVectors.size(); i++) {
            cellIds.get(assignment[i]).add(pendingIds.get(i));
            cellVectors.get(assignment[i]).add(pendingVectors.get(i));
        }
    }
    // endregion

    // region search
    @Override
    public List<Hit> search(float[] query, int k) {
        if (centroids == null) {
            build();
        }
        float[] q = VectorIndex.normalize(query);
        List<Hit> candidates = new ArrayList<>();
        for (int cell : nearestCentroids(q, nprobe)) {
            List<float[]> vs = cellVectors.get(cell);
            for (int i = 0; i < vs.size(); i++) {
                candidates.add(new Hit(cellIds.get(cell).get(i), VectorIndex.dot(q, vs.get(i))));
            }
        }
        candidates.sort(Comparator.comparingDouble(Hit::score).reversed());
        return candidates.subList(0, Math.min(k, candidates.size()));
    }
    // endregion

    private int[] nearestCentroids(float[] v, int n) {
        return IntStream.range(0, centroids.length).boxed()
                .sorted(Comparator.comparingDouble((Integer c) -> VectorIndex.dot(v, centroids[c])).reversed())
                .limit(n).mapToInt(Integer::intValue).toArray();
    }

    /** Number of vectors actually scored for the last-built index and a given query: the work saved vs brute force. */
    public int scannedFor(float[] query) {
        if (centroids == null) {
            build();
        }
        int total = 0;
        for (int cell : nearestCentroids(VectorIndex.normalize(query), nprobe)) {
            total += cellIds.get(cell).size();
        }
        return total;
    }

    @Override
    public int size() {
        return pendingIds.size();
    }
}
