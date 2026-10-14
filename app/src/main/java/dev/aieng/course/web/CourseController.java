package dev.aieng.course.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import dev.aieng.course.content.Course;
import dev.aieng.course.content.Course.InterviewQuestion;
import dev.aieng.course.content.Course.Lesson;
import dev.aieng.course.content.Course.Phase;
import dev.aieng.course.content.Course.QuizQuestion;
import dev.aieng.course.progress.ProgressService;
import dev.aieng.course.search.SearchService;

@Controller
class CourseController {

    record QuizFeedback(QuizQuestion question, Integer chosen, boolean correct) {
    }

    record BankEntry(Lesson lesson, InterviewQuestion item) {
    }

    private final Course course;
    private final ProgressService progress;
    private final SearchService search;
    private final List<Lesson> ordered;

    CourseController(Course course, ProgressService progress, SearchService search) {
        this.course = course;
        this.progress = progress;
        this.search = search;
        this.ordered = course.phases().stream().flatMap(p -> p.lessons().stream()).toList();
    }

    @ControllerAdvice
    static class Globals {
        private final Course course;
        private final ProgressService progress;

        Globals(Course course, ProgressService progress) {
            this.course = course;
            this.progress = progress;
        }

        @ModelAttribute("nav")
        List<Phase> nav() {
            return course.phases();
        }

        @ModelAttribute("summary")
        ProgressService.Summary summary() {
            return progress.summary(course);
        }
    }

    @GetMapping("/")
    String roadmap(Model model) {
        Set<String> done = progress.completed();
        model.addAttribute("phases", course.phases());
        model.addAttribute("done", done);
        model.addAttribute("phaseDone", course.phases().stream().collect(java.util.stream.Collectors.toMap(Phase::id,
                p -> (int) p.lessons().stream().filter(l -> done.contains(l.id())).count())));
        model.addAttribute("next", ordered.stream().filter(l -> !done.contains(l.id())).findFirst().orElse(null));
        model.addAttribute("totalHours", Math.round(ordered.stream().mapToInt(Lesson::minutes).sum() / 60.0));
        return "roadmap";
    }

    @GetMapping("/phases/{phaseId}")
    String phase(@PathVariable String phaseId, Model model) {
        Phase phase = course.phase(phaseId);
        if (phase == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("phase", phase);
        model.addAttribute("done", progress.completed());
        model.addAttribute("scores", progress.bestScores());
        return "phase";
    }

    @GetMapping("/lessons/{phaseId}/{slug}")
    String lesson(@PathVariable String phaseId, @PathVariable String slug, Model model) {
        Lesson lesson = lessonOr404(phaseId, slug);
        int i = ordered.indexOf(lesson);
        model.addAttribute("lesson", lesson);
        model.addAttribute("phase", course.phase(phaseId));
        model.addAttribute("prev", i > 0 ? ordered.get(i - 1) : null);
        model.addAttribute("next", i < ordered.size() - 1 ? ordered.get(i + 1) : null);
        model.addAttribute("prereqs", lesson.prereqs().stream().map(course::lesson).toList());
        model.addAttribute("completed", progress.isCompleted(lesson.id()));
        model.addAttribute("score", progress.bestScores().get(lesson.id()));
        return "lesson";
    }

    @PostMapping("/lessons/{phaseId}/{slug}/complete")
    String toggleComplete(@PathVariable String phaseId, @PathVariable String slug, Model model) {
        Lesson lesson = lessonOr404(phaseId, slug);
        model.addAttribute("lesson", lesson);
        model.addAttribute("completed", progress.toggle(lesson.id()));
        return "fragments :: complete-button";
    }

    @PostMapping("/lessons/{phaseId}/{slug}/quiz")
    String gradeQuiz(@PathVariable String phaseId, @PathVariable String slug, @RequestParam Map<String, String> form,
                     Model model) {
        Lesson lesson = lessonOr404(phaseId, slug);
        List<QuizFeedback> feedback = new ArrayList<>();
        int score = 0;
        for (int q = 0; q < lesson.quiz().size(); q++) {
            QuizQuestion question = lesson.quiz().get(q);
            Integer chosen = parse(form.get("q" + q));
            boolean correct = chosen != null && chosen == question.answer();
            score += correct ? 1 : 0;
            feedback.add(new QuizFeedback(question, chosen, correct));
        }
        progress.recordQuiz(lesson.id(), score, lesson.quiz().size());
        model.addAttribute("feedback", feedback);
        model.addAttribute("score", new ProgressService.QuizScore(score, lesson.quiz().size()));
        return "fragments :: quiz-result";
    }

    @GetMapping("/search")
    String search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "KEYWORD") SearchService.Mode mode,
                  Model model) {
        model.addAttribute("q", q);
        model.addAttribute("mode", mode);
        model.addAttribute("modes", SearchService.Mode.values());
        model.addAttribute("hits", search.search(q, mode, 20));
        return "search";
    }

    @GetMapping("/glossary")
    String glossary(Model model) {
        model.addAttribute("entries", course.glossary());
        model.addAttribute("lessons", course.lessons());
        return "glossary";
    }

    @GetMapping("/interview")
    String interview(@RequestParam(defaultValue = "") String phase, @RequestParam(defaultValue = "") String level,
                     Model model) {
        List<BankEntry> bank = ordered.stream()
                .filter(l -> phase.isEmpty() || l.phaseId().equals(phase))
                .flatMap(l -> l.interview().stream().map(q -> new BankEntry(l, q)))
                .filter(e -> level.isEmpty() || e.item().level().equalsIgnoreCase(level))
                .toList();
        model.addAttribute("bank", bank);
        model.addAttribute("phase", phase);
        model.addAttribute("level", level);
        model.addAttribute("levels", List.of("mid", "senior", "staff", "architect"));
        return "interview";
    }

    @GetMapping("/case-studies")
    String caseStudies(Model model) {
        model.addAttribute("studies", ordered.stream().filter(l -> l.tags().contains("case-study"))
                .sorted(Comparator.comparing(Lesson::id)).toList());
        model.addAttribute("done", progress.completed());
        return "case-studies";
    }

    @PostMapping("/progress/reset")
    String reset() {
        progress.reset();
        return "redirect:/";
    }

    private Lesson lessonOr404(String phaseId, String slug) {
        Lesson lesson = course.lesson(phaseId + "/" + slug);
        if (lesson == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return lesson;
    }

    private static Integer parse(String s) {
        try {
            return s == null ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
