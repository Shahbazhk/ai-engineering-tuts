package dev.aieng.p12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EvalTest {

    @Test
    void retrievalMetricsOnAWorkedExample() {
        List<String> ranked = List.of("d3", "d1", "d7", "d2");
        Set<String> relevant = Set.of("d1", "d2");
        assertEquals(0.5, RetrievalMetrics.recallAtK(ranked, relevant, 2), 1e-12, "only d1 in the top 2");
        assertEquals(1.0, RetrievalMetrics.recallAtK(ranked, relevant, 4), 1e-12);
        assertEquals(0.5, RetrievalMetrics.precisionAtK(ranked, relevant, 2), 1e-12);
        assertEquals(0.5, RetrievalMetrics.reciprocalRank(ranked, relevant), 1e-12, "first hit at rank 2");
        assertEquals(1.0, RetrievalMetrics.ndcgAtK(List.of("a", "b"), Map.of("a", 3, "b", 1), 2), 1e-12, "ideal order");
        assertTrue(RetrievalMetrics.ndcgAtK(List.of("b", "a"), Map.of("a", 3, "b", 1), 2) < 1.0, "swapped order is penalised");
    }

    @Test
    void textMetricsNormaliseAndGivePartialCredit() {
        assertTrue(TextMetrics.exactMatch("The Port is 8443.", "port is 8443"));
        assertEquals(1.0, TextMetrics.tokenF1("port 8443", "8443 port"), 1e-12);
        double f1 = TextMetrics.tokenF1("use proxy port 8443 on windows", "port 8443");
        assertEquals(0.5, f1, 1e-12, "precision 2/6, recall 2/2 -> F1 0.5: partial credit");
        assertEquals(0.0, TextMetrics.tokenF1("no idea", "port 8443"), 1e-12);
        assertEquals(0.5, TextMetrics.keyFactRecall("Install the client from Self Service", List.of("self service", "8443")), 1e-12);
        assertTrue(TextMetrics.exactMatch("Café", "cafe"), "accents are normalised");
    }

    @Test
    void judgeParsesStructuredGradesAndRejectsGarbage() {
        LlmJudge judge = new LlmJudge(prompt -> {
            assertTrue(prompt.contains("RUBRIC:\nMentions the port number"));
            return "Sure! {\"score\": 4, \"reason\": \"Correct but terse.\"}";
        });
        LlmJudge.Grade g = judge.grade("Which port?", "8443", "Mentions the port number");
        assertEquals(4, g.score());
        assertTrue(g.passes(4));
        assertThrows(IllegalArgumentException.class, () -> LlmJudge.parse("{\"score\": 9}"));
    }

    @Test
    void pairwiseJudgingCancelsPositionBias() {
        // A biased judge that always prefers whatever is shown first.
        LlmJudge biased = new LlmJudge(prompt -> "1");
        assertEquals(LlmJudge.Preference.TIE, biased.compare("q", "short", "long and detailed"));
        // A consistent judge that prefers the response mentioning 8443, wherever it appears.
        LlmJudge consistent = new LlmJudge(prompt -> {
            String one = prompt.substring(prompt.indexOf("RESPONSE 1:"), prompt.indexOf("RESPONSE 2:"));
            return one.contains("8443") ? "1" : "2";
        });
        assertEquals(LlmJudge.Preference.A, consistent.compare("q", "port 8443", "no idea"));
        assertEquals(LlmJudge.Preference.B, consistent.compare("q", "no idea", "port 8443"));
    }

    @Test
    void ragMetricsSeparateRetrievalFromGeneration() {
        String context = "Set the proxy port to 8443. Install the client from the software centre.";
        String faithful = "Set the proxy port to 8443.";
        String hallucinated = "Set the proxy port to 8443. The VPN also requires a hardware token from security.";
        assertEquals(1.0, RagMetrics.faithfulness(faithful, context, RagMetrics::lexicalSupport), 1e-12);
        assertEquals(0.5, RagMetrics.faithfulness(hallucinated, context, RagMetrics::lexicalSupport), 1e-12);

        List<String> chunks = List.of("proxy port 8443", "canteen menu", "client from software centre");
        assertEquals(2.0 / 3, RagMetrics.contextPrecision(chunks, (c, q) -> !c.contains("canteen"), "vpn?"), 1e-12);
        assertEquals(0.5, RagMetrics.contextRecall(chunks, List.of("8443", "hardware token")), 1e-12);
    }

    @Test
    void evalRunnerGatesOnThresholdAndBaseline() {
        List<EvalRunner.Case> dataset = List.of(
                new EvalRunner.Case("c1", "vpn port?", "8443", List.of("vpn")),
                new EvalRunner.Case("c2", "mac install?", "self service", List.of("vpn", "mac")),
                new EvalRunner.Case("c3", "canteen?", "I don't know", List.of("refusal")),
                new EvalRunner.Case("c4", "windows install?", "software centre", List.of("vpn")));
        java.util.function.Function<String, String> system = q -> switch (q) {
            case "vpn port?" -> "Use port 8443.";
            case "mac install?" -> "Install from Self Service.";
            case "canteen?" -> "The canteen serves pizza."; // should have refused
            default -> "Use the software centre.";
        };
        java.util.function.ToDoubleBiFunction<String, EvalRunner.Case> metric =
                (out, c) -> TextMetrics.keyFactRecall(out, List.of(c.expected()));

        EvalRunner.Report ok = EvalRunner.run(dataset, system, metric, 0.7, 0.70, 0.05);
        assertEquals(0.75, ok.mean(), 1e-12);
        assertTrue(ok.passed(), ok.failures().toString());
        assertEquals(0.0, ok.byTag().get("refusal"), 1e-12, "the failure is visible per category");
        assertTrue(ok.ciLow() <= ok.mean() && ok.mean() <= ok.ciHigh());

        EvalRunner.Report regressed = EvalRunner.run(dataset, system, metric, 0.7, 0.90, 0.05);
        assertFalse(regressed.passed());
        assertTrue(regressed.failures().getFirst().startsWith("regression"));
    }
}
