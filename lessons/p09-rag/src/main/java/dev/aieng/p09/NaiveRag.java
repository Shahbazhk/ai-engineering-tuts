package dev.aieng.p09;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Retrieval-augmented generation with no framework: chunk, embed, store; at question time embed, retrieve,
 * build a grounded prompt with numbered sources, generate. The embedder and generator are plain functions, so the
 * whole pipeline is testable with fakes and runs against any model.
 */
public final class NaiveRag {

    public record Source(String documentId, String title, String text, double score) {
    }

    public record Answer(String text, List<Source> sources, boolean grounded) {
    }

    record Entry(String documentId, String title, String text, float[] vector) {
    }

    static final String NO_ANSWER = "I couldn't find this in the knowledge base.";

    private final Function<String, float[]> embed;
    private final Function<String, String> generate;
    private final int topK;
    private final double minScore;
    private final List<Entry> store = new ArrayList<>();

    public NaiveRag(Function<String, float[]> embed, Function<String, String> generate, int topK, double minScore) {
        this.embed = embed;
        this.generate = generate;
        this.topK = topK;
        this.minScore = minScore;
    }

    // region ingest
    public void ingest(String documentId, String title, String markdown) {
        for (Chunker.Chunk chunk : Chunker.byHeadings(markdown, 800)) {
            String text = Chunker.withHeader(title, chunk);
            store.add(new Entry(documentId, title, text, normalize(embed.apply(text))));
        }
    }
    // endregion

    // region ask
    public Answer ask(String question) {
        float[] q = normalize(embed.apply(question));
        List<Source> sources = store.stream()
                .map(e -> new Source(e.documentId(), e.title(), e.text(), dot(q, e.vector())))
                .filter(s -> s.score() >= minScore)                 // relevance gate
                .sorted(Comparator.comparingDouble(Source::score).reversed())
                .limit(topK)
                .toList();
        if (sources.isEmpty()) {
            // Refuse WITHOUT calling the model: nothing to ground an answer in, and the call would cost money.
            return new Answer(NO_ANSWER, List.of(), false);
        }
        return new Answer(generate.apply(prompt(question, sources)), sources, true);
    }

    static String prompt(String question, List<Source> sources) {
        StringBuilder sb = new StringBuilder("""
                Answer the question using ONLY the sources below. Cite sources as [n].
                If the sources do not contain the answer, reply exactly: %s

                """.formatted(NO_ANSWER));
        for (int i = 0; i < sources.size(); i++) {
            sb.append("<source id=\"").append(i + 1).append("\">\n").append(sources.get(i).text()).append("\n</source>\n");
        }
        return sb.append("\n<question>\n").append(question).append("\n</question>").toString();
    }
    // endregion

    public int chunks() {
        return store.size();
    }

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
