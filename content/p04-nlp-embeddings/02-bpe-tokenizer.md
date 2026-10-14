---
title: Build a BPE Tokenizer
summary: How LLMs split text into tokens — train byte-pair encoding from scratch, and see why token counts drive cost, context limits and odd model behaviour.
minutes: 60
prereqs: [p04-nlp-embeddings/01-text-to-numbers]
tags: [nlp, tokenization, bpe, llm]
quiz:
  - q: How does BPE training choose the next merge?
    options:
      - Alphabetically
      - The most frequent pair of adjacent symbols in the training corpus
      - The longest word
      - Randomly
    answer: 1
    why: Each merge adds the most common adjacent pair as a new symbol, so frequent words and subwords become single tokens.
  - q: Why do LLMs struggle with tasks like counting the letters in "strawberry"?
    options:
      - They have no memory
      - They see subword tokens, not individual characters
      - Their vocabulary has no letters
      - Temperature is too high
    answer: 1
    why: '"strawberry" may be 2–3 tokens. The model never directly sees the individual letters unless it has learned their spelling.'
  - q: The same text costs more tokens in German or Hindi than in English with many tokenizers. Why?
    options:
      - Those languages have longer words in every case
      - The tokenizer's merges were learned mostly from English text, so other languages split into more, smaller pieces
      - Non-English text is compressed differently by HTTP
      - Pricing is higher for other languages
    answer: 1
    why: Tokenizer training data determines which strings get efficient merges. This directly affects cost and context usage per language.
interview:
  - q: Why does tokenization matter for an enterprise LLM application?
    a: |
      Tokens are the unit of cost (pricing is per input/output token), of limits (context windows, max output tokens) and of latency (output tokens are generated one at a time). Tokenizers differ between model families, so the same document can be 20–40% more tokens on one model than another, and non-English or code-heavy content often tokenizes poorly. Practical consequences: estimate cost with the target model's tokenizer, chunk documents by tokens rather than characters, leave headroom in the context window, and remember that model quirks (spelling, arithmetic on digits, identifiers split oddly) often come from tokenization.
    level: senior
---

## Why it matters

Every price, limit and latency figure in LLM work is measured in **tokens**. Chunk sizes for RAG are measured in tokens. Many odd model behaviours — trouble spelling, counting characters, or handling long numbers — come from the tokenizer. Building one takes an afternoon and removes the mystery.

## Concept

Word-level vocabularies explode in size and cannot handle unseen words. Character-level sequences are very long. **Byte-Pair Encoding** sits in between:

1. Start with every word split into characters.
2. Count every adjacent pair of symbols across the corpus.
3. Merge the most frequent pair into a new symbol; record the merge.
4. Repeat until the vocabulary reaches the target size (32k–200k for real LLMs).

To **encode** new text, apply the learned merges in the order they were learned.

```mermaid
flowchart TD
    A["l o w e s t"] -->|"merge (l,o) → lo"| B["lo w e s t"]
    B -->|"merge (lo,w) → low"| C["low e s t"]
    C -->|"merge (e,s) → es"| D["low es t"]
    D -->|"merge (es,t) → est"| E["low est"]
    E -->|"merge (low,est) → lowest"| F["lowest"]
```

Frequent words become one token; rare words fall back to several subword pieces; nothing is ever "unknown". Production tokenizers (GPT's `tiktoken`, Llama's SentencePiece) work on **bytes** rather than characters, so any input — emoji, binary, any script — can be encoded.

## Build it from scratch

Training: count pairs, merge the best one, repeat. Ties are broken alphabetically so training is deterministic.

<!-- code: lessons/p04-nlp-embeddings/src/main/java/dev/aieng/p04/Bpe.java#train -->

Encoding applies the earliest-learned applicable merge first, exactly like GPT-2's tokenizer:

<!-- code: lessons/p04-nlp-embeddings/src/main/java/dev/aieng/p04/Bpe.java#encode -->

The tests show the key properties: frequent training words become single tokens (`▁lowest`), an unseen word (`lowness`) is split into known pieces that keep the frequent stem (`▁low…`), and decoding is lossless.

## Now with the library

```java
// Hugging Face tokenizers from Java via DJL (reference snippet; ai.djl.huggingface:tokenizers is not a build dependency)
try (HuggingFaceTokenizer tok = HuggingFaceTokenizer.newInstance("meta-llama/Llama-3.2-1B")) {
    Encoding e = tok.encode("Spring Boot auto-configuration");
    System.out.println(Arrays.toString(e.getTokens()));
    System.out.println(e.getIds().length + " tokens");
}
```

For OpenAI-family models, the pure-Java library **JTokkit** implements `tiktoken`:

```java
Encoding enc = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
int tokens = enc.countTokens(prompt);
```

Spring AI uses JTokkit internally for token estimation (`JTokkitTokenCountEstimator`), for example when splitting documents with `TokenTextSplitter` (Phase 9).

## Production notes

- **Count tokens with the target model's tokenizer** when budgeting cost and context. A rule of thumb for English is ~4 characters or ~0.75 words per token; code, JSON and non-English text are often much worse.
- **Chunk by tokens,** not characters, for RAG — otherwise chunks overflow embedding-model limits unpredictably.
- **Identifiers and numbers tokenize strangely.** `getCustomerAccountBalance` may be five tokens; long numbers are split into pieces. This affects both cost and model accuracy on exact-match tasks.

## Exercises

1. **Easy** — Train on a paragraph of your own text with 50 merges and print the merges in order. Which ones appear first?
2. **Medium** — Measure "compression": characters per token on a held-out paragraph, for 10, 50, 200 and 1,000 merges.
3. **Hard** — Make the tokenizer byte-level: operate on UTF-8 bytes (mapped to printable characters as GPT-2 does) so any input — including emoji and Hindi — encodes without unknown symbols. Compare tokens per character for English and another language.

## Common mistakes

- Estimating tokens with `text.length() / 4` for non-English text or code.
- Assuming tokenizers are interchangeable between model families.
- Forgetting that special tokens (system/user markers, BOS/EOS) also consume context.

## Further reading

- Sennrich, Haddow & Birch, "Neural Machine Translation of Rare Words with Subword Units", ACL 2016.
- Andrej Karpathy, "Let's build the GPT Tokenizer" (video, 2024).
- JTokkit: <https://github.com/knuddelsgmbh/jtokkit>
