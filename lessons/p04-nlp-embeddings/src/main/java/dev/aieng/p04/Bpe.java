package dev.aieng.p04;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Byte-Pair Encoding (Sennrich et al., 2016), the tokenizer family used by GPT, Llama and most LLMs.
 * Training: start from characters, repeatedly merge the most frequent adjacent pair into a new token.
 * Encoding: apply the learned merges, in the order they were learned, to new text.
 * Words carry a leading "▁" marking a preceding space (SentencePiece convention), so decoding is lossless.
 */
public final class Bpe {

    public static final String SPACE = "▁";

    record Pair(String left, String right) {
        String merged() {
            return left + right;
        }
    }

    private final Map<Pair, Integer> mergeRanks = new LinkedHashMap<>();
    private final Map<String, Integer> vocab = new LinkedHashMap<>();

    // region train
    public Bpe train(String corpus, int numMerges) {
        Map<List<String>, Integer> words = new HashMap<>();
        for (String w : preTokenize(corpus)) {
            words.merge(chars(w), 1, Integer::sum);
        }
        words.keySet().forEach(symbols -> symbols.forEach(s -> vocab.putIfAbsent(s, vocab.size())));

        for (int m = 0; m < numMerges; m++) {
            Map<Pair, Integer> counts = new HashMap<>();
            words.forEach((symbols, freq) -> {
                for (int i = 0; i + 1 < symbols.size(); i++) {
                    counts.merge(new Pair(symbols.get(i), symbols.get(i + 1)), freq, Integer::sum);
                }
            });
            // Most frequent pair; ties broken alphabetically so training is deterministic.
            Pair best = counts.entrySet().stream()
                    .max(Map.Entry.<Pair, Integer>comparingByValue()
                            .thenComparing(e -> e.getKey().merged(), java.util.Comparator.reverseOrder()))
                    .map(Map.Entry::getKey).orElse(null);
            if (best == null || counts.get(best) < 2) {
                break; // nothing left worth merging
            }
            mergeRanks.put(best, mergeRanks.size());
            vocab.putIfAbsent(best.merged(), vocab.size());
            Map<List<String>, Integer> next = new HashMap<>();
            words.forEach((symbols, freq) -> next.merge(applyMerge(symbols, best), freq, Integer::sum));
            words = next;
        }
        return this;
    }
    // endregion

    // region encode
    /** Repeatedly merges the adjacent pair with the lowest rank (earliest learned) until none apply. */
    public List<String> encode(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : preTokenize(text)) {
            List<String> symbols = chars(word);
            while (symbols.size() > 1) {
                Pair best = null;
                int bestRank = Integer.MAX_VALUE;
                for (int i = 0; i + 1 < symbols.size(); i++) {
                    Integer rank = mergeRanks.get(new Pair(symbols.get(i), symbols.get(i + 1)));
                    if (rank != null && rank < bestRank) {
                        bestRank = rank;
                        best = new Pair(symbols.get(i), symbols.get(i + 1));
                    }
                }
                if (best == null) {
                    break;
                }
                symbols = applyMerge(symbols, best);
            }
            tokens.addAll(symbols);
        }
        return tokens;
    }

    public String decode(List<String> tokens) {
        String joined = String.join("", tokens).replace(SPACE, " ");
        return joined.startsWith(" ") ? joined.substring(1) : joined;
    }
    // endregion

    /** Token ids as a model would see them; unseen characters map to -1 (real tokenizers fall back to bytes). */
    public int[] ids(String text) {
        return encode(text).stream().mapToInt(t -> vocab.getOrDefault(t, -1)).toArray();
    }

    public int vocabularySize() {
        return vocab.size();
    }

    public int merges() {
        return mergeRanks.size();
    }

    static List<String> applyMerge(List<String> symbols, Pair pair) {
        List<String> out = new ArrayList<>(symbols.size());
        for (int i = 0; i < symbols.size(); i++) {
            if (i + 1 < symbols.size() && symbols.get(i).equals(pair.left()) && symbols.get(i + 1).equals(pair.right())) {
                out.add(pair.merged());
                i++;
            } else {
                out.add(symbols.get(i));
            }
        }
        return out;
    }

    /** Splits on spaces and prefixes every word with ▁, so "Hello world" -> ["▁Hello", "▁world"]. */
    static List<String> preTokenize(String text) {
        List<String> words = new ArrayList<>();
        for (String w : text.split(" ")) {
            if (!w.isEmpty()) {
                words.add(SPACE + w);
            }
        }
        return words;
    }

    private static List<String> chars(String word) {
        List<String> out = new ArrayList<>();
        word.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp))));
        return out;
    }
}
