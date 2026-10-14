package dev.aieng.course.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import dev.aieng.course.content.Course;
import dev.aieng.course.content.Course.Lesson;
import dev.aieng.course.progress.ProgressService;

/** Boots the whole app offline (no model provider) and renders every page. */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.datasource.url=jdbc:h2:mem:webtest;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class WebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    Course course;
    @Autowired
    ProgressService progress;

    @BeforeEach
    void clean() {
        progress.reset();
    }

    @Test
    void staticPagesRender() throws Exception {
        for (String path : new String[] {"/", "/search", "/search?q=attention&mode=KEYWORD", "/tutor", "/playground",
                "/glossary", "/interview", "/interview?level=senior", "/case-studies"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void everyPhaseAndLessonRenders() throws Exception {
        for (var phase : course.phases()) {
            mvc.perform(get("/phases/" + phase.id())).andExpect(status().isOk())
                    .andExpect(content().string(containsString(escape(phase.title()))));
            for (Lesson l : phase.lessons()) {
                mvc.perform(get(l.url())).andExpect(status().isOk())
                        .andExpect(content().string(containsString(escape(l.title()))));
            }
        }
    }

    @Test
    void unknownLessonIs404() throws Exception {
        mvc.perform(get("/lessons/p99-nope/01-nothing")).andExpect(status().isNotFound());
        mvc.perform(get("/phases/p99-nope")).andExpect(status().isNotFound());
    }

    @Test
    void completingALessonUpdatesProgress() throws Exception {
        Lesson first = course.phases().get(0).lessons().get(0);
        mvc.perform(post(first.url() + "/complete")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Completed")));
        mvc.perform(get("/")).andExpect(content().string(containsString("1/" + course.phases().get(0).lessons().size() + " done")));
        mvc.perform(post(first.url() + "/complete")).andExpect(content().string(containsString("Mark lesson complete")));
    }

    @Test
    void quizIsGradedAndRecorded() throws Exception {
        Lesson first = course.phases().get(0).lessons().get(0);
        var post = post(first.url() + "/quiz");
        for (int i = 0; i < first.quiz().size(); i++) {
            post.param("q" + i, String.valueOf(first.quiz().get(i).answer()));
        }
        int n = first.quiz().size();
        mvc.perform(post).andExpect(status().isOk())
                .andExpect(content().string(containsString("Score: " + n + " / " + n + " (100%)")));
        mvc.perform(post(first.url() + "/quiz")).andExpect(content().string(containsString("Not answered.")));
        mvc.perform(get(first.url())).andExpect(content().string(containsString("Best score so far: " + n + "/" + n)));
    }

    @Test
    void tutorDegradesToSourcesWithoutAModel() throws Exception {
        mvc.perform(post("/tutor").param("question", "How do I stream tokens from Ollama?"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not reachable")))
                .andExpect(content().string(containsString("/lessons/")))
                .andExpect(content().string(containsString("Context passages")));
    }

    @Test
    void similarityFallsBackToBagOfWords() throws Exception {
        mvc.perform(post("/playground/similarity").param("texts", "spring boot\nspring boot\ncats"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Offline fallback")))
                .andExpect(content().string(containsString("1.00")));
    }

    @Test
    void searchEscapesUserInput() throws Exception {
        mvc.perform(get("/search").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    private static String escape(String s) {
        // Thymeleaf th:text escapes only the HTML-significant characters
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
