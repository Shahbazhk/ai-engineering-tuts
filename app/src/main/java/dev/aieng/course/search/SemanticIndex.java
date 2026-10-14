package dev.aieng.course.search;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.aieng.course.content.Course;
import dev.aieng.course.content.Course.Section;

/**
 * Dense-vector index over lesson sections: brute-force cosine similarity on L2-normalised embeddings.
 * Exact search over ~1-2k vectors takes well under a millisecond, so no ANN structure is needed here
 * (Phase 8 builds HNSW and explains when it starts paying off).
 * ponytail: embeddings are recomputed on every start; persist them keyed by content hash if startup gets slow.
 */
@Component
public class SemanticIndex {

    public enum State { DISABLED, NOT_BUILT, BUILDING, READY, FAILED }

    public record Scored(int section, double score) {
    }

    private static final Logger log = LoggerFactory.getLogger(SemanticIndex.class);
    private static final int MAX_CHARS = 2000;
    private static final int BATCH = 32;

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final List<Section> sections;
    private final List<String> titles;
    private final boolean buildOnStartup;
    private String queryPrefix = "";
    private String documentPrefix = "";

    private volatile float[][] vectors;
    private volatile State state;
    private volatile String error = "";

    public SemanticIndex(ObjectProvider<EmbeddingModel> embeddingModel, Course course,
                  @Value("${course.ai.semantic-index-on-startup:true}") boolean buildOnStartup) {
        this.embeddingModel = embeddingModel;
        this.sections = course.phases().stream().flatMap(p -> p.lessons().stream())
                .flatMap(l -> l.sections().stream()).toList();
        this.titles = sections.stream().map(s -> course.lesson(s.lessonId()).title()).toList();
        this.buildOnStartup = buildOnStartup;
        this.state = embeddingModel.getIfAvailable() == null ? State.DISABLED : State.NOT_BUILT;
    }

    /**
     * Asymmetric embedding models are trained with instructions such as "search_query: " / "search_document: "
     * (nomic-embed-text) or "query: " / "passage: " (e5). Omitting them silently lowers retrieval quality.
     */
    @Value("${course.ai.query-prefix:search_query: }")
    void setQueryPrefix(String prefix) {
        this.queryPrefix = prefix;
    }

    @Value("${course.ai.document-prefix:search_document: }")
    void setDocumentPrefix(String prefix) {
        this.documentPrefix = prefix;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        if (buildOnStartup) {
            rebuildAsync();
        }
    }

    public synchronized void rebuildAsync() {
        if (state == State.DISABLED || state == State.BUILDING) {
            return;
        }
        state = State.BUILDING;
        Thread.ofVirtual().name("semantic-index").start(this::build);
    }

    private void build() {
        long start = System.currentTimeMillis();
        try {
            EmbeddingModel model = embeddingModel.getObject();
            float[][] built = new float[sections.size()][];
            for (int from = 0; from < sections.size(); from += BATCH) {
                List<String> batch = new ArrayList<>();
                for (int i = from; i < Math.min(from + BATCH, sections.size()); i++) {
                    batch.add(textFor(i));
                }
                List<float[]> embedded = model.embed(batch);
                for (int j = 0; j < embedded.size(); j++) {
                    built[from + j] = normalize(embedded.get(j));
                }
            }
            vectors = built;
            state = State.READY;
            log.info("Semantic index ready: {} sections in {} ms", built.length, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            error = String.valueOf(e.getMessage());
            state = State.FAILED;
            log.warn("Semantic index unavailable (is Ollama running and the embedding model pulled?): {}", error);
        }
    }

    private String textFor(int i) {
        Section s = sections.get(i);
        String text = documentPrefix + titles.get(i) + " - " + s.heading() + "\n" + s.text();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    public List<Scored> search(String query, int k) {
        float[][] vs = vectors;
        if (state != State.READY || vs == null) {
            return List.of();
        }
        float[] q = normalize(embeddingModel.getObject().embed(queryPrefix + query));
        PriorityQueue<Scored> top = new PriorityQueue<>((a, b) -> Double.compare(a.score(), b.score()));
        for (int i = 0; i < vs.length; i++) {
            top.add(new Scored(i, dot(q, vs[i])));
            if (top.size() > k) {
                top.poll();
            }
        }
        List<Scored> out = new ArrayList<>(top);
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    public float[] embed(String text) {
        return normalize(embeddingModel.getObject().embed(text));
    }

    public boolean embeddingsAvailable() {
        return state != State.DISABLED;
    }

    public State state() {
        return state;
    }

    public String error() {
        return error;
    }

    static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = norm == 0 ? 0 : (float) (v[i] / norm);
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
