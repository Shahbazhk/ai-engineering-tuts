package dev.aieng.course.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class Bm25IndexTest {

    @Test
    void ranksTheDocumentThatMatchesRareTermsFirst() {
        Bm25Index index = new Bm25Index();
        index.add("Spring Boot auto-configuration creates beans");
        index.add("HNSW is a graph index for approximate nearest neighbour search");
        index.add("Spring beans and the application context");
        List<Bm25Index.Scored> hits = index.search("nearest neighbour graph", 3);
        assertEquals(1, hits.get(0).doc());
        assertEquals(1, hits.size());
    }

    @Test
    void shorterDocumentWinsOnEqualTermFrequency() {
        Bm25Index index = new Bm25Index();
        index.add("embedding " + "filler ".repeat(50));
        index.add("embedding vector");
        assertEquals(1, index.search("embedding", 2).get(0).doc());
    }

    @Test
    void tokenizerDropsStopwordsAndStemsPlurals() {
        assertEquals(List.of("embedding", "query"), Bm25Index.tokenize("The embeddings of queries"));
    }

    @Test
    void emptyIndexAndUnknownTermsReturnNothing() {
        assertTrue(new Bm25Index().search("anything", 5).isEmpty());
        Bm25Index index = new Bm25Index();
        index.add("hello world");
        assertTrue(index.search("zzz", 5).isEmpty());
    }

    @Test
    void snippetHighlightsWithoutBreakingHtml() {
        String s = SearchService.snippet("Use <b> & attention heads here", "attention");
        assertTrue(s.contains("<mark>attention</mark>"), s);
        assertTrue(s.contains("&lt;b&gt; &amp;"), s);
    }
}
