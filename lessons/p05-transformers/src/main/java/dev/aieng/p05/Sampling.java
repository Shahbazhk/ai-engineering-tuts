package dev.aieng.p05;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * Decoding strategies: how the next token is chosen from the model's logits. These are the temperature, top_k
 * and top_p parameters of every LLM API.
 */
public final class Sampling {

    private Sampling() {
    }

    public static int greedy(double[] logits) {
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) {
                best = i;
            }
        }
        return best;
    }

    // region sample
    /**
     * Pipeline: logits / temperature -> keep top-k -> keep smallest set whose probability >= top-p ->
     * renormalise -> sample. topK <= 0 and topP >= 1 disable those filters; temperature 0 means greedy.
     */
    public static int sample(double[] logits, double temperature, int topK, double topP, Random rnd) {
        if (temperature == 0) {
            return greedy(logits);
        }
        double[] scaled = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            scaled[i] = logits[i] / temperature;
        }
        double[] p = M.softmax(scaled);
        Integer[] order = new Integer[p.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> p[i]).reversed());

        int keep = topK > 0 ? Math.min(topK, p.length) : p.length;
        double cumulative = 0;
        for (int r = 0; r < keep; r++) {
            cumulative += p[order[r]];
            if (cumulative >= topP) {
                keep = r + 1; // nucleus: the smallest prefix reaching top-p probability mass
                break;
            }
        }
        double mass = 0;
        for (int r = 0; r < keep; r++) {
            mass += p[order[r]];
        }
        double u = rnd.nextDouble() * mass;
        double running = 0;
        for (int r = 0; r < keep; r++) {
            running += p[order[r]];
            if (u < running) {
                return order[r];
            }
        }
        return order[keep - 1];
    }
    // endregion
}
