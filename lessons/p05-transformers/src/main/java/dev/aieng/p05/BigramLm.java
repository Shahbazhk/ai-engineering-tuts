package dev.aieng.p05;

import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import dev.aieng.p03.Optimizers;
import dev.aieng.p03.Tensor;

/**
 * The smallest possible language model: predict the next character from the current one. Trained two ways -
 * by counting, and as a neural network with the Phase 3 autograd engine - to show that gradient descent on
 * cross-entropy recovers the counting solution. GPT replaces "current character" with "attention over all
 * previous tokens", but the training objective is identical.
 */
public final class BigramLm {

    private final char[] alphabet;
    private final int[] index = new int[Character.MAX_VALUE + 1];

    public BigramLm(String text) {
        TreeSet<Character> chars = new TreeSet<>();
        for (char c : text.toCharArray()) {
            chars.add(c);
        }
        alphabet = new char[chars.size()];
        int i = 0;
        for (char c : chars) {
            alphabet[i] = c;
            index[c] = i++;
        }
    }

    public int vocab() {
        return alphabet.length;
    }

    public int[] encode(String text) {
        return text.chars().map(c -> index[c]).toArray();
    }

    public String decode(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        ids.forEach(id -> sb.append(alphabet[id]));
        return sb.toString();
    }

    // region counts
    /**
     * Counting estimate with add-alpha smoothing: P(b | a) = (count(a b) + alpha) / (count(a) + alpha * V).
     * alpha = 0 is the maximum-likelihood solution (lowest possible training loss); alpha > 0 reserves probability
     * for unseen pairs, trading training fit for better generalisation.
     */
    public double[][] countModel(int[] ids, double alpha) {
        int v = vocab();
        double[][] p = new double[v][v];
        for (double[] row : p) {
            java.util.Arrays.fill(row, alpha);
        }
        for (int i = 0; i + 1 < ids.length; i++) {
            p[ids[i]][ids[i + 1]]++;
        }
        for (double[] row : p) {
            double sum = 0;
            for (double x : row) {
                sum += x;
            }
            for (int j = 0; j < v; j++) {
                row[j] = sum == 0 ? 1.0 / v : row[j] / sum;
            }
        }
        return p;
    }

    /** Average negative log-likelihood per character; exp of it is the perplexity. */
    public static double nll(double[][] probs, int[] ids) {
        double sum = 0;
        for (int i = 0; i + 1 < ids.length; i++) {
            sum -= Math.log(probs[ids[i]][ids[i + 1]]);
        }
        return sum / (ids.length - 1);
    }
    // endregion

    // region neural
    /**
     * Neural bigram: logits = onehot(current) @ W, i.e. row W[current]. Trained with softmax cross-entropy and
     * Adam using the Tensor engine from Phase 3. Returns the learned probability table.
     */
    public double[][] neuralModel(int[] ids, int steps, long seed) {
        int v = vocab();
        int n = ids.length - 1;
        double[] onehot = new double[n * v];
        int[] targets = new int[n];
        for (int i = 0; i < n; i++) {
            onehot[i * v + ids[i]] = 1;
            targets[i] = ids[i + 1];
        }
        Tensor x = Tensor.of(n, v, onehot);
        Tensor w = Tensor.randn(v, v, 0.01, new Random(seed));
        Optimizers.Optimizer adam = Optimizers.adam(0.1);
        for (int step = 0; step < steps; step++) {
            adam.zeroGrad(List.of(w));
            x.matmul(w).softmaxCrossEntropy(targets).backward();
            adam.step(List.of(w));
        }
        double[][] probs = new double[v][];
        for (int a = 0; a < v; a++) {
            double[] logits = new double[v];
            System.arraycopy(w.data, a * v, logits, 0, v);
            probs[a] = M.softmax(logits);
        }
        return probs;
    }
    // endregion

    public String generate(double[][] probs, char start, int length, double temperature, long seed) {
        Random rnd = new Random(seed);
        List<Integer> out = new java.util.ArrayList<>(List.of(index[start]));
        for (int i = 0; i < length; i++) {
            double[] logits = new double[vocab()];
            double[] row = probs[out.getLast()];
            for (int j = 0; j < logits.length; j++) {
                logits[j] = Math.log(row[j]);
            }
            out.add(Sampling.sample(logits, temperature, 0, 1.0, rnd));
        }
        return decode(out);
    }

    public static final String CORPUS = """
            spring boot makes it easy to create stand-alone production-grade spring based applications.
            spring ai provides a portable api for chat models, embedding models and vector stores.
            a transformer predicts the next token from all previous tokens using attention.
            retrieval augmented generation adds relevant documents to the prompt before the model answers.
            """.repeat(3);

    public static void main(String[] args) {
        BigramLm lm = new BigramLm(CORPUS);
        int[] ids = lm.encode(CORPUS);
        double[][] mle = lm.countModel(ids, 0);
        double[][] smoothed = lm.countModel(ids, 1);
        double[][] neural = lm.neuralModel(ids, 300, 1);
        System.out.printf("vocab %d | uniform %.3f | counts (MLE) %.3f | counts (add-one) %.3f | neural %.3f%n",
                lm.vocab(), Math.log(lm.vocab()), nll(mle, ids), nll(smoothed, ids), nll(neural, ids));
        System.out.println("sample: " + lm.generate(neural, 's', 120, 0.8, 7).replace('\n', ' '));
    }
}
