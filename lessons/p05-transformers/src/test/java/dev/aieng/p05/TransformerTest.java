package dev.aieng.p05;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class TransformerTest {

    @Test
    void attentionWeightsAreDistributionsAndCausalMaskHidesTheFuture() {
        Random rnd = new Random(1);
        double[][] q = M.random(4, 8, 1, rnd), k = M.random(4, 8, 1, rnd), v = M.random(4, 8, 1, rnd);
        Attention.Result r = Attention.scaledDotProduct(q, k, v, true);
        for (int i = 0; i < 4; i++) {
            double sum = 0;
            for (int j = 0; j < 4; j++) {
                sum += r.weights()[i][j];
                if (j > i) {
                    assertEquals(0, r.weights()[i][j], "position " + i + " must not see " + j);
                }
            }
            assertEquals(1, sum, 1e-12);
        }
        assertArrayEquals(v[0], r.output()[0], 1e-12, "the first token can only attend to itself");
    }

    @Test
    void attentionIsPermutationEquivariantWithoutPositions() {
        Random rnd = new Random(2);
        double[][] x = M.random(3, 4, 1, rnd);
        double[][] swapped = {x[2], x[1], x[0]};
        double[][] a = Attention.scaledDotProduct(x, x, x, false).output();
        double[][] b = Attention.scaledDotProduct(swapped, swapped, swapped, false).output();
        assertArrayEquals(a[0], b[2], 1e-12);
        assertArrayEquals(a[2], b[0], 1e-12);
    }

    @Test
    void ropeEncodesRelativePositionAndPreservesLength() {
        Random rnd = new Random(3);
        double[] q = rnd.doubles(8).toArray(), k = rnd.doubles(8).toArray();
        double near = dot(Positional.rope(q, 5), Positional.rope(k, 3));
        double shifted = dot(Positional.rope(q, 105), Positional.rope(k, 103));
        assertEquals(near, shifted, 1e-9, "same offset (2) -> same score, wherever it occurs");
        assertEquals(norm(q), norm(Positional.rope(q, 42)), 1e-12);
        double[][] pe = Positional.sinusoidal(10, 6);
        assertEquals(0, pe[0][0], 1e-12);
        assertEquals(1, pe[0][1], 1e-12);
        assertEquals(Math.sin(1), pe[1][0], 1e-12);
    }

    @Test
    void gptIsCausalAndShapesAreRight() {
        TinyGpt gpt = new TinyGpt(new TinyGpt.Config(20, 16, 4, 2, 32), 1);
        double[][] a = gpt.forward(new int[] {1, 2, 3, 4});
        double[][] b = gpt.forward(new int[] {1, 2, 3, 9});
        assertEquals(4, a.length);
        assertEquals(20, a[0].length);
        assertArrayEquals(a[2], b[2], 1e-12, "changing token 4 cannot affect predictions at position 3");
        assertNotEquals(a[3][0], b[3][0]);
        assertThrows(IllegalArgumentException.class, () -> gpt.forward(new int[33]));
    }

    @Test
    void kvCacheGivesIdenticalResultsToFullRecomputation() {
        TinyGpt gpt = new TinyGpt(new TinyGpt.Config(30, 24, 3, 3, 64), 5);
        int[] prompt = {3, 1, 4, 1, 5};
        var cached = gpt.generate(prompt, 20, Sampling::greedy, true);
        var uncached = gpt.generate(prompt, 20, Sampling::greedy, false);
        assertEquals(uncached, cached);

        TinyGpt.KvCache cache = new TinyGpt.KvCache(3);
        double[] last = null;
        for (int t : prompt) {
            last = gpt.next(t, cache);
        }
        assertArrayEquals(gpt.forward(prompt)[4], last, 1e-9);
        assertEquals(5, cache.length());
    }

    @Test
    void parameterCountMatchesTheFormula() {
        TinyGpt gpt = new TinyGpt(new TinyGpt.Config(100, 64, 4, 2, 128), 1);
        long d = 64;
        long expected = 100 * d + 128 * d + 2 * (12 * d * d + 9 * d) + 2 * d;
        assertEquals(expected, gpt.parameterCount());
    }

    @Test
    void samplingStrategies() {
        double[] logits = {1.0, 3.0, 2.0, -1.0};
        Random rnd = new Random(4);
        assertEquals(1, Sampling.greedy(logits));
        assertEquals(1, Sampling.sample(logits, 0, 0, 1, rnd), "temperature 0 is greedy");
        for (int i = 0; i < 200; i++) {
            assertEquals(1, Sampling.sample(logits, 1.0, 1, 1.0, rnd), "top-k 1 is greedy");
            int s = Sampling.sample(logits, 1.0, 2, 1.0, rnd);
            assertTrue(s == 1 || s == 2, "top-k 2 only allows the two best tokens");
            assertEquals(1, Sampling.sample(logits, 1.0, 0, 0.5, rnd), "top-p 0.5: token 1 alone has 66% mass");
        }
        int[] counts = new int[4];
        for (int i = 0; i < 20_000; i++) {
            counts[Sampling.sample(logits, 1.0, 0, 1.0, rnd)]++;
        }
        double[] p = M.softmax(logits);
        for (int i = 0; i < 4; i++) {
            assertEquals(p[i], counts[i] / 20_000.0, 0.015);
        }
    }

    @Test
    void neuralBigramConvergesToTheCountingSolution() {
        BigramLm lm = new BigramLm(BigramLm.CORPUS);
        int[] ids = lm.encode(BigramLm.CORPUS);
        double mleLoss = BigramLm.nll(lm.countModel(ids, 0), ids);
        double smoothedLoss = BigramLm.nll(lm.countModel(ids, 1), ids);
        double neuralLoss = BigramLm.nll(lm.neuralModel(ids, 300, 1), ids);
        assertTrue(mleLoss < Math.log(lm.vocab()) - 1, "much better than uniform");
        assertTrue(neuralLoss >= mleLoss - 1e-9, "nothing beats the MLE on its own training data");
        assertEquals(mleLoss, neuralLoss, 0.05, "gradient descent finds (nearly) the counting solution");
        assertTrue(smoothedLoss > mleLoss, "smoothing gives up some training fit");
        assertEquals("spring", lm.decode(java.util.Arrays.stream(lm.encode("spring")).boxed().toList()));
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    private static double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }
}
