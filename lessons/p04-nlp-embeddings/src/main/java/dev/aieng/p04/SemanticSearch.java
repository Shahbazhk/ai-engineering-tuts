package dev.aieng.p04;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Sentence-level semantic search against Spring AI's provider-neutral {@link EmbeddingModel}. The same code runs
 * against Ollama, OpenAI, an in-process ONNX model or a test fake - only the bean changes.
 */
public final class SemanticSearch {

    public record Hit(String text, double score) {
    }

    private final EmbeddingModel model;
    private final List<String> texts = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();

    public SemanticSearch(EmbeddingModel model) {
        this.model = model;
    }

    // region index
    /** Embeds in one batch call (far cheaper than one call per text) and stores L2-normalised vectors. */
    public void index(List<String> documents) {
        List<float[]> embedded = model.embed(documents);
        for (int i = 0; i < documents.size(); i++) {
            texts.add(documents.get(i));
            vectors.add(normalize(embedded.get(i)));
        }
    }

    public List<Hit> search(String query, int k) {
        float[] q = normalize(model.embed(query));
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            hits.add(new Hit(texts.get(i), dot(q, vectors.get(i))));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.subList(0, Math.min(k, hits.size()));
    }
    // endregion

    // region pooling
    /**
     * Mean pooling: average the token vectors (respecting the attention mask) to get one sentence vector.
     * This is what sentence-transformer models do on top of their per-token transformer outputs.
     */
    public static float[] meanPool(float[][] tokenVectors, int[] attentionMask) {
        float[] out = new float[tokenVectors[0].length];
        int count = 0;
        for (int t = 0; t < tokenVectors.length; t++) {
            if (attentionMask[t] == 0) {
                continue; // padding tokens must not dilute the average
            }
            count++;
            for (int d = 0; d < out.length; d++) {
                out[d] += tokenVectors[t][d];
            }
        }
        for (int d = 0; d < out.length; d++) {
            out[d] /= Math.max(count, 1);
        }
        return out;
    }

    /**
     * Matryoshka truncation: models trained with Matryoshka Representation Learning put the most important
     * information in the first dimensions, so you can keep e.g. 256 of 768 dims and re-normalise.
     */
    public static float[] truncate(float[] v, int dims) {
        return normalize(java.util.Arrays.copyOf(v, dims));
    }
    // endregion

    static float[] normalize(float[] v) {
        double n = 0;
        for (float x : v) {
            n += x * x;
        }
        n = Math.sqrt(n);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = n == 0 ? 0 : (float) (v[i] / n);
        }
        return out;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
