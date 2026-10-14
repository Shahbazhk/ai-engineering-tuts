package dev.aieng.p09;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Pre-retrieval query transformations. Users ask short, vague or conversational questions; documents are written
 * differently. Each transformation costs one extra (cheap) model call before retrieval.
 */
public final class QueryTransforms {

    private QueryTransforms() {
    }

    // region transforms
    /** Rewrite a follow-up ("and on Mac?") into a standalone query using the conversation. */
    public static String standalone(String history, String followUp, Function<String, String> llm) {
        return llm.apply("""
                Rewrite the final user question so it can be understood without the conversation.
                Return only the rewritten question.

                Conversation:
                %s

                Final question: %s""".formatted(history, followUp)).strip();
    }

    /** Multi-query: generate paraphrases, retrieve for each, fuse the results (e.g. with RRF from Phase 8). */
    public static List<String> multiQuery(String question, int n, Function<String, String> llm) {
        String raw = llm.apply("""
                Write %d different search queries that would find documents answering the question below.
                One query per line, no numbering.

                Question: %s""".formatted(n, question));
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(question); // always keep the original
        for (String line : raw.split("\\R")) {
            String q = line.replaceFirst("^[-*\\d.)\\s]+", "").strip();
            if (!q.isEmpty()) {
                queries.add(q);
            }
        }
        return new ArrayList<>(queries).subList(0, Math.min(n + 1, queries.size()));
    }

    /**
     * HyDE (Gao et al., 2022): embed a hypothetical ANSWER instead of the question. Answers look like documents,
     * so their embeddings land closer to the relevant passages. The hypothetical text is never shown to users.
     */
    public static String hypotheticalDocument(String question, Function<String, String> llm) {
        return llm.apply("Write a short passage from internal documentation that answers: " + question);
    }
    // endregion
}
