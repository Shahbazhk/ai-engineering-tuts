package dev.aieng.p09;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * The same pipeline with Spring AI's modular RAG: a RetrievalAugmentationAdvisor (an advisor, like memory and
 * tools) composed of a retriever, optional post-processors (permission checks, reranking) and an augmenter that
 * writes the retrieved context into the prompt.
 */
public class SpringRag {

    private final ChatClient chat;

    // region advisor
    public SpringRag(ChatModel model, VectorStore store, String siteId, List<DocumentPostProcessor> postProcessors) {
        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(store)
                .topK(5)
                .similarityThreshold(0.3)                                   // relevance gate
                .filterExpression(new FilterExpressionBuilder().eq("siteId", siteId).build())
                .build();
        var rag = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .documentPostProcessors(postProcessors)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)  // no context -> instruct the model to say it doesn't know
                        .build())
                .build();
        this.chat = ChatClient.builder(model)
                .defaultSystem("You answer employee questions about the intranet. Be concise.")
                .defaultAdvisors(rag)
                .build();
    }
    // endregion

    public String ask(String question) {
        return chat.prompt().user(question).call().content();
    }

    /** Ingestion: deterministic ids (source id + chunk number) so re-ingesting upserts instead of duplicating. */
    public static List<Document> toDocuments(String sourceId, String title, String siteId, String markdown) {
        return Chunker.byHeadings(markdown, 800).stream()
                .map(c -> new Document(sourceId + "#" + c.index(), Chunker.withHeader(title, c),
                        java.util.Map.of("siteId", siteId, "sourceId", sourceId, "title", title)))
                .toList();
    }
}
