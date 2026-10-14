package dev.aieng.course.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import dev.aieng.course.content.ContentLoader;
import dev.aieng.course.content.Course;

/**
 * Eval gate for the Tutor's retrieval step (Phase 12 dogfooding): keyword retrieval must find the lesson that answers
 * each question in its top 5. A content or search change that breaks retrieval fails the build.
 */
class TutorRetrievalEvalTest {

    static final double MIN_HIT_RATE_AT_5 = 0.85;
    static final double MIN_MRR = 0.65;

    @Test
    void keywordRetrievalFindsTheAnsweringLesson() throws Exception {
        Course course = new ContentLoader(Path.of("..").toAbsolutePath().normalize()).load();
        SemanticIndex semantic = new SemanticIndex(new DefaultListableBeanFactory().getBeanProvider(EmbeddingModel.class), course, false);
        SearchService search = new SearchService(course, semantic);

        List<Map<String, String>> cases;
        try (InputStream in = getClass().getResourceAsStream("/evals/tutor-retrieval.yml")) {
            cases = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        }
        double hits = 0;
        double reciprocalRanks = 0;
        List<String> misses = new ArrayList<>();
        for (Map<String, String> c : cases) {
            List<String> lessons = new ArrayList<>(new LinkedHashSet<>(
                    search.keyword(c.get("q"), 20).stream().map(h -> h.lesson().id()).toList()));
            int rank = lessons.indexOf(c.get("lesson"));
            if (rank >= 0 && rank < 5) {
                hits++;
            } else {
                misses.add(c.get("q") + " -> got " + lessons.subList(0, Math.min(3, lessons.size())));
            }
            reciprocalRanks += rank >= 0 ? 1.0 / (rank + 1) : 0;
        }
        double hitRate = hits / cases.size();
        double mrr = reciprocalRanks / cases.size();
        System.out.printf("Tutor retrieval eval: %d cases, hit@5 %.3f, MRR %.3f%n", cases.size(), hitRate, mrr);
        misses.forEach(m -> System.out.println("  miss: " + m));
        assertTrue(hitRate >= MIN_HIT_RATE_AT_5, "hit@5 " + hitRate + " below " + MIN_HIT_RATE_AT_5 + "; misses: " + misses);
        assertTrue(mrr >= MIN_MRR, "MRR " + mrr + " below " + MIN_MRR);
    }
}
