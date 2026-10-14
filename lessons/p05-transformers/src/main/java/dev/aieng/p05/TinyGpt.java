package dev.aieng.p05;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * A decoder-only transformer (GPT architecture) forward pass, with a KV cache for generation.
 * Pre-norm blocks: x = x + MHA(LN(x)); x = x + FFN(LN(x)). Weights are random here - the point is to see every
 * operation an LLM performs per token. Training needs gradients for each op (see the lesson's exercises).
 */
public final class TinyGpt {

    public record Config(int vocab, int dModel, int heads, int layers, int maxLen) {
        public Config {
            if (dModel % heads != 0) {
                throw new IllegalArgumentException("dModel must be divisible by heads");
            }
        }

        int headDim() {
            return dModel / heads;
        }
    }

    /** Keys and values already computed for earlier positions, one growing list of rows per layer. */
    public static final class KvCache {
        final List<List<double[]>> keys = new ArrayList<>();
        final List<List<double[]>> values = new ArrayList<>();

        KvCache(int layers) {
            for (int l = 0; l < layers; l++) {
                keys.add(new ArrayList<>());
                values.add(new ArrayList<>());
            }
        }

        public int length() {
            return keys.getFirst().size();
        }
    }

    private final Config cfg;
    private final double[][] tokenEmbedding; // vocab x d; also used (transposed) as the output head: weight tying
    private final double[][] positionEmbedding; // maxLen x d
    private final Block[] blocks;
    private final double[] finalGain;
    private final double[] finalBias;

    public TinyGpt(Config cfg, long seed) {
        this.cfg = cfg;
        Random rnd = new Random(seed);
        tokenEmbedding = M.random(cfg.vocab(), cfg.dModel(), 0.02, rnd);
        positionEmbedding = M.random(cfg.maxLen(), cfg.dModel(), 0.02, rnd);
        blocks = new Block[cfg.layers()];
        for (int l = 0; l < blocks.length; l++) {
            blocks[l] = new Block(cfg, rnd);
        }
        finalGain = ones(cfg.dModel());
        finalBias = new double[cfg.dModel()];
    }

    // region forward
    /** Logits for every position of {@code tokens} (n x vocab), computed from scratch. */
    public double[][] forward(int[] tokens) {
        return run(tokens, 0, new KvCache(cfg.layers()));
    }

    /** Logits for ONE new token, reusing cached keys/values of all earlier positions: O(n) instead of O(n^2). */
    public double[] next(int token, KvCache cache) {
        return run(new int[] {token}, cache.length(), cache)[0];
    }

    private double[][] run(int[] tokens, int startPos, KvCache cache) {
        if (startPos + tokens.length > cfg.maxLen()) {
            throw new IllegalArgumentException("context window of " + cfg.maxLen() + " tokens exceeded");
        }
        double[][] x = new double[tokens.length][];
        for (int i = 0; i < tokens.length; i++) {
            x[i] = new double[cfg.dModel()];
            for (int d = 0; d < cfg.dModel(); d++) {
                x[i][d] = tokenEmbedding[tokens[i]][d] + positionEmbedding[startPos + i][d];
            }
        }
        for (int l = 0; l < blocks.length; l++) {
            x = blocks[l].forward(x, cache.keys.get(l), cache.values.get(l));
        }
        double[][] h = layerNorm(x, finalGain, finalBias);
        return M.matmul(h, M.transpose(tokenEmbedding)); // project back to vocabulary scores
    }
    // endregion

    // region generate
    public List<Integer> generate(int[] prompt, int newTokens, Function<double[], Integer> sampler, boolean useCache) {
        List<Integer> out = new ArrayList<>();
        for (int t : prompt) {
            out.add(t);
        }
        if (useCache) {
            KvCache cache = new KvCache(cfg.layers());
            double[][] promptLogits = run(prompt, 0, cache);  // "prefill": all prompt tokens in one pass
            double[] logits = promptLogits[promptLogits.length - 1];
            for (int i = 0; i < newTokens; i++) {             // "decode": one token at a time
                int nextToken = sampler.apply(logits);
                out.add(nextToken);
                if (i + 1 < newTokens) {
                    logits = next(nextToken, cache);
                }
            }
        } else {
            for (int i = 0; i < newTokens; i++) {             // recompute everything every step: O(n^2) per token
                double[][] logits = forward(out.stream().mapToInt(Integer::intValue).toArray());
                out.add(sampler.apply(logits[logits.length - 1]));
            }
        }
        return out;
    }
    // endregion

    /**
     * Per block: attention 4·d² (Wq, Wk, Wv, Wo), FFN 8·d² + 5·d (W1, b1, W2, b2), two LayerNorms 4·d.
     * Plus embeddings (vocab·d + maxLen·d) and the final LayerNorm (2·d). The output head is tied, so it is free.
     */
    public long parameterCount() {
        long d = cfg.dModel();
        long perBlock = 4 * d * d + (8 * d * d + 5 * d) + 4 * d;
        return cfg.vocab() * d + cfg.maxLen() * d + cfg.layers() * perBlock + 2 * d;
    }

    // region block
    static final class Block {
        final Config cfg;
        final double[][] wq, wk, wv, wo, w1, w2;
        final double[] b1, b2, ln1g, ln1b, ln2g, ln2b;

        Block(Config cfg, Random rnd) {
            this.cfg = cfg;
            int d = cfg.dModel();
            double std = 1 / Math.sqrt(d);
            wq = M.random(d, d, std, rnd);
            wk = M.random(d, d, std, rnd);
            wv = M.random(d, d, std, rnd);
            wo = M.random(d, d, std / Math.sqrt(2 * cfg.layers()), rnd); // scaled residual init (GPT-2)
            w1 = M.random(d, 4 * d, std, rnd);
            w2 = M.random(4 * d, d, 1 / Math.sqrt(4 * d) / Math.sqrt(2 * cfg.layers()), rnd);
            b1 = new double[4 * d];
            b2 = new double[d];
            ln1g = ones(d);
            ln1b = new double[d];
            ln2g = ones(d);
            ln2b = new double[d];
        }

        double[][] forward(double[][] x, List<double[]> cachedK, List<double[]> cachedV) {
            // 1) multi-head causal self-attention with a residual connection
            double[][] h = layerNorm(x, ln1g, ln1b);
            double[][] q = M.matmul(h, wq);
            double[][] k = M.matmul(h, wk);
            double[][] v = M.matmul(h, wv);
            cachedK.addAll(List.of(k));
            cachedV.addAll(List.of(v));
            double[][] allK = cachedK.toArray(double[][]::new);
            double[][] allV = cachedV.toArray(double[][]::new);
            int hd = cfg.headDim();
            double[][] concat = new double[x.length][cfg.dModel()];
            for (int head = 0; head < cfg.heads(); head++) {
                int from = head * hd;
                Attention.Result r = Attention.scaledDotProduct(
                        M.slice(q, from, from + hd), M.slice(allK, from, from + hd), M.slice(allV, from, from + hd), true);
                for (int i = 0; i < x.length; i++) {
                    System.arraycopy(r.output()[i], 0, concat[i], from, hd);
                }
            }
            x = M.add(x, M.matmul(concat, wo));

            // 2) position-wise feed-forward network with a residual connection
            double[][] f = M.matmul(layerNorm(x, ln2g, ln2b), w1);
            for (double[] row : f) {
                for (int j = 0; j < row.length; j++) {
                    row[j] = gelu(row[j] + b1[j]);
                }
            }
            double[][] out = M.matmul(f, w2);
            for (double[] row : out) {
                for (int j = 0; j < row.length; j++) {
                    row[j] += b2[j];
                }
            }
            return M.add(x, out);
        }
    }

    /** LayerNorm per row: (x - mean) / sqrt(var + eps) * gain + bias. */
    static double[][] layerNorm(double[][] x, double[] gain, double[] bias) {
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            double mean = 0;
            for (double v : x[i]) {
                mean += v / x[i].length;
            }
            double var = 0;
            for (double v : x[i]) {
                var += (v - mean) * (v - mean) / x[i].length;
            }
            double inv = 1 / Math.sqrt(var + 1e-5);
            for (int j = 0; j < x[i].length; j++) {
                out[i][j] = (x[i][j] - mean) * inv * gain[j] + bias[j];
            }
        }
        return out;
    }

    /** GELU, tanh approximation as in GPT-2. */
    static double gelu(double x) {
        return 0.5 * x * (1 + Math.tanh(Math.sqrt(2 / Math.PI) * (x + 0.044715 * x * x * x)));
    }
    // endregion

    private static double[] ones(int n) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, 1);
        return a;
    }
}
