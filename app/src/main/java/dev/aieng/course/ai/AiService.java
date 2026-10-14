package dev.aieng.course.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import dev.aieng.course.search.SearchService;
import dev.aieng.course.search.SearchService.Hit;
import dev.aieng.course.search.SemanticIndex;

/**
 * Everything that talks to a model: the RAG tutor and the playground. Every method degrades gracefully when
 * no model is configured or reachable - the course must stay usable offline.
 */
@Service
public class AiService {

    public record Completion(String text, String html, String model, long millis, Integer promptTokens,
                             Integer completionTokens, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public record TutorAnswer(String question, List<Hit> sources, String system, String prompt, Completion completion) {
    }

    public record Similarity(List<String> texts, double[][] matrix, String method) {
    }

    static final String TUTOR_SYSTEM = """
            You are the teaching assistant for "AI Engineering From Scratch - Java & Spring Edition".
            The learner is a senior Java/Spring engineer, so skip Java basics and be precise.
            Answer ONLY from the numbered context passages.
            After every sentence that uses a passage, cite it with its number in square brackets, e.g. [1] or [2].
            If the context does not contain the answer, say so plainly and name the closest lesson.
            Prefer short answers with a Java example when code helps.""";

    private static final Parser MD = Parser.builder().build();
    // Model output is untrusted: raw HTML is escaped and link URLs sanitised.
    private static final HtmlRenderer SAFE_HTML = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    private final ObjectProvider<ChatModel> chatModel;
    private final SearchService search;
    private final SemanticIndex semantic;
    private final String provider;

    AiService(ObjectProvider<ChatModel> chatModel, SearchService search, SemanticIndex semantic,
              @Value("${spring.ai.model.chat:none}") String provider) {
        this.chatModel = chatModel;
        this.search = search;
        this.semantic = semantic;
        this.provider = provider;
    }

    public boolean chatAvailable() {
        return chatModel.getIfAvailable() != null;
    }

    public String provider() {
        return provider;
    }

    public TutorAnswer ask(String question, int k) {
        List<Hit> sources = search.retrieve(question, k);
        String prompt = tutorPrompt(question, sources);
        Completion completion = complete(TUTOR_SYSTEM, prompt, null, 0.2);
        if (completion.ok()) {
            completion = withCitationLinks(completion, sources);
        }
        return new TutorAnswer(question, sources, TUTOR_SYSTEM, prompt, completion);
    }

    static String tutorPrompt(String question, List<Hit> sources) {
        StringBuilder sb = new StringBuilder("Context passages:\n\n");
        for (int i = 0; i < sources.size(); i++) {
            Hit h = sources.get(i);
            String text = h.section().text();
            sb.append('[').append(i + 1).append("] ").append(h.lesson().title()).append(" > ").append(h.section().heading())
                    .append('\n').append(text.length() > 1500 ? text.substring(0, 1500) + " ..." : text).append("\n\n");
        }
        return sb.append("Question: ").append(question).toString();
    }

    public Completion complete(String system, String user, String model, Double temperature) {
        ChatModel cm = chatModel.getIfAvailable();
        if (cm == null) {
            return failed("No chat model configured (spring.ai.model.chat=" + provider + ").");
        }
        long start = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec request = ChatClient.create(cm).prompt();
            if (system != null && !system.isBlank()) {
                request = request.system(system);
            }
            ChatOptions.Builder<?> options = ChatOptions.builder();
            if (model != null && !model.isBlank()) {
                options.model(model.strip());
            }
            if (temperature != null) {
                options.temperature(temperature);
            }
            ChatResponse response = request.user(user).options(options).call().chatResponse();
            String text = response == null || response.getResult() == null ? ""
                    : String.valueOf(response.getResult().getOutput().getText());
            Usage usage = response == null ? null : response.getMetadata().getUsage();
            String usedModel = response == null ? model : response.getMetadata().getModel();
            return new Completion(text, toHtml(text), usedModel, System.currentTimeMillis() - start,
                    usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens(), null);
        } catch (RuntimeException e) {
            return failed(rootMessage(e));
        }
    }

    public Similarity similarity(List<String> texts) {
        List<float[]> vectors = new ArrayList<>();
        String method;
        try {
            if (!semantic.embeddingsAvailable()) {
                throw new IllegalStateException("no embedding model");
            }
            for (String t : texts) {
                vectors.add(semantic.embed(t));
            }
            method = "Embedding model (" + provider + ")";
        } catch (RuntimeException e) {
            vectors.clear();
            for (String t : texts) {
                vectors.add(hashedBagOfWords(t));
            }
            method = "Offline fallback: hashed bag-of-words (no embedding model reachable: " + rootMessage(e) + ")";
        }
        double[][] m = new double[texts.size()][texts.size()];
        for (int i = 0; i < texts.size(); i++) {
            for (int j = 0; j < texts.size(); j++) {
                m[i][j] = cosine(vectors.get(i), vectors.get(j));
            }
        }
        return new Similarity(texts, m, method);
    }

    /** The "feature hashing" trick: a sparse lexical vector squeezed into 512 buckets. No semantics, just overlap. */
    static float[] hashedBagOfWords(String text) {
        float[] v = new float[512];
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                v[Math.floorMod(w.hashCode(), v.length)] += 1;
            }
        }
        return v;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    static String toHtml(String markdown) {
        return SAFE_HTML.render(MD.parse(markdown));
    }

    private static Completion withCitationLinks(Completion c, List<Hit> sources) {
        Matcher m = CITATION.matcher(c.html());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String replacement = n >= 1 && n <= sources.size()
                    ? "<a class=\"cite\" href=\"" + HtmlUtils.htmlEscape(sources.get(n - 1).url()) + "\">[" + n + "]</a>"
                    : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return new Completion(c.text(), sb.toString(), c.model(), c.millis(), c.promptTokens(), c.completionTokens(), null);
    }

    private static Completion failed(String error) {
        return new Completion("", "", null, 0, null, null, error);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
