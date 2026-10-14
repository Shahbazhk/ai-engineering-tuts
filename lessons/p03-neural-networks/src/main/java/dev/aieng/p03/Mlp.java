package dev.aieng.p03;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A multi-layer perceptron built from {@link Value} scalars. Slow, but every gradient is visible. */
public final class Mlp {

    // region model
    /** One neuron: tanh(w . x + b), or linear for the output layer. */
    static final class Neuron {
        final List<Value> w = new ArrayList<>();
        final Value b;
        final boolean linear;

        Neuron(int inputs, boolean linear, Random rnd) {
            for (int i = 0; i < inputs; i++) {
                w.add(new Value(rnd.nextGaussian() / Math.sqrt(inputs)));
            }
            b = new Value(0);
            this.linear = linear;
        }

        Value forward(List<Value> x) {
            Value sum = b;
            for (int i = 0; i < w.size(); i++) {
                sum = sum.add(w.get(i).mul(x.get(i)));
            }
            return linear ? sum : sum.tanh();
        }
    }

    private final List<List<Neuron>> layers = new ArrayList<>();

    /** sizes = {inputs, hidden..., outputs}, e.g. {2, 16, 16, 1}. */
    public Mlp(int[] sizes, long seed) {
        Random rnd = new Random(seed);
        for (int l = 0; l + 1 < sizes.length; l++) {
            List<Neuron> layer = new ArrayList<>();
            boolean last = l + 2 == sizes.length;
            for (int n = 0; n < sizes[l + 1]; n++) {
                layer.add(new Neuron(sizes[l], last, rnd));
            }
            layers.add(layer);
        }
    }

    public List<Value> forward(double[] input) {
        List<Value> x = new ArrayList<>();
        for (double v : input) {
            x.add(new Value(v));
        }
        for (List<Neuron> layer : layers) {
            List<Value> next = new ArrayList<>();
            for (Neuron n : layer) {
                next.add(n.forward(x));
            }
            x = next;
        }
        return x;
    }
    // endregion

    public List<Value> parameters() {
        List<Value> params = new ArrayList<>();
        for (List<Neuron> layer : layers) {
            for (Neuron n : layer) {
                params.addAll(n.w);
                params.add(n.b);
            }
        }
        return params;
    }

    // region train
    /**
     * Full-batch training for targets in {-1, +1} with mean squared error on the raw output.
     * The four lines inside the loop - forward, loss, backward, update - are the whole of deep learning.
     */
    public double train(double[][] xs, double[] ys, int epochs, double learningRate) {
        double last = Double.NaN;
        for (int epoch = 0; epoch < epochs; epoch++) {
            Value loss = new Value(0);
            for (int i = 0; i < xs.length; i++) {
                Value prediction = forward(xs[i]).getFirst();
                loss = loss.add(prediction.sub(new Value(ys[i])).pow(2));   // forward + loss
            }
            loss = loss.mul(1.0 / xs.length);
            loss.backward();                                                // backward
            for (Value p : parameters()) {
                p.step(learningRate);                                       // update
            }
            last = loss.data();
        }
        return last;
    }
    // endregion

    public static void main(String[] args) {
        double[][] xs = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        double[] ys = {-1, 1, 1, -1}; // XOR: not linearly separable
        Mlp mlp = new Mlp(new int[] {2, 8, 1}, 1);
        for (int round = 0; round < 5; round++) {
            System.out.printf("loss after %d epochs: %.5f%n", (round + 1) * 100, mlp.train(xs, ys, 100, 0.1));
        }
        for (double[] x : xs) {
            System.out.printf("%s -> %.3f%n", java.util.Arrays.toString(x), mlp.forward(x).getFirst().data());
        }
    }
}
