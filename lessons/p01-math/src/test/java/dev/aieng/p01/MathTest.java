package dev.aieng.p01;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class MathTest {

    @Test
    void vectorBasics() {
        double[] a = {3, 4};
        double[] b = {4, 3};
        assertEquals(24, Vectors.dot(a, b));
        assertEquals(5, Vectors.norm(a));
        assertEquals(24.0 / 25, Vectors.cosine(a, b), 1e-12);
        assertEquals(Math.sqrt(2), Vectors.euclidean(a, b), 1e-12);
        assertEquals(1, Vectors.norm(Vectors.normalize(a)), 1e-12);
        assertEquals(-1, Vectors.cosine(a, Vectors.scale(a, -2)), 1e-12, "opposite direction");
        assertThrows(IllegalArgumentException.class, () -> Vectors.dot(a, new double[3]));
    }

    @Test
    void normalisedDotEqualsCosine() {
        Random rnd = new Random(1);
        double[] a = rnd.doubles(64).toArray();
        double[] b = rnd.doubles(64).toArray();
        assertEquals(Vectors.cosine(a, b), Vectors.dot(Vectors.normalize(a), Vectors.normalize(b)), 1e-12);
    }

    @Test
    void matrixMultiplyMatchesHandComputation() {
        Matrix a = Matrix.of(new double[][] {{1, 2, 3}, {4, 5, 6}});
        Matrix b = Matrix.of(new double[][] {{7, 8}, {9, 10}, {11, 12}});
        assertEquals(Matrix.of(new double[][] {{58, 64}, {139, 154}}), a.multiply(b));
        assertEquals(a, a.multiply(Matrix.identity(3)));
        assertArrayEquals(new double[] {14, 32}, a.apply(new double[] {1, 2, 3}));
        assertEquals(a, a.transpose().transpose());
        assertThrows(IllegalArgumentException.class, () -> a.multiply(a));
    }

    @Test
    void cacheFriendlyMultiplyMatchesNaive() {
        Random rnd = new Random(7);
        Matrix a = Matrix.random(17, 23, rnd);
        Matrix b = Matrix.random(23, 11, rnd);
        assertArrayEquals(a.multiplyNaive(b).data(), a.multiply(b).data(), 1e-9);
    }

    @Test
    void numericDerivativesAreAccurate() {
        assertEquals(Math.cos(1.0), Gradients.derivative(Math::sin, 1.0), 1e-8);
        double[] grad = Gradients.gradient(p -> p[0] * p[0] + 3 * p[0] * p[1], new double[] {2, 5});
        assertArrayEquals(new double[] {2 * 2 + 3 * 5, 3 * 2}, grad, 1e-6);
    }

    @Test
    void chainRuleMatchesNumericGradient() {
        double b = 0.3;
        double x = 1.7;
        double w = -0.4;
        double numeric = Gradients.derivative(ww -> Gradients.sigmoid(ww * x + b), w);
        assertEquals(numeric, Gradients.sigmoidNeuronGradW(w, b, x), 1e-8);
        assertTrue(Gradients.relativeError(new double[] {numeric}, new double[] {Gradients.sigmoidNeuronGradW(w, b, x)}) < 1e-6);
    }

    @Test
    void softmaxIsAStableProbabilityDistribution() {
        double[] p = Information.softmax(new double[] {1000, 1001, 1002});
        assertEquals(1, p[0] + p[1] + p[2], 1e-12);
        assertTrue(p[2] > p[1] && p[1] > p[0]);
        double[] cold = Information.softmax(new double[] {1, 2}, 0.1);
        double[] hot = Information.softmax(new double[] {1, 2}, 10);
        assertTrue(cold[1] > 0.99, "low temperature is nearly greedy");
        assertTrue(Math.abs(hot[1] - 0.5) < 0.05, "high temperature is nearly uniform");
    }

    @Test
    void entropyAndCrossEntropy() {
        assertEquals(Math.log(4), Information.entropy(new double[] {.25, .25, .25, .25}), 1e-12);
        assertEquals(0, Information.entropy(new double[] {1, 0}), 1e-12);
        // Uniform logits over 4 classes -> loss = log 4, perplexity = 4.
        double ce = Information.crossEntropy(new double[] {0, 0, 0, 0}, 2);
        assertEquals(Math.log(4), ce, 1e-12);
        assertEquals(4, Information.perplexity(ce), 1e-9);
        assertTrue(Information.crossEntropy(new double[] {0, 0, 10, 0}, 2) < 0.001);
        assertEquals(0, Information.klDivergence(new double[] {.3, .7}, new double[] {.3, .7}), 1e-12);
        assertTrue(Information.klDivergence(new double[] {.9, .1}, new double[] {.5, .5}) > 0);
    }

    @Test
    void categoricalSamplingMatchesProbabilities() {
        Random rnd = new Random(42);
        double[] p = {0.1, 0.6, 0.3};
        int[] counts = new int[3];
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            counts[Probability.sampleCategorical(p, rnd)]++;
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(p[i], counts[i] / (double) n, 0.01);
        }
    }

    @Test
    void bayesAndMoments() {
        // 1% prevalence, 99% sensitive test, 5% false positives -> only ~16.7% of positives are real.
        assertEquals(0.1667, Probability.bayes(0.99, 0.01, 0.05), 1e-4);
        assertEquals(2.0, Probability.variance(new double[] {1, 3, 5, 7}) / 2.5, 1e-12);
        Random rnd = new Random(3);
        double[] samples = new double[50_000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = Probability.gaussian(rnd);
        }
        assertEquals(0, Probability.mean(samples), 0.02);
        assertEquals(1, Probability.variance(samples), 0.03);
    }

    @Test
    void gradientDescentFindsTheMinimum() {
        var quadratic = GradientDescent.minimize(p -> (p[0] - 3) * (p[0] - 3), p -> new double[] {2 * (p[0] - 3)},
                new double[] {0}, 0.1, 0, 1000, 1e-9);
        assertEquals(3, quadratic.x()[0], 1e-6);

        var plain = GradientDescent.minimize(GradientDescent::rosenbrock, GradientDescent::rosenbrockGradient,
                new double[] {-1.5, 2}, 0.0005, 0.0, 20_000, 1e-6);
        var withMomentum = GradientDescent.minimize(GradientDescent::rosenbrock, GradientDescent::rosenbrockGradient,
                new double[] {-1.5, 2}, 0.0005, 0.9, 20_000, 1e-6);
        assertTrue(withMomentum.value() < plain.value(), "momentum gets further in the same budget");
        assertEquals(1, withMomentum.x()[0], 0.01);
    }

    @Test
    void tooHighLearningRateDiverges() {
        assertThrows(ArithmeticException.class, () -> GradientDescent.minimize(p -> p[0] * p[0],
                p -> new double[] {2 * p[0]}, new double[] {1}, 1.5, 0, 10_000, 1e-12));
    }

    @Test
    void simdMatchesScalar() {
        Random rnd = new Random(5);
        float[] a = new float[1003]; // deliberately not a multiple of the lane count
        float[] b = new float[1003];
        for (int i = 0; i < a.length; i++) {
            a[i] = rnd.nextFloat();
            b[i] = rnd.nextFloat();
        }
        assertEquals(SimdDot.scalar(a, b), SimdDot.simd(a, b), 1e-2);
        assertTrue(SimdDot.lanes() >= 1);
    }
}
