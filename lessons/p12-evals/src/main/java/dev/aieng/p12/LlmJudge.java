package dev.aieng.p12;

import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM-as-judge: a (usually stronger) model grades outputs against a rubric. Useful where no reference answer can
 * be matched mechanically - tone, helpfulness, faithfulness. Judges have biases (position, verbosity, self-
 * preference), so rubrics are explicit, outputs are structured, and pairwise comparisons are run in both orders.
 */
public final class LlmJudge {

    public record Grade(int score, String reason) {
        public boolean passes(int threshold) {
            return score >= threshold;
        }
    }

    public enum Preference { A, B, TIE }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Function<String, String> judgeModel;

    public LlmJudge(Function<String, String> judgeModel) {
        this.judgeModel = judgeModel;
    }

    // region grade
    public Grade grade(String question, String answer, String rubric) {
        String prompt = """
                You are a strict evaluator. Grade the ANSWER to the QUESTION using the RUBRIC.
                Score 1-5 where 5 fully satisfies the rubric and 1 fails it.
                Reply with JSON only: {"score": <1-5>, "reason": "<one sentence>"}

                RUBRIC:
                %s

                QUESTION:
                %s

                ANSWER:
                %s""".formatted(rubric, question, answer);
        return parse(judgeModel.apply(prompt));
    }

    static Grade parse(String raw) {
        String json = raw.substring(Math.max(0, raw.indexOf('{')), raw.lastIndexOf('}') + 1); // tolerate chatter
        JsonNode node = JSON.readTree(json);
        int score = node.path("score").asInt(-1);
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("judge returned an invalid score: " + raw);
        }
        return new Grade(score, node.path("reason").asString(""));
    }
    // endregion

    // region pairwise
    /**
     * Pairwise comparison run twice with the answers swapped. Judges favour the first position surprisingly
     * often; only a preference that survives the swap counts, otherwise it is a tie.
     */
    public Preference compare(String question, String answerA, String answerB) {
        String first = ask(question, answerA, answerB);   // A shown first
        String second = ask(question, answerB, answerA);  // B shown first
        if (first.equals("1") && second.equals("2")) {
            return Preference.A;
        }
        if (first.equals("2") && second.equals("1")) {
            return Preference.B;
        }
        return Preference.TIE; // inconsistent verdicts (position bias) or unparseable answers
    }
    // endregion

    private String ask(String question, String one, String two) {
        return judgeModel.apply("""
                Which response answers the question better? Reply with only 1 or 2.
                QUESTION: %s
                RESPONSE 1: %s
                RESPONSE 2: %s""".formatted(question, one, two)).strip();
    }
}
