package dev.aieng.p12;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * RAG-specific metrics in the spirit of RAGAS. Retrieval and generation are scored separately, because a wrong
 * answer has two very different root causes: the evidence was missing, or the model ignored or invented it.
 */
public final class RagMetrics {

    private RagMetrics() {
    }

    // region rag
    /**
     * Faithfulness: split the answer into claims (sentences here) and ask a verifier whether each is supported by
     * the retrieved context. 1.0 = every claim grounded; low = hallucination. The verifier is an LLM in practice.
     */
    public static double faithfulness(String answer, String context, BiPredicate<String, String> supports) {
        List<String> claims = Arrays.stream(answer.split("(?<=[.!?])\\s+"))
                .map(String::strip).filter(c -> c.length() > 3).toList();
        if (claims.isEmpty()) {
            return 1.0;
        }
        return (double) claims.stream().filter(c -> supports.test(c, context)).count() / claims.size();
    }

    /** Context precision: fraction of retrieved chunks that are relevant to the question (judged or labelled). */
    public static double contextPrecision(List<String> chunks, BiPredicate<String, String> relevantTo, String question) {
        return chunks.isEmpty() ? 0 : (double) chunks.stream().filter(c -> relevantTo.test(c, question)).count() / chunks.size();
    }

    /** Context recall: fraction of ground-truth facts that appear somewhere in the retrieved context. */
    public static double contextRecall(List<String> chunks, List<String> groundTruthFacts) {
        return TextMetrics.keyFactRecall(String.join("\n", chunks), groundTruthFacts);
    }
    // endregion

    /** A crude lexical verifier for offline tests: a claim is "supported" if most of its content words occur in context. */
    public static boolean lexicalSupport(String claim, String context) {
        String ctx = TextMetrics.normalize(context);
        List<String> words = Arrays.stream(TextMetrics.normalize(claim).split(" ")).filter(w -> w.length() > 3).toList();
        if (words.isEmpty()) {
            return true;
        }
        return words.stream().filter(ctx::contains).count() >= Math.ceil(words.size() * 0.75);
    }
}
