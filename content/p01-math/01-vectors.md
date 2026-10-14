---
title: Vectors, Dot Products and Cosine Similarity
summary: The data structure behind embeddings, weights and attention — and the one-line operation that powers semantic search.
minutes: 45
prereqs: [p00-setup/04-how-to-use-this-course]
tags: [math, vectors, embeddings]
quiz:
  - q: Two embedding vectors have cosine similarity 0.95. What does that tell you?
    options:
      - They have almost the same length
      - They point in almost the same direction, so the texts are semantically similar
      - They are 95% identical byte-for-byte
      - One is 0.95 times the other
    answer: 1
    why: Cosine measures the angle only. Magnitude is ignored, which is why it is the default for comparing embeddings.
  - q: Why do vector databases often L2-normalise vectors at insert time?
    options:
      - To save disk space
      - Because after normalisation the dot product equals cosine similarity, which is cheaper to compute
      - Because embedding models require it
      - To remove negative numbers
    answer: 1
    why: For unit vectors, a·b = |a||b|cos θ = cos θ. Computing one dot product is cheaper than a dot product plus two norms.
  - q: What is the dot product of [1, 2, 3] and [4, 5, 6]?
    options: ["21", "32", "15", "[4, 10, 18]"]
    answer: 1
    why: 1·4 + 2·5 + 3·6 = 4 + 10 + 18 = 32. The element-wise product [4, 10, 18] is a different operation (Hadamard product).
interview:
  - q: When would you choose Euclidean distance over cosine similarity for embeddings?
    a: |
      Cosine ignores magnitude and compares direction, which is what most text-embedding models are trained for, so it is the default. If vectors are L2-normalised, Euclidean distance and cosine produce the same ranking (|a−b|² = 2 − 2cos θ), so the choice stops mattering. Use Euclidean (or dot product without normalisation) only when the model documentation says magnitude carries meaning — some models encode confidence or popularity in vector length. Always match the metric the embedding model was trained with.
    level: senior
---

## Why it matters

An **embedding** is a list of numbers — typically 384 to 3,072 of them — that places a piece of text in a space where similar meanings are close together. A **model weight matrix** is a stack of such lists. **Attention** compares them pairwise. If you understand vectors and the dot product, you understand the core operation of modern AI.

## Concept

A vector is an arrow with a direction and a length. In code it is a `double[]`.

- **Dot product** $a \cdot b = \sum_i a_i b_i$ — large and positive when vectors point the same way, zero when perpendicular, negative when opposite.
- **Norm (length)** $\lVert a \rVert = \sqrt{a \cdot a}$.
- **Cosine similarity** $\cos\theta = \dfrac{a \cdot b}{\lVert a \rVert \, \lVert b \rVert}$ — the dot product with length divided out. Always in $[-1, 1]$.

```mermaid
flowchart LR
    Q["'How do I configure a DataSource?'"] --> E[Embedding model]
    D1["'Spring Boot DataSource properties'"] --> E
    D2["'Chocolate cake recipe'"] --> E
    E --> V1["q = [0.12, -0.40, …]"]
    E --> V2["d1 = [0.10, -0.38, …]"]
    E --> V3["d2 = [-0.55, 0.21, …]"]
    V1 & V2 -.cos ≈ 0.89.-> S1[relevant]
    V1 & V3 -.cos ≈ 0.04.-> S2[irrelevant]
```

Geometric intuition in 2D: $a = (3, 4)$ and $b = (4, 3)$ have length 5 each and $a \cdot b = 24$, so $\cos\theta = 24/25 = 0.96$ — nearly the same direction. Embeddings work the same way in 768 dimensions; you just can't draw it.

## Build it from scratch

<!-- code: lessons/p01-math/src/main/java/dev/aieng/p01/Vectors.java#core -->

Two details worth noticing:

1. `normalize` divides by the norm. After that, **dot product equals cosine similarity** — the test `normalisedDotEqualsCosine` proves it. Vector databases use this to replace a cosine computation with one dot product.
2. Dimension mismatches throw. Mixing vectors from two different embedding models (say, 768-dim and 1,536-dim) is a real production bug; mixing two models that happen to have the *same* dimension is worse, because nothing throws and the results are just wrong.

## Now with the library

Spring AI's `EmbeddingModel` returns exactly this structure — a `float[]` per input:

```java
float[] v = embeddingModel.embed("How do I configure a DataSource?");  // e.g. 768 floats for nomic-embed-text
```

The course app's semantic search (`app/.../search/SemanticIndex.java`) normalises every section embedding once at startup and then ranks with a plain dot product — the code in this lesson, applied to real data. Try the **Embedding similarity** tab in the Playground: paraphrases score high without sharing words.

## Production notes

- **Use `float`, not `double`, for stored embeddings.** Half the memory, and embedding models are not that precise anyway. A million 768-dim float vectors is ~3 GB; quantising to `int8` (Phase 13) brings it to ~770 MB.
- **Store the model name and version with every vector.** Re-embedding is required whenever the model changes.
- **Cosine thresholds are model-specific.** 0.8 might mean "near duplicate" for one model and "loosely related" for another. Calibrate on your own data; never hard-code a threshold from a blog post.

## Exercises

1. **Easy** — Compute by hand the cosine similarity of $(1, 0)$ and $(1, 1)$. Verify with `Vectors.cosine`.
2. **Medium** — Prove that for unit vectors $\lVert a - b \rVert^2 = 2 - 2\cos\theta$, then write a test that checks it on random vectors.
3. **Hard** — Generate 10,000 random 768-dim vectors and measure the distribution of cosine similarities between random pairs. Why does it cluster tightly around 0? (Hint: concentration of measure in high dimensions — this is why "0.3" can be a meaningful similarity for some models.)

## Common mistakes

- Comparing cosine scores across different embedding models.
- Forgetting that `cosine` of a zero vector is undefined (the code returns 0 by convention — decide explicitly in your own code).
- Using `double[]` everywhere in a hot loop when `float[]` would halve memory bandwidth.

## Further reading

- 3Blue1Brown, *Essence of Linear Algebra* (video series) — the best visual intuition available.
- Gilbert Strang, *Introduction to Linear Algebra*, chapter 1.
