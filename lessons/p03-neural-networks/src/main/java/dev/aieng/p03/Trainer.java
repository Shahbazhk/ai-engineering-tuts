package dev.aieng.p03;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A complete training run: a 2-layer MLP built on {@link Tensor} learns to classify a 3-armed spiral, a classic
 * dataset no linear model can solve. Mini-batches, an optimiser, a train/validation split and loss logging -
 * the same loop that fine-tunes an LLM, at toy scale.
 */
public final class Trainer {

    public record Data(double[][] x, int[] y) {
    }

    public record Report(double finalLoss, double trainAccuracy, double validationAccuracy, List<Double> losses) {
    }

    // region model
    /** Two-layer MLP: logits = relu(x W1 + b1) W2 + b2. */
    public static final class Model {
        final Tensor w1, b1, w2, b2;

        public Model(int inputs, int hidden, int classes, Random rnd) {
            w1 = Tensor.randn(inputs, hidden, Init.he(inputs), rnd);
            b1 = Tensor.zeros(1, hidden);
            w2 = Tensor.randn(hidden, classes, Init.xavier(hidden), rnd);
            b2 = Tensor.zeros(1, classes);
        }

        public Tensor logits(Tensor x) {
            return x.matmul(w1).addRow(b1).relu().matmul(w2).addRow(b2);
        }

        public List<Tensor> parameters() {
            return List.of(w1, b1, w2, b2);
        }
    }
    // endregion

    // region loop
    public static Report train(Model model, Data train, Data validation, Optimizers.Optimizer optimizer,
                               int epochs, int batchSize, long seed) {
        Random rnd = new Random(seed);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < train.y().length; i++) {
            order.add(i);
        }
        List<Double> losses = new ArrayList<>();
        double epochLoss = Double.NaN;
        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(order, rnd);                     // new mini-batch composition every epoch
            double sum = 0;
            int batches = 0;
            for (int start = 0; start < order.size(); start += batchSize) {
                List<Integer> idx = order.subList(start, Math.min(start + batchSize, order.size()));
                Tensor loss = model.logits(rows(train.x(), idx)).softmaxCrossEntropy(labels(train.y(), idx));
                optimizer.zeroGrad(model.parameters());           // gradients accumulate unless cleared
                loss.backward();
                optimizer.step(model.parameters());
                sum += loss.data[0];
                batches++;
            }
            epochLoss = sum / batches;
            losses.add(epochLoss);
        }
        return new Report(epochLoss, accuracy(model, train), accuracy(model, validation), losses);
    }
    // endregion

    public static double accuracy(Model model, Data d) {
        int[] predicted = model.logits(rows(d.x(), null)).argmaxRows();
        int correct = 0;
        for (int i = 0; i < predicted.length; i++) {
            correct += predicted[i] == d.y()[i] ? 1 : 0;
        }
        return (double) correct / predicted.length;
    }

    /** CS231n's spiral: {@code perClass} points on each of {@code classes} interleaved arms. */
    public static Data spiral(int perClass, int classes, double noise, long seed) {
        Random rnd = new Random(seed);
        int n = perClass * classes;
        double[][] x = new double[n][2];
        int[] y = new int[n];
        for (int c = 0; c < classes; c++) {
            for (int i = 0; i < perClass; i++) {
                double r = (double) i / perClass;
                double t = c * 4.0 + 4.0 * r + rnd.nextGaussian() * noise;
                int k = c * perClass + i;
                x[k][0] = r * Math.sin(t);
                x[k][1] = r * Math.cos(t);
                y[k] = c;
            }
        }
        return new Data(x, y);
    }

    public static Data[] split(Data d, double validationFraction, long seed) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < d.y().length; i++) {
            idx.add(i);
        }
        Collections.shuffle(idx, new Random(seed));
        int v = (int) (d.y().length * validationFraction);
        return new Data[] {subset(d, idx.subList(v, idx.size())), subset(d, idx.subList(0, v))};
    }

    private static Data subset(Data d, List<Integer> idx) {
        double[][] x = new double[idx.size()][];
        int[] y = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            x[i] = d.x()[idx.get(i)];
            y[i] = d.y()[idx.get(i)];
        }
        return new Data(x, y);
    }

    private static Tensor rows(double[][] x, List<Integer> idx) {
        int n = idx == null ? x.length : idx.size();
        int f = x[0].length;
        double[] data = new double[n * f];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x[idx == null ? i : idx.get(i)], 0, data, i * f, f);
        }
        return Tensor.of(n, f, data);
    }

    private static int[] labels(int[] y, List<Integer> idx) {
        int[] out = new int[idx.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = y[idx.get(i)];
        }
        return out;
    }

    public static void main(String[] args) {
        Data[] parts = split(spiral(200, 3, 0.2, 1), 0.2, 2);
        for (var entry : new Object[][] {{"SGD+momentum", Optimizers.sgd(0.1, 0.9)}, {"Adam", Optimizers.adam(0.01)}}) {
            Model model = new Model(2, 64, 3, new Random(3));
            Report r = train(model, parts[0], parts[1], (Optimizers.Optimizer) entry[1], 300, 32, 4);
            System.out.printf("%-13s loss %.4f  train acc %.3f  validation acc %.3f  (epoch 10 loss %.3f)%n",
                    entry[0], r.finalLoss(), r.trainAccuracy(), r.validationAccuracy(), r.losses().get(9));
        }
    }
}
