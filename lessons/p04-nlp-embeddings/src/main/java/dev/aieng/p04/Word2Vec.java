package dev.aieng.p04;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Skip-gram with negative sampling (Mikolov et al., 2013). Learn a vector per word such that words appearing
 * in similar contexts end up close together: "you shall know a word by the company it keeps" (Firth, 1957).
 * Each (centre, context) pair is a positive example; a few random words are negatives. The model is a
 * logistic regression on the dot product of two vectors.
 */
public final class Word2Vec {

    private final Map<String, Integer> index = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private double[][] input;   // the embeddings we keep
    private double[][] output;  // context vectors, discarded after training

    // region train
    public Word2Vec train(List<List<String>> sentences, int dim, int window, int negatives, int epochs,
                          double learningRate, long seed) {
        for (List<String> s : sentences) {
            for (String w : s) {
                if (!index.containsKey(w)) {
                    index.put(w, words.size());
                    words.add(w);
                }
            }
        }
        Random rnd = new Random(seed);
        input = new double[words.size()][dim];
        output = new double[words.size()][dim];
        for (double[] v : input) {
            for (int i = 0; i < dim; i++) {
                v[i] = (rnd.nextDouble() - 0.5) / dim;
            }
        }
        for (int epoch = 0; epoch < epochs; epoch++) {
            for (List<String> s : sentences) {
                for (int c = 0; c < s.size(); c++) {
                    int centre = index.get(s.get(c));
                    for (int o = Math.max(0, c - window); o <= Math.min(s.size() - 1, c + window); o++) {
                        if (o == c) {
                            continue;
                        }
                        update(centre, index.get(s.get(o)), 1, learningRate);           // real context: push together
                        for (int k = 0; k < negatives; k++) {
                            update(centre, rnd.nextInt(words.size()), 0, learningRate); // random word: push apart
                        }
                    }
                }
            }
        }
        return this;
    }

    /** One step of logistic regression on sigma(in . out) towards label 1 (context) or 0 (negative sample). */
    private void update(int centre, int context, int label, double lr) {
        double[] in = input[centre];
        double[] out = output[context];
        double dot = 0;
        for (int i = 0; i < in.length; i++) {
            dot += in[i] * out[i];
        }
        double g = (1 / (1 + Math.exp(-dot)) - label) * lr; // (p - y): the logistic-regression gradient again
        for (int i = 0; i < in.length; i++) {
            double inI = in[i];
            in[i] -= g * out[i];
            out[i] -= g * inI;
        }
    }
    // endregion

    public double[] vector(String word) {
        Integer i = index.get(word);
        if (i == null) {
            throw new IllegalArgumentException("unknown word: " + word);
        }
        return input[i].clone();
    }

    public double similarity(String a, String b) {
        return cosine(vector(a), vector(b));
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    /** A toy corpus with two clear topics so the effect is visible in seconds. */
    public static List<List<String>> toyCorpus() {
        String[] royal = {"the king rules the kingdom", "the queen rules the kingdom", "a king wears a crown",
                "a queen wears a crown", "the king and the queen live in the palace", "the palace has a throne"};
        String[] pets = {"the dog eats meat", "the cat eats fish", "a dog chases a cat", "the cat drinks milk",
                "a dog sleeps in the garden", "the cat sleeps in the garden"};
        List<List<String>> out = new ArrayList<>();
        for (int repeat = 0; repeat < 20; repeat++) {
            for (String s : royal) {
                out.add(List.of(s.split(" ")));
            }
            for (String s : pets) {
                out.add(List.of(s.split(" ")));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        Word2Vec w2v = new Word2Vec().train(toyCorpus(), 16, 2, 3, 30, 0.05, 1);
        for (String[] pair : new String[][] {{"king", "queen"}, {"dog", "cat"}, {"king", "dog"}, {"crown", "fish"}}) {
            System.out.printf("sim(%s, %s) = %.3f%n", pair[0], pair[1], w2v.similarity(pair[0], pair[1]));
        }
    }
}
