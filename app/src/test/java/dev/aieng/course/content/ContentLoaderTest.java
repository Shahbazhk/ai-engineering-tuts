package dev.aieng.course.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.aieng.course.content.Course.Lesson;

/** Integrity checks over the real course content: if a lesson breaks, the build breaks. */
class ContentLoaderTest {

    static Course course;

    @BeforeAll
    static void load() {
        course = new ContentLoader(Path.of("..").toAbsolutePath().normalize()).load();
    }

    @Test
    void loadsEveryPhaseAndLesson() {
        assertFalse(course.phases().isEmpty());
        for (var phase : course.phases()) {
            assertFalse(phase.lessons().isEmpty(), phase.id() + " has no lessons");
        }
        assertEquals(course.lessons().size(), course.phases().stream().mapToInt(p -> p.lessons().size()).sum());
    }

    @Test
    void phasesAreNumberedContiguously() {
        for (int i = 0; i < course.phases().size(); i++) {
            assertEquals(i, course.phases().get(i).number(), "phase numbering gap at " + course.phases().get(i).id());
        }
    }

    @Test
    void everyLessonFollowsTheTemplate() {
        List<String> problems = new ArrayList<>();
        for (Lesson l : course.lessons().values()) {
            if (l.summary().isBlank()) {
                problems.add(l.id() + ": no summary");
            }
            if (l.html().contains("<!-- code:")) {
                problems.add(l.id() + ": unexpanded include");
            }
            if (l.html().contains("MATHPH")) {
                problems.add(l.id() + ": unrestored math placeholder");
            }
            if (l.sections().size() < 2) {
                problems.add(l.id() + ": fewer than two sections");
            }
            if (l.quiz().size() < 2) {
                problems.add(l.id() + ": fewer than two quiz questions");
            }
            l.quiz().stream().filter(q -> q.options().size() > 6 || q.why().isBlank())
                    .forEach(q -> problems.add(l.id() + ": malformed quiz question: " + q.question()));
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void prerequisitesPointBackwards() {
        List<String> ids = course.phases().stream().flatMap(p -> p.lessons().stream()).map(Lesson::id).toList();
        for (Lesson l : course.lessons().values()) {
            for (String p : l.prereqs()) {
                assertTrue(ids.indexOf(p) < ids.indexOf(l.id()), l.id() + " depends on later lesson " + p);
            }
        }
    }

    @Test
    void glossaryTermsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (var e : course.glossary()) {
            assertTrue(seen.add(e.term().toLowerCase()), "duplicate glossary term " + e.term());
            assertFalse(e.definition().isBlank(), e.term());
        }
    }

    @Test
    void includeExpandsCodeAndRegion(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("content/p00-x"));
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/A.java"), """
                class A {
                    // region hello
                    void hello() {}
                    // endregion
                    void other() {}
                }
                """);
        Files.writeString(root.resolve("content/p00-x/phase.yml"), "title: X\n");
        Files.writeString(root.resolve("content/p00-x/01-a.md"), """
                ---
                title: A
                ---
                ## Code
                <!-- code: src/A.java#hello -->
                Inline $x_i + y_j$ math and `cost is $5` code.
                """);
        Course c = new ContentLoader(root).load();
        String html = c.lesson("p00-x/01-a").html();
        assertTrue(html.contains("void hello() {}"));
        assertFalse(html.contains("void other()"));
        assertFalse(html.contains("region"));
        assertTrue(html.contains("\\(x_i + y_j\\)"), html);
        assertTrue(html.contains("<code>cost is $5</code>"), html);
    }

    @Test
    void brokenIncludeFailsFast(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("content/p00-x"));
        Files.writeString(root.resolve("content/p00-x/phase.yml"), "title: X\n");
        Files.writeString(root.resolve("content/p00-x/01-a.md"), "---\ntitle: A\n---\n<!-- code: nope/Missing.java -->\n");
        var e = assertThrows(IllegalStateException.class, () -> new ContentLoader(root).load());
        assertTrue(e.getMessage().contains("include not found"));
    }

    @Test
    void includeCannotEscapeTheRoot(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("content/p00-x"));
        Files.writeString(root.resolve("content/p00-x/phase.yml"), "title: X\n");
        Files.writeString(root.resolve("content/p00-x/01-a.md"), "---\ntitle: A\n---\n<!-- code: ../../etc/passwd -->\n");
        assertThrows(IllegalStateException.class, () -> new ContentLoader(root).load());
    }

    @Test
    void displayMathAndDollarAmountsAreHandled() {
        List<String> math = new ArrayList<>();
        String out = Markdown.protectMath("Price $5 and $10.\n$$\\sum_i x_i$$\n```\necho $HOME $PATH\n```\n", math);
        assertEquals(1, math.size(), out);
        assertTrue(math.get(0).startsWith("\\["));
        assertTrue(out.contains("Price $5 and $10."));
        assertTrue(out.contains("echo $HOME $PATH"));
        assertNotNull(course);
    }
}
