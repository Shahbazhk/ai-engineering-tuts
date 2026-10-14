package dev.aieng.course.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import dev.aieng.course.content.Course;
import dev.aieng.course.content.Course.Lesson;
import dev.aieng.course.content.Course.Section;

/**
 * Keyword (BM25), semantic (embeddings) and hybrid search over lesson sections. Hybrid uses Reciprocal Rank
 * Fusion, which merges ranked lists without needing comparable scores: rrf(d) = sum 1 / (60 + rank(d)).
 */
@Service
public class SearchService {

    public enum Mode { KEYWORD, SEMANTIC, HYBRID }

    public record Hit(Section section, Lesson lesson, double score, String snippetHtml) {
        public String url() {
            return lesson.url() + (section.anchor().isEmpty() ? "" : "#" + section.anchor());
        }
    }

    private static final int RRF_K = 60;

    private final Course course;
    private final SemanticIndex semantic;
    private final List<Section> sections;
    private final Bm25Index bm25 = new Bm25Index();

    public SearchService(Course course, SemanticIndex semantic) {
        this.course = course;
        this.semantic = semantic;
        this.sections = course.phases().stream().flatMap(p -> p.lessons().stream())
                .flatMap(l -> l.sections().stream()).toList();
        for (Section s : sections) {
            Lesson l = course.lesson(s.lessonId());
            // Titles and headings are repeated: a cheap field boost.
            bm25.add(l.title() + " " + l.title() + " " + s.heading() + " " + s.heading() + " " + String.join(" ", l.tags())
                    + " " + s.text());
        }
    }

    public List<Hit> search(String query, Mode mode, int k) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return switch (mode) {
            case KEYWORD -> keyword(query, k);
            case SEMANTIC -> semanticHits(query, k);
            case HYBRID -> hybrid(query, k);
        };
    }

    /** Hybrid when embeddings are ready, keyword otherwise: the retrieval step used by the tutor. */
    public List<Hit> retrieve(String query, int k) {
        return search(query, semantic.state() == SemanticIndex.State.READY ? Mode.HYBRID : Mode.KEYWORD, k);
    }

    public List<Hit> keyword(String query, int k) {
        return bm25.search(query, k).stream().map(s -> hit(s.doc(), s.score(), query)).toList();
    }

    private List<Hit> semanticHits(String query, int k) {
        return semantic.search(query, k).stream().map(s -> hit(s.section(), s.score(), query)).toList();
    }

    private List<Hit> hybrid(String query, int k) {
        int depth = Math.max(k * 4, 20);
        Map<Integer, Double> fused = new HashMap<>();
        List<Bm25Index.Scored> kw = bm25.search(query, depth);
        for (int r = 0; r < kw.size(); r++) {
            fused.merge(kw.get(r).doc(), 1.0 / (RRF_K + r + 1), Double::sum);
        }
        List<SemanticIndex.Scored> sem = semantic.search(query, depth);
        for (int r = 0; r < sem.size(); r++) {
            fused.merge(sem.get(r).section(), 1.0 / (RRF_K + r + 1), Double::sum);
        }
        List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(fused.entrySet());
        ranked.sort(Map.Entry.<Integer, Double>comparingByValue().reversed());
        return ranked.stream().limit(k).map(e -> hit(e.getKey(), e.getValue(), query)).toList();
    }

    private Hit hit(int i, double score, String query) {
        Section s = sections.get(i);
        return new Hit(s, course.lesson(s.lessonId()), score, snippet(s.text(), query));
    }

    public int indexedSections() {
        return sections.size();
    }

    static String snippet(String text, String query) {
        String flat = text.replaceAll("\\s+", " ");
        String lower = flat.toLowerCase(Locale.ROOT);
        int at = -1;
        for (String term : Bm25Index.tokenize(query)) {
            at = lower.indexOf(term);
            if (at >= 0) {
                break;
            }
        }
        int from = Math.max(0, at - 90);
        int to = Math.min(flat.length(), Math.max(at, 0) + 180);
        String window = flat.substring(from, to);
        String terms = String.join("|", Bm25Index.tokenize(query).stream().map(java.util.regex.Pattern::quote).toList());
        StringBuilder html = new StringBuilder(from > 0 ? "&hellip;" : "");
        int last = 0;
        if (!terms.isEmpty()) {
            var m = java.util.regex.Pattern.compile("(?i)\\b(?:" + terms + ")\\w*").matcher(window);
            while (m.find()) {
                html.append(HtmlUtils.htmlEscape(window.substring(last, m.start())))
                        .append("<mark>").append(HtmlUtils.htmlEscape(m.group())).append("</mark>");
                last = m.end();
            }
        }
        html.append(HtmlUtils.htmlEscape(window.substring(last)));
        return html.append(to < flat.length() ? "&hellip;" : "").toString();
    }
}
