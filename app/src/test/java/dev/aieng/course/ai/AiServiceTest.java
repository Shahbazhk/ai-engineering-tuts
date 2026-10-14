package dev.aieng.course.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import dev.aieng.course.content.Course;
import dev.aieng.course.content.ContentLoader;
import dev.aieng.course.search.SearchService;
import dev.aieng.course.search.SemanticIndex;

/** The tutor pipeline with a scripted ChatModel: retrieval, prompt assembly, citation linking, output escaping. */
class AiServiceTest {

    static final Course COURSE = new ContentLoader(java.nio.file.Path.of("..").toAbsolutePath().normalize()).load();

    @Test
    void tutorPutsRetrievedContextInThePromptAndLinksCitations() {
        AtomicReference<Prompt> seen = new AtomicReference<>();
        ChatModel scripted = prompt -> {
            seen.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Use `stream: true` [1]. <script>x</script> [9]"))));
        };
        AiService ai = service(scripted);

        AiService.TutorAnswer answer = ai.ask("How do I stream tokens from Ollama?", 3);

        assertTrue(answer.completion().ok(), answer.completion().error());
        assertFalse(answer.sources().isEmpty());
        String userMessage = seen.get().getUserMessage().getText();
        assertTrue(userMessage.contains("[1] "), userMessage);
        assertTrue(userMessage.endsWith("Question: How do I stream tokens from Ollama?"));
        String html = answer.completion().html();
        assertTrue(html.contains("<a class=\"cite\" href=\"" + answer.sources().get(0).url()), html);
        assertTrue(html.contains("[9]") && !html.contains("[9]</a>"), "out-of-range citation stays plain: " + html);
        assertFalse(html.contains("<script>"), "model output must be escaped: " + html);
    }

    @Test
    void modelFailureIsReportedNotThrown() {
        ChatModel broken = prompt -> {
            throw new IllegalStateException("connection refused");
        };
        AiService.Completion c = service(broken).complete("sys", "hi", null, 0.0);
        assertFalse(c.ok());
        assertTrue(c.error().contains("connection refused"));
    }

    @Test
    void bagOfWordsCosineBehaves() {
        assertEquals(1.0, AiService.cosine(AiService.hashedBagOfWords("a b c"), AiService.hashedBagOfWords("c b a")), 1e-9);
        assertEquals(0.0, AiService.cosine(AiService.hashedBagOfWords(""), AiService.hashedBagOfWords("x")), 1e-9);
    }

    private static AiService service(ChatModel model) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("chatModel", model);
        SemanticIndex semantic = new SemanticIndex(beans.getBeanProvider(EmbeddingModel.class), COURSE, false);
        SearchService search = new SearchService(COURSE, semantic);
        return new AiService(beans.getBeanProvider(ChatModel.class), search, semantic, "scripted");
    }
}
