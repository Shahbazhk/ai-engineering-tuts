package dev.aieng.p08;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Exact k-nearest-neighbour search: score every vector, keep the k best in a min-heap. O(n * d) per query.
 * Slow at scale, but always 100% recall - so it is the ground truth every approximate index is measured against.
 */
public final class BruteForceIndex implements VectorIndex {

    private final List<Integer> ids = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();

    @Override
    public void add(int id, float[] vector) {
        ids.add(id);
        vectors.add(VectorIndex.normalize(vector));
    }

    // region search
    @Override
    public List<Hit> search(float[] query, int k) {
        float[] q = VectorIndex.normalize(query);
        // Min-heap of size k: the root is the worst of the current top k, so each candidate costs O(log k).
        PriorityQueue<Hit> top = new PriorityQueue<>((a, b) -> Double.compare(a.score(), b.score()));
        for (int i = 0; i < vectors.size(); i++) {
            double s = VectorIndex.dot(q, vectors.get(i));
            if (top.size() < k) {
                top.add(new Hit(ids.get(i), s));
            } else if (s > top.peek().score()) {
                top.poll();
                top.add(new Hit(ids.get(i), s));
            }
        }
        List<Hit> out = new ArrayList<>(top);
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }
    // endregion

    @Override
    public int size() {
        return vectors.size();
    }
}
