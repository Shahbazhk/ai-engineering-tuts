package dev.aieng.p04;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class NlpTest {

    @Test
    void tfIdfRanksExactWordMatchesButMissesSynonyms() {
        List<String> docs = List.of("Configure the DataSource URL and pool size", "Bake bread at 220 degrees",
                "Database connection settings for Spring Boot");
        TfIdf tfidf = new TfIdf().fit(docs);
        assertEquals(0, tfidf.rank(docs, "datasource pool").getFirst());
        // "oven temperature" shares no words with any document: every score is 0, ranking is arbitrary.
        double[] q = tfidf.vector("oven temperature");
        assertEquals(0, java.util.Arrays.stream(q).sum(), 1e-12);
    }

    @Test
    void bpeLearnsFrequentSubwordsAndRoundTrips() {
        String corpus = "low lower lowest low low newer newest new new wider widest".repeat(3);
        Bpe bpe = new Bpe().train(corpus, 30);
        assertTrue(bpe.merges() > 5);
        // Frequent training words become single tokens.
        assertEquals(List.of(Bpe.SPACE + "lowest", Bpe.SPACE + "newer"), bpe.encode("lowest newer"));
        // An unseen word is split into known subwords - the frequent stem survives - and still round-trips.
        List<String> unseen = bpe.encode("lowness");
        assertTrue(unseen.getFirst().startsWith(Bpe.SPACE + "low"), unseen.toString());
        assertTrue(unseen.size() > 1 && unseen.size() < "lowness".length(), unseen.toString());
        assertEquals("lowness", bpe.decode(unseen));
    }

    @Test
    void bpeIsDeterministic() {
        String corpus = "aaab aaab abab abab";
        assertEquals(new Bpe().train(corpus, 10).encode("aaab abab"), new Bpe().train(corpus, 10).encode("aaab abab"));
    }

    @Test
    void word2vecGroupsWordsByContext() {
        Word2Vec w2v = new Word2Vec().train(Word2Vec.toyCorpus(), 16, 2, 3, 30, 0.05, 1);
        assertTrue(w2v.similarity("king", "queen") > w2v.similarity("king", "dog"));
        assertTrue(w2v.similarity("dog", "cat") > w2v.similarity("cat", "crown"));
    }

    @Test
    void semanticSearchFindsParaphrasesThroughTheEmbeddingModel() {
        SemanticSearch search = new SemanticSearch(new ConceptEmbeddingModel());
        search.index(List.of("Spring Boot JDBC connection settings", "Baking sourdough bread", "Rotating TLS certificates"));
        var hits = search.search("how do I configure a datasource", 2);
        assertEquals("Spring Boot JDBC connection settings", hits.getFirst().text());
        assertTrue(hits.getFirst().score() > hits.get(1).score());
    }

    @Test
    void meanPoolingIgnoresPaddingAndTruncationRenormalises() {
        float[][] tokens = {{1, 0}, {3, 2}, {100, 100}};
        float[] pooled = SemanticSearch.meanPool(tokens, new int[] {1, 1, 0});
        assertEquals(2f, pooled[0], 1e-6);
        assertEquals(1f, pooled[1], 1e-6);
        float[] t = SemanticSearch.truncate(new float[] {3, 4, 12}, 2);
        assertEquals(0.6f, t[0], 1e-6);
        assertEquals(0.8f, t[1], 1e-6);
    }

    /**
     * A deterministic fake embedding model: each dimension is a "concept" and words vote for concepts.
     * Real models learn this from data; the fake just makes the test independent of any model server.
     */
    static final class ConceptEmbeddingModel implements EmbeddingModel {
        private static final Map<String, Integer> CONCEPTS = Map.ofEntries(
                Map.entry("datasource", 0), Map.entry("jdbc", 0), Map.entry("database", 0), Map.entry("connection", 0),
                Map.entry("configure", 1), Map.entry("settings", 1), Map.entry("config", 1),
                Map.entry("bread", 2), Map.entry("baking", 2), Map.entry("sourdough", 2),
                Map.entry("tls", 3), Map.entry("certificates", 3), Map.entry("rotating", 3));

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new java.util.ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                out.add(new Embedding(embedText(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return embedText(document.getText());
        }

        private static float[] embedText(String text) {
            float[] v = new float[5];
            v[4] = 0.1f; // small shared component so no vector is all zeros
            for (String w : text.toLowerCase(Locale.ROOT).split("\\W+")) {
                Integer c = CONCEPTS.get(w);
                if (c != null) {
                    v[c] += 1;
                }
            }
            return v;
        }
    }
}
