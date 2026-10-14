package dev.aieng.course.progress;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.aieng.course.content.Course;

/** Single-learner progress store. ponytail: no user accounts; add a user_id column if this ever goes multi-user. */
@Service
public class ProgressService {

    public record QuizScore(int score, int total) {
        public int percent() {
            return total == 0 ? 0 : Math.round(100f * score / total);
        }
    }

    public record Summary(int completed, int total, int percent) {
    }

    private final JdbcClient jdbc;

    ProgressService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> completed() {
        return new HashSet<>(jdbc.sql("SELECT lesson_id FROM lesson_progress").query(String.class).list());
    }

    public boolean isCompleted(String lessonId) {
        return jdbc.sql("SELECT COUNT(*) FROM lesson_progress WHERE lesson_id = ?").param(lessonId)
                .query(Integer.class).single() > 0;
    }

    /** Flips completion and returns the new state. */
    @Transactional
    public boolean toggle(String lessonId) {
        if (jdbc.sql("DELETE FROM lesson_progress WHERE lesson_id = ?").param(lessonId).update() > 0) {
            return false;
        }
        jdbc.sql("INSERT INTO lesson_progress (lesson_id, completed_at) VALUES (?, ?)")
                .params(lessonId, Timestamp.from(Instant.now())).update();
        return true;
    }

    public void recordQuiz(String lessonId, int score, int total) {
        jdbc.sql("INSERT INTO quiz_attempt (lesson_id, score, total, taken_at) VALUES (?, ?, ?, ?)")
                .params(lessonId, score, total, Timestamp.from(Instant.now())).update();
    }

    /** Best attempt per lesson, by percentage. */
    public Map<String, QuizScore> bestScores() {
        record Row(String lessonId, int score, int total) {
        }
        List<Row> rows = jdbc.sql("SELECT lesson_id, score, total FROM quiz_attempt").query(Row.class).list();
        return rows.stream().collect(Collectors.toMap(Row::lessonId, r -> new QuizScore(r.score(), r.total()),
                (a, b) -> a.percent() >= b.percent() ? a : b));
    }

    public Summary summary(Course course) {
        Set<String> done = completed();
        done.retainAll(course.lessons().keySet());
        int total = course.lessons().size();
        return new Summary(done.size(), total, total == 0 ? 0 : Math.round(100f * done.size() / total));
    }

    @Transactional
    public void reset() {
        jdbc.sql("DELETE FROM lesson_progress").update();
        jdbc.sql("DELETE FROM quiz_attempt").update();
    }
}
