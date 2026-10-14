package dev.aieng.p03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class NeuralNetworkTest {

    @Test
    void valueGradientsMatchHandDerivation() {
        // f = (a * b + c) * a  ->  df/da = 2ab + c, df/db = a^2, df/dc = a
        Value a = new Value(2), b = new Value(-3), c = new Value(10);
        Value f = a.mul(b).add(c).mul(a);
        f.backward();
        assertEquals(8, f.data(), 1e-12);
        assertEquals(2 * 2 * -3 + 10, a.grad(), 1e-12);
        assertEquals(4, b.grad(), 1e-12);
        assertEquals(2, c.grad(), 1e-12);
    }

    @Test
    void reusedNodesAccumulateGradients() {
        Value a = new Value(3);
        Value f = a.add(a); // a used twice: df/da must be 2, not 1
        f.backward();
        assertEquals(2, a.grad(), 1e-12);
    }

    @Test
    void valueGradientsPassNumericCheck() {
        double x0 = 0.7, y0 = -1.3, h = 1e-6;
        Value x = new Value(x0), y = new Value(y0);
        Value f = x.mul(y).tanh().add(x.pow(3)).add(y.exp().log()).relu();
        f.backward();
        java.util.function.DoubleBinaryOperator g = (xv, yv) ->
                Math.max(0, Math.tanh(xv * yv) + Math.pow(xv, 3) + yv);
        assertEquals((g.applyAsDouble(x0 + h, y0) - g.applyAsDouble(x0 - h, y0)) / (2 * h), x.grad(), 1e-6);
        assertEquals((g.applyAsDouble(x0, y0 + h) - g.applyAsDouble(x0, y0 - h)) / (2 * h), y.grad(), 1e-6);
    }

    @Test
    void deepGraphDoesNotOverflowTheStack() {
        Value v = new Value(1);
        for (int i = 0; i < 50_000; i++) {
            v = v.mul(1.0);
        }
        v.backward();
        assertEquals(1, v.data(), 1e-12);
    }

    @Test
    void mlpLearnsXor() {
        double[][] xs = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        double[] ys = {-1, 1, 1, -1};
        Mlp mlp = new Mlp(new int[] {2, 8, 1}, 1);
        double loss = mlp.train(xs, ys, 500, 0.1);
        assertTrue(loss < 0.05, "loss " + loss);
        for (int i = 0; i < 4; i++) {
            assertEquals(Math.signum(ys[i]), Math.signum(mlp.forward(xs[i]).getFirst().data()));
        }
        assertEquals(2 * 8 + 8 + 8 + 1, mlp.parameters().size());
    }

    @Test
    void tensorGradientsPassNumericCheck() {
        Random rnd = new Random(1);
        Tensor x = Tensor.randn(4, 3, 1, rnd);
        Tensor w = Tensor.randn(3, 5, 1, rnd);
        Tensor b = Tensor.randn(1, 5, 1, rnd);
        int[] targets = {0, 4, 2, 1};
        Supplier<Tensor> loss = () -> x.matmul(w).addRow(b).tanh().softmaxCrossEntropy(targets);
        loss.get().backward();
        for (Tensor p : new Tensor[] {x, w, b}) {
            double[] analytic = p.grad.clone();
            for (int i = 0; i < p.data.length; i++) {
                double original = p.data[i];
                p.data[i] = original + 1e-6;
                double plus = loss.get().data[0];
                p.data[i] = original - 1e-6;
                double minus = loss.get().data[0];
                p.data[i] = original;
                assertEquals((plus - minus) / 2e-6, analytic[i], 1e-6);
            }
        }
    }

    @Test
    void reluAndSumGradients() {
        Tensor x = Tensor.of(1, 3, -1, 0.5, 2);
        x.relu().sum().backward();
        assertEquals(0, x.grad[0]);
        assertEquals(1, x.grad[1]);
        assertEquals(1, x.grad[2]);
    }

    @Test
    void xavierKeepsActivationsAliveWhileTinyWeightsVanish() {
        double[] tiny = Init.activationStd(0.01, 128, 10, 1);
        double[] xavier = Init.activationStd(Init.xavier(128), 128, 10, 1);
        double[] large = Init.activationStd(0.5, 128, 10, 1);
        assertTrue(tiny[9] < 1e-6, "vanishing: " + tiny[9]);
        assertTrue(xavier[9] > 0.1, "xavier layer 10: " + xavier[9]);
        assertTrue(large[9] > 0.9, "saturated near +/-1: " + large[9]);
    }

    @Test
    void adamTrainsTheSpiralClassifier() {
        Trainer.Data[] parts = Trainer.split(Trainer.spiral(100, 3, 0.2, 1), 0.2, 2);
        Trainer.Model model = new Trainer.Model(2, 64, 3, new Random(3));
        Trainer.Report r = Trainer.train(model, parts[0], parts[1], Optimizers.adam(0.01), 200, 32, 4);
        assertTrue(r.losses().get(0) > r.finalLoss(), "loss must fall");
        assertTrue(r.trainAccuracy() > 0.9, "train " + r.trainAccuracy());
        assertTrue(r.validationAccuracy() > 0.85, "validation " + r.validationAccuracy());
    }

    @Test
    void optimizersReduceAQuadratic() {
        for (Optimizers.Optimizer opt : new Optimizers.Optimizer[] {Optimizers.sgd(0.1, 0.9), Optimizers.adam(0.1)}) {
            Tensor w = Tensor.of(1, 2, 5, -3);
            for (int step = 0; step < 300; step++) {
                opt.zeroGrad(java.util.List.of(w));
                // loss = w @ copy(w)^T. The copy is a separate leaf, so dLoss/dw = w: the gradient of 0.5 * |w|^2.
                Tensor loss = w.matmul(Tensor.of(2, 1, w.data[0], w.data[1]));
                loss.backward();
                opt.step(java.util.List.of(w));
            }
            assertTrue(Math.abs(w.data[0]) < 0.05 && Math.abs(w.data[1]) < 0.05, java.util.Arrays.toString(w.data));
        }
    }
}
