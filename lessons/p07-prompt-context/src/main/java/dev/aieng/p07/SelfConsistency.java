package dev.aieng.p07;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-consistency (Wang et al., 2022): sample several reasoning paths at temperature > 0, extract each final
 * answer, and take the majority. Costs N calls; improves accuracy on reasoning tasks and yields a free
 * confidence signal (the agreement rate).
 */
public final class SelfConsistency {

    public record Vote(String answer, double agreement, List<String> all) {
    }

    private static final Pattern FINAL = Pattern.compile("(?im)^\\s*final answer\\s*:\\s*(.+?)\\s*$");

    private SelfConsistency() {
    }

    // region vote
    public static Vote vote(Supplier<String> sampleReasoning, int samples) {
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            extractFinalAnswer(sampleReasoning.get()).ifPresent(a -> answers.add(normalise(a)));
        }
        Map<String, Integer> counts = new HashMap<>();
        answers.forEach(a -> counts.merge(a, 1, Integer::sum));
        String best = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        double agreement = answers.isEmpty() ? 0 : (double) counts.getOrDefault(best, 0) / samples;
        return new Vote(best, agreement, answers);
    }

    /** Chain-of-thought prompts end with a fixed "Final answer: ..." line so the answer is machine-readable. */
    public static Optional<String> extractFinalAnswer(String completion) {
        Matcher m = FINAL.matcher(completion);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return Optional.ofNullable(last);
    }
    // endregion

    static String normalise(String answer) {
        return answer.strip().replaceAll("[.\\s]+$", "").toLowerCase();
    }
}
