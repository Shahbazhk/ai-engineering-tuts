package dev.aieng.course.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import dev.aieng.course.ai.AiService;
import dev.aieng.course.search.SearchService;
import dev.aieng.course.search.SemanticIndex;

/** AI tutor (RAG over the course) and the playground pages. HTMX posts return fragments. */
@Controller
class AiController {

    private static final int MAX_INPUT = 4000;
    private static final int MAX_SIMILARITY_TEXTS = 12;

    private final AiService ai;
    private final SemanticIndex semantic;
    private final SearchService search;

    AiController(AiService ai, SemanticIndex semantic, SearchService search) {
        this.ai = ai;
        this.semantic = semantic;
        this.search = search;
    }

    @GetMapping("/tutor")
    String tutor(Model model) {
        status(model);
        return "tutor";
    }

    @PostMapping("/tutor")
    String ask(@RequestParam String question, Model model) {
        model.addAttribute("answer", ai.ask(clip(question), 5));
        return "fragments :: tutor-answer";
    }

    @GetMapping("/playground")
    String playground(Model model) {
        status(model);
        model.addAttribute("system", "You are a concise senior Java architect.");
        model.addAttribute("defaultTexts", String.join("\n", List.of(
                "Spring Boot auto-configures beans from the classpath.",
                "Spring Boot sets up components automatically based on dependencies.",
                "A transformer uses self-attention over token embeddings.",
                "The cat sat on the mat.")));
        return "playground";
    }

    @PostMapping("/playground/prompt")
    String prompt(@RequestParam(defaultValue = "") String system, @RequestParam String user,
                  @RequestParam(defaultValue = "") String modelA, @RequestParam(defaultValue = "") String modelB,
                  @RequestParam(defaultValue = "0.7") double temperature, Model model) {
        String sys = clip(system);
        String usr = clip(user);
        double t = Math.max(0, Math.min(2, temperature));
        model.addAttribute("a", ai.complete(sys, usr, modelA, t));
        model.addAttribute("b", modelB.isBlank() ? null : ai.complete(sys, usr, modelB, t));
        return "fragments :: prompt-result";
    }

    @PostMapping("/playground/similarity")
    String similarity(@RequestParam String texts, Model model) {
        List<String> lines = Arrays.stream(clip(texts).split("\\R")).map(String::strip).filter(s -> !s.isEmpty())
                .limit(MAX_SIMILARITY_TEXTS).toList();
        model.addAttribute("sim", ai.similarity(lines));
        return "fragments :: similarity-result";
    }

    @PostMapping("/playground/rag")
    String rag(@RequestParam String question, @RequestParam(defaultValue = "5") int k, Model model) {
        model.addAttribute("answer", ai.ask(clip(question), Math.max(1, Math.min(10, k))));
        model.addAttribute("inspect", true);
        return "fragments :: tutor-answer";
    }

    @PostMapping("/admin/reindex")
    String reindex() {
        semantic.rebuildAsync();
        return "redirect:/playground";
    }

    private void status(Model model) {
        model.addAttribute("chatAvailable", ai.chatAvailable());
        model.addAttribute("provider", ai.provider());
        model.addAttribute("indexState", semantic.state());
        model.addAttribute("indexError", semantic.error());
        model.addAttribute("sections", search.indexedSections());
    }

    private static String clip(String s) {
        return s.length() > MAX_INPUT ? s.substring(0, MAX_INPUT) : s;
    }
}
