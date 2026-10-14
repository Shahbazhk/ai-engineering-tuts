---
title: Word Embeddings with word2vec
summary: Learn dense word vectors from co-occurrence — skip-gram with negative sampling in plain Java — and see meaning emerge from context.
minutes: 55
prereqs: [p04-nlp-embeddings/02-bpe-tokenizer]
tags: [nlp, embeddings, word2vec]
quiz:
  - q: What training signal does skip-gram use?
    options:
      - Human-labelled synonyms
      - Predicting which words appear near each other in raw text
      - Dictionary definitions
      - Translation pairs
    answer: 1
    why: It is self-supervised — the text itself provides the labels. Words with similar contexts end up with similar vectors.
  - q: What is negative sampling for?
    options:
      - Removing offensive words
      - Avoiding a softmax over the whole vocabulary by contrasting real context words with a few random ones
      - Making vectors negative
      - Reducing the learning rate
    answer: 1
    why: A full softmax over a 100k vocabulary per step is expensive. Logistic regression against k random "negatives" approximates it cheaply.
  - q: What is the main limitation of word2vec compared with modern contextual embeddings?
    options:
      - It cannot be trained in Java
      - Each word has one fixed vector regardless of context — "bank" has the same vector in "river bank" and "bank account"
      - Its vectors are too large
      - It needs labelled data
    answer: 1
    why: Static embeddings cannot disambiguate. Transformers (Phase 5) produce a different vector for each occurrence based on its context.
interview:
  - q: How do embeddings capture meaning if nobody tells the model what words mean?
    a: |
      Through the distributional hypothesis — words used in similar contexts tend to have similar meanings. Training optimises vectors so that a word's vector predicts the words around it; words with interchangeable contexts ("king"/"queen", "dog"/"cat") must therefore end up near each other to predict those contexts well. Meaning is encoded relationally, as geometry, not as definitions. The same principle, scaled up and made context-dependent by transformers trained with contrastive objectives, gives the sentence embeddings used for semantic search.
    level: senior
---

## Why it matters

Word2vec (2013) was the moment NLP discovered that **meaning can be learned as geometry** from raw text. Every embedding model you use for semantic search descends from this idea: train vectors so that things used in similar contexts end up close together.

## Concept

**Distributional hypothesis:** "You shall know a word by the company it keeps" (J.R. Firth, 1957).

**Skip-gram:** for each word in the text (the *centre*), predict the words within a window around it (the *context*).

```mermaid
flowchart LR
    S["the  [king]  rules  the  kingdom"] --> P1["(king, the)"]
    S --> P2["(king, rules)"]
    S --> P3["(king, the)"]
    P1 & P2 & P3 --> POS["positive pairs → push vectors together"]
    R["random words: 'fish', 'garden'"] --> NEG["negative pairs → push apart"]
```

Each word gets two vectors, *input* (centre) and *output* (context). For a pair, the model predicts $\sigma(v_{\text{centre}} \cdot u_{\text{context}})$: 1 for real pairs, 0 for random negatives. That is **logistic regression on a dot product** — the gradient is the familiar $(p - y)$ times the other vector.

After training, words that appear in similar contexts have similar input vectors. Famous analogies such as $v_{\text{king}} - v_{\text{man}} + v_{\text{woman}} \approx v_{\text{queen}}$ emerge on large corpora.

## Build it from scratch

<!-- code: lessons/p04-nlp-embeddings/src/main/java/dev/aieng/p04/Word2Vec.java#train -->

On a toy corpus about royalty and pets (`Word2Vec.main`):

```text
sim(king, queen) = 0.766
sim(dog, cat) = 0.700
sim(king, dog) = 0.330
sim(crown, fish) = 0.370
```

Nobody told the model that kings and queens are related — they appear in the same contexts ("rules the kingdom", "wears a crown"), so their vectors converge. The test `word2vecGroupsWordsByContext` asserts the ordering.

## Now with the library

You will not train word2vec in production; modern embedding models replace it. But the idea maps directly:

| word2vec | Modern sentence embedding model |
|---|---|
| One vector per word | One vector per text (sentence, paragraph, chunk) |
| Static (no context) | Contextual (transformer encoder, Phase 5) |
| Skip-gram objective | Contrastive objective on (query, relevant passage) pairs |
| Logistic regression with negatives | InfoNCE loss with in-batch negatives |

## Production notes

- **Static embeddings still have uses:** they are tiny and fast for features in classical models, and for keyword expansion ("synonyms" for BM25 queries).
- **Embeddings encode bias in the training text.** Analogy tests famously surface gender and other stereotypes; downstream systems (ranking candidates, routing tickets) can inherit them. Evaluate for it.
- **Domain matters.** Generic embeddings may not know that "DXP", "portlet" and "site" are related in a Liferay context. Domain fine-tuning of embedding models is cheap compared with LLM fine-tuning (Phase 13).

## Exercises

1. **Easy** — Add a sentence about "the prince rules the kingdom" to the corpus and check `sim(prince, king)`.
2. **Medium** — Implement `nearest(word, k)` returning the k most similar words, and print neighbours of "cat" and "crown".
3. **Hard** — Implement subsampling of frequent words (drop "the" and "a" with probability $1 - \sqrt{t/f(w)}$) and show that it improves the king/queen vs king/dog separation.

## Common mistakes

- Evaluating embeddings only on analogies; use downstream task metrics.
- Treating cosine similarity of 0.7 as universally "high" — scales differ per model and corpus.
- Forgetting that out-of-vocabulary words have no vector at all in word-level models (subword models like fastText fix this).

## Further reading

- Mikolov et al., "Distributed Representations of Words and Phrases and their Compositionality", NeurIPS 2013.
- Jay Alammar, "The Illustrated Word2vec" (2019).
- Bolukbasi et al., "Man is to Computer Programmer as Woman is to Homemaker?", NeurIPS 2016.
