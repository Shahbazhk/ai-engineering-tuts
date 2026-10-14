package dev.aieng.p08;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * Hierarchical Navigable Small World graph (Malkov & Yashunin, 2016) - the index inside Lucene/Elasticsearch/
 * OpenSearch, pgvector (hnsw), Qdrant, Weaviate and Redis. Every vector is a node linked to ~M similar nodes.
 * Upper layers are sparse "express lanes"; search descends greedily from the top, then does a best-first
 * search with a candidate list of size ef on the bottom layer.
 */
public final class Hnsw implements VectorIndex {

    private final int m;            // links per node on upper layers (layer 0 allows 2m)
    private final int efConstruction;
    private int efSearch;
    private final double levelFactor;
    private final Random rnd;

    private final List<Integer> ids = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();
    private final List<List<List<Integer>>> links = new ArrayList<>(); // node -> layer -> neighbour nodes
    private int entryPoint = -1;
    private int topLayer = -1;

    public Hnsw(int m, int efConstruction, int efSearch, long seed) {
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.levelFactor = 1 / Math.log(m);
        this.rnd = new Random(seed);
    }

    public void setEfSearch(int efSearch) {
        this.efSearch = efSearch;
    }

    // region insert
    @Override
    public void add(int id, float[] vector) {
        int node = vectors.size();
        float[] v = VectorIndex.normalize(vector);
        ids.add(id);
        vectors.add(v);
        // Exponentially decaying level distribution: most nodes live only on layer 0.
        int level = (int) Math.floor(-Math.log(1 - rnd.nextDouble()) * levelFactor);
        List<List<Integer>> nodeLinks = new ArrayList<>();
        for (int l = 0; l <= level; l++) {
            nodeLinks.add(new ArrayList<>());
        }
        links.add(nodeLinks);
        if (entryPoint < 0) {
            entryPoint = node;
            topLayer = level;
            return;
        }
        int ep = entryPoint;
        for (int l = topLayer; l > level; l--) {
            ep = greedyClosest(v, ep, l); // descend through layers above the new node's level
        }
        for (int l = Math.min(level, topLayer); l >= 0; l--) {
            List<Integer> candidates = searchLayer(v, ep, efConstruction, l);
            int maxLinks = l == 0 ? 2 * m : m;
            for (int nb : selectNeighbours(v, candidates, m)) {
                nodeLinks.get(l).add(nb);
                List<Integer> back = links.get(nb).get(l);
                back.add(node);
                if (back.size() > maxLinks) {
                    List<Integer> keep = selectNeighbours(vectors.get(nb), sortedBySimilarity(vectors.get(nb), back), maxLinks);
                    back.clear();
                    back.addAll(keep);
                }
            }
            ep = candidates.getFirst();
        }
        if (level > topLayer) {
            entryPoint = node;
            topLayer = level;
        }
    }
    /**
     * The paper's neighbour-selection heuristic: walk candidates from most to least similar and keep one only if
     * it is closer to the new node than to every neighbour already kept. This favours links in DIFFERENT
     * directions, which keeps clusters connected to each other; plain "closest M" builds cliques inside
     * clusters and recall collapses on clustered data. Remaining slots are filled with the closest leftovers.
     */
    private List<Integer> selectNeighbours(float[] base, List<Integer> candidatesBestFirst, int max) {
        List<Integer> selected = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        for (int c : candidatesBestFirst) {
            if (selected.size() >= max) {
                break;
            }
            double toBase = VectorIndex.dot(base, vectors.get(c));
            boolean diverse = true;
            for (int s : selected) {
                if (VectorIndex.dot(vectors.get(c), vectors.get(s)) > toBase) {
                    diverse = false;
                    break;
                }
            }
            (diverse ? selected : skipped).add(c);
        }
        for (int c : skipped) {
            if (selected.size() >= max) {
                break;
            }
            selected.add(c);
        }
        return selected;
    }

    private List<Integer> sortedBySimilarity(float[] base, List<Integer> nodes) {
        List<Integer> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingDouble((Integer x) -> -VectorIndex.dot(base, vectors.get(x))));
        return sorted;
    }
    // endregion

    // region search
    @Override
    public List<Hit> search(float[] query, int k) {
        if (entryPoint < 0) {
            return List.of();
        }
        float[] q = VectorIndex.normalize(query);
        int ep = entryPoint;
        for (int l = topLayer; l > 0; l--) {
            ep = greedyClosest(q, ep, l);
        }
        List<Hit> out = new ArrayList<>();
        for (int node : searchLayer(q, ep, Math.max(efSearch, k), 0)) {
            if (out.size() == k) {
                break;
            }
            out.add(new Hit(ids.get(node), VectorIndex.dot(q, vectors.get(node))));
        }
        return out;
    }

    /** Best-first search on one layer; returns up to ef nodes ordered from most to least similar. */
    private List<Integer> searchLayer(float[] q, int entry, int ef, int layer) {
        Set<Integer> visited = new HashSet<>();
        visited.add(entry);
        // candidates: most similar first; results: least similar first (so the worst can be evicted)
        PriorityQueue<double[]> candidates = new PriorityQueue<>((a, b) -> Double.compare(b[1], a[1]));
        PriorityQueue<double[]> results = new PriorityQueue<>((a, b) -> Double.compare(a[1], b[1]));
        double s0 = VectorIndex.dot(q, vectors.get(entry));
        candidates.add(new double[] {entry, s0});
        results.add(new double[] {entry, s0});
        while (!candidates.isEmpty()) {
            double[] c = candidates.poll();
            if (results.size() >= ef && c[1] < results.peek()[1]) {
                break; // the best remaining candidate is worse than our worst result: done
            }
            List<List<Integer>> nodeLinks = links.get((int) c[0]);
            if (layer >= nodeLinks.size()) {
                continue;
            }
            for (int nb : nodeLinks.get(layer)) {
                if (visited.add(nb)) {
                    double s = VectorIndex.dot(q, vectors.get(nb));
                    if (results.size() < ef || s > results.peek()[1]) {
                        candidates.add(new double[] {nb, s});
                        results.add(new double[] {nb, s});
                        if (results.size() > ef) {
                            results.poll();
                        }
                    }
                }
            }
        }
        List<double[]> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Double.compare(b[1], a[1]));
        return sorted.stream().map(x -> (int) x[0]).toList();
    }
    // endregion

    private int greedyClosest(float[] q, int start, int layer) {
        int best = start;
        double bestScore = VectorIndex.dot(q, vectors.get(best));
        boolean improved = true;
        while (improved) {
            improved = false;
            List<List<Integer>> nodeLinks = links.get(best);
            if (layer >= nodeLinks.size()) {
                break;
            }
            for (int nb : nodeLinks.get(layer)) {
                double s = VectorIndex.dot(q, vectors.get(nb));
                if (s > bestScore) {
                    bestScore = s;
                    best = nb;
                    improved = true;
                }
            }
        }
        return best;
    }

    @Override
    public int size() {
        return vectors.size();
    }

    public int layers() {
        return topLayer + 1;
    }
}
