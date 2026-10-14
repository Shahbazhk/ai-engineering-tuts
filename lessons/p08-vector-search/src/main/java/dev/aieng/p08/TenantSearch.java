package dev.aieng.p08;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Spring AI's portable VectorStore API with a non-negotiable metadata filter. Every search is scoped to the
 * caller's tenant and visibility, applied INSIDE the vector store query - never by filtering results afterwards,
 * which leaks information through scores and returns fewer than k hits.
 */
public class TenantSearch {

    private final VectorStore store;

    public TenantSearch(VectorStore store) {
        this.store = store;
    }

    // region usage
    public void index(String tenant, String visibility, String id, String text) {
        store.add(List.of(new Document(id, text, Map.of("tenant", tenant, "visibility", visibility))));
    }

    public List<Document> search(String tenant, List<String> allowedVisibility, String query, int k) {
        FilterExpressionBuilder f = new FilterExpressionBuilder();
        return store.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(k)
                .similarityThreshold(0.0)
                .filterExpression(f.and(f.eq("tenant", tenant), f.in("visibility", allowedVisibility.toArray())).build())
                .build());
    }
    // endregion
}
