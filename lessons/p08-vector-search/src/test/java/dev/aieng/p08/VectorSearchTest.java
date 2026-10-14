package dev.aieng.p08;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;

class VectorSearchTest {

    static float[][] data;
    static float[][] queries;
    static BruteForceIndex exact;

    @BeforeAll
    static void setUp() {
        data = Benchmark.clusteredData(3000, 32, 30, 1);
        queries = Benchmark.clusteredData(40, 32, 30, 2);
        exact = new BruteForceIndex();
        for (int i = 0; i < data.length; i++) {
            exact.add(i, data[i]);
        }
    }

    @Test
    void bruteForceReturnsSortedExactNeighbours() {
        var hits = exact.search(data[7], 5);
        assertEquals(7, hits.getFirst().id(), "a stored vector is its own nearest neighbour");
        assertEquals(1.0, hits.getFirst().score(), 1e-6);
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).score() >= hits.get(i).score());
        }
    }

    @Test
    void ivfTradesRecallForWorkViaNprobe() {
        IvfIndex all = new IvfIndex(30, 30, 3);
        IvfIndex few = new IvfIndex(30, 2, 3);
        for (int i = 0; i < data.length; i++) {
            all.add(i, data[i]);
            few.add(i, data[i]);
        }
        assertEquals(1.0, Benchmark.recallAtK(exact, all, queries, 10), 1e-9, "probing every cell is exact");
        double partial = Benchmark.recallAtK(exact, few, queries, 10);
        assertTrue(partial > 0.5 && partial <= 1.0, "nprobe=2 recall " + partial);
        assertTrue(few.scannedFor(queries[0]) < data.length / 3, "scans a fraction of the data");
    }

    @Test
    void hnswReachesHighRecallAndEfSearchControlsIt() {
        Hnsw hnsw = new Hnsw(12, 80, 64, 5);
        for (int i = 0; i < data.length; i++) {
            hnsw.add(i, data[i]);
        }
        double high = Benchmark.recallAtK(exact, hnsw, queries, 10);
        hnsw.setEfSearch(10);
        double low = Benchmark.recallAtK(exact, hnsw, queries, 10);
        assertTrue(high >= 0.95, "recall@10 with ef=64: " + high);
        assertTrue(low <= high, "smaller ef -> lower or equal recall");
        assertTrue(hnsw.layers() >= 2, "a hierarchy was built");
        assertEquals(7, hnsw.search(data[7], 1).getFirst().id());
    }

    @Test
    void rrfRewardsDocumentsRankedHighInBothLists() {
        List<Integer> bm25 = List.of(1, 2, 3, 4);
        List<Integer> vector = List.of(3, 5, 1, 6);
        List<Integer> fused = Ranking.rrf(List.of(bm25, vector), 60);
        assertEquals(List.of(1, 3), fused.subList(0, 2), "1 and 3 appear high in both lists");
        assertEquals(6, fused.size());
    }

    @Test
    void mmrAvoidsNearDuplicates() {
        float[][] v = {{1, 0, 0}, {0.98f, -0.05f, 0}, {0.7f, 0.7f, 0}};
        float[] q = {1, 0.2f, 0};
        List<Integer> relevanceOnly = Ranking.mmr(q, List.of(0, 1, 2), i -> v[i], 2, 1.0);
        List<Integer> diverse = Ranking.mmr(q, List.of(0, 1, 2), i -> v[i], 2, 0.5);
        assertEquals(List.of(0, 1), relevanceOnly, "pure relevance picks the near-duplicate pair");
        assertEquals(List.of(0, 2), diverse, "MMR skips the duplicate");
    }

    @Test
    void vectorStoreSearchIsScopedByMetadataFilter() {
        TenantSearch search = new TenantSearch(SimpleVectorStore.builder(new KeywordEmbeddingModel()).build());
        search.index("acme", "public", "a1", "How to reset the SSO password");
        search.index("acme", "internal", "a2", "SSO password reset runbook for admins");
        search.index("globex", "public", "g1", "SSO password reset at Globex");
        List<String> ids = new ArrayList<>();
        search.search("acme", List.of("public"), "reset SSO password", 10).forEach(d -> ids.add(d.getId()));
        assertEquals(List.of("a1"), ids, "other tenants and internal docs are never returned");
        assertEquals(2, search.search("acme", List.of("public", "internal"), "reset SSO password", 10).size());
    }

    /** Fake embedding: one dimension per keyword. Enough to exercise the store and filters deterministically. */
    static final class KeywordEmbeddingModel implements EmbeddingModel {
        static final String[] WORDS = {"sso", "password", "reset", "runbook", "admins", "globex", "how"};

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                out.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        static float[] vector(String text) {
            String t = text.toLowerCase(Locale.ROOT);
            float[] v = new float[WORDS.length + 1];
            v[WORDS.length] = 0.1f;
            for (int i = 0; i < WORDS.length; i++) {
                v[i] = t.contains(WORDS[i]) ? 1 : 0;
            }
            return v;
        }
    }
}
