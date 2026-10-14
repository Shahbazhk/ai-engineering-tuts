package dev.aieng.course.content;

import java.util.List;
import java.util.Map;

/** Immutable, fully parsed course. Rebuilt from files on startup. */
public record Course(List<Phase> phases, Map<String, Lesson> lessons, List<GlossaryEntry> glossary) {

    public record Phase(String id, int number, String title, String summary, String hours, List<Lesson> lessons) {
        public int minutes() {
            return lessons.stream().mapToInt(Lesson::minutes).sum();
        }
    }

    public record Lesson(String id, String phaseId, String slug, int number, String title, String summary,
                         int minutes, List<String> prereqs, List<String> tags, String html, List<Section> sections,
                         List<QuizQuestion> quiz, List<InterviewQuestion> interview) {
        public String url() {
            return "/lessons/" + id;
        }
    }

    /** A heading-delimited slice of a lesson; the unit of search and retrieval. */
    public record Section(String lessonId, String anchor, String heading, String text) {
    }

    public record QuizQuestion(String question, List<String> options, int answer, String why) {
    }

    public record InterviewQuestion(String question, String answer, String level) {
    }

    public record GlossaryEntry(String term, String definition, String lesson) {
    }

    public Lesson lesson(String id) {
        return lessons.get(id);
    }

    public Phase phase(String id) {
        return phases.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }
}
