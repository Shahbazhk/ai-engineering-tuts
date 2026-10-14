---
title: Self-Attention from Scratch
summary: Queries, keys and values; scaled dot-product attention; the causal mask — the operation that lets every token read its whole context.
minutes: 60
prereqs: [p05-transformers/01-language-modelling]
tags: [transformers, attention]
quiz:
  - q: In Attention(Q, K, V) = softmax(QKᵀ/√d_k)V, what does each row of the softmax output represent?
    options:
      - The embedding of a token
      - How much one query position attends to every key position — a probability distribution summing to 1
      - The gradient of the loss
      - The positional encoding
    answer: 1
    why: Each row is a set of attention weights used to take a weighted average of the value vectors.
  - q: Why divide the scores by √d_k?
    options:
      - To normalise the output length
      - Dot products of random d_k-dimensional vectors have variance ~d_k; without scaling, softmax saturates and gradients vanish
      - To make attention causal
      - It is a historical accident with no effect
    answer: 1
    why: Scaling keeps the score variance around 1 so softmax stays in a region with useful gradients.
  - q: What does the causal mask do in a decoder like GPT?
    options:
      - Hides padding tokens
      - Sets scores for future positions to −∞ so each token only attends to itself and earlier tokens
      - Randomly drops attention weights
      - Masks the system prompt
    answer: 1
    why: During training, every position predicts its next token in parallel; the mask prevents it from cheating by looking ahead.
  - q: What is the time and memory complexity of standard attention over a sequence of n tokens?
    options: ["O(n)", "O(n log n)", "O(n²)", "O(1)"]
    answer: 2
    why: Every query is compared with every key. This quadratic cost is why long contexts are expensive and why tricks like FlashAttention matter.
interview:
  - q: Explain attention with an analogy a backend engineer would appreciate.
    a: |
      It is a soft, differentiable hash-map lookup. Each token emits a query ("what am I looking for?"), a key ("what do I contain?") and a value ("what will I contribute if selected?"). Instead of an exact key match returning one value, the query is compared with every key by dot product, softmax turns the scores into weights, and the result is a weighted average of all values. Because it is a weighted average rather than a discrete lookup, it is differentiable, so the projections that produce queries, keys and values can be learned. Self-attention means queries, keys and values all come from the same sequence.
    level: senior
---

## Why it matters

Attention is the mechanism that replaced recurrent networks and made LLMs possible. It is also the source of the context-window limits, the quadratic cost of long prompts and the KV cache that dominates inference memory. Every architecture discussion about LLMs eventually comes back to it.

## Concept

From each token's vector $x_i$, three learned projections produce:

- a **query** $q_i = x_i W_Q$ — what this token is looking for,
- a **key** $k_i = x_i W_K$ — what this token offers,
- a **value** $v_i = x_i W_V$ — what this token passes on if attended to.

$$\mathrm{Attention}(Q, K, V) = \mathrm{softmax}\!\left(\frac{QK^\top}{\sqrt{d_k}} + \text{mask}\right) V$$

```mermaid
flowchart LR
    X["token vectors X (n × d)"] --> Q["Q = X·Wq"]
    X --> K["K = X·Wk"]
    X --> V["V = X·Wv"]
    Q & K --> S["scores = Q·Kᵀ / √dk  (n × n)"]
    S --> MASK["causal mask: future = −∞"]
    MASK --> SM["softmax per row → weights"]
    SM & V --> O["output = weights · V  (n × dv)"]
```

- **Why $\sqrt{d_k}$:** the dot product of two random vectors with unit-variance components has variance $d_k$. Dividing keeps scores near unit variance, so softmax does not saturate (Phase 3's vanishing-gradient lesson again).
- **Causal mask:** in a decoder, position $i$ may only see positions $\le i$. Setting future scores to $-\infty$ makes their softmax weight exactly 0. This lets training compute the loss for *every* position in parallel without cheating.
- **Cost:** the score matrix is $n \times n$. Doubling the context quadruples attention compute and memory.

## Build it from scratch

<!-- code: lessons/p05-transformers/src/main/java/dev/aieng/p05/Attention.java#attention -->

The `offset` handles the KV-cache case (lesson 5), where a few new queries attend to many cached keys.

Tests pin the key properties:

- every row of weights sums to 1 and future weights are exactly 0;
- the first token can only attend to itself, so its output equals its own value vector;
- **without positional information, attention is permutation-equivariant** — shuffle the input and the outputs shuffle the same way. Attention alone cannot tell "dog bites man" from "man bites dog". Lesson 3 fixes that.

## Now with the library

Production implementations compute the same function with very different engineering:

| Technique | What it changes |
|---|---|
| FlashAttention | Never materialises the n × n matrix; tiles the computation in fast on-chip memory. Same result, far less memory traffic |
| Multi-query / grouped-query attention | Shares keys/values across heads to shrink the KV cache (lesson 7) |
| Sliding-window attention | Each token attends only to the last w tokens (Mistral), making cost linear in n |

You never call attention directly from Spring AI, but its cost model explains API pricing and latency: input tokens are processed in parallel (prefill), and longer prompts make every generated token slightly slower.

## Production notes

- **Long prompts are not free.** Prefill cost grows with prompt length, and attention over a long context makes each decode step slower too.
- **"Lost in the middle":** models use information at the start and end of long contexts more reliably than in the middle. Put the most important instructions and evidence first or last (Phase 7).
- **Attention weights are not explanations.** They show where information was mixed, not why a decision was made — be careful presenting them as interpretability.

## Exercises

1. **Easy** — For $q = [1, 0]$ and keys $k_1 = [1, 0]$, $k_2 = [0, 1]$, compute the attention weights by hand (with $d_k = 2$).
2. **Medium** — Remove the $\sqrt{d_k}$ scaling and measure the entropy of the attention rows for $d_k = 4$ and $d_k = 256$ with random inputs. Explain the result.
3. **Hard** — Implement attention's backward pass (gradients for Q, K and V) and gradient-check it.

## Common mistakes

- Forgetting the causal mask in a decoder (the model "sees the answer" during training).
- Masking with a large negative number instead of $-\infty$ in low precision, which can leak probability.
- Confusing self-attention (Q, K, V from the same sequence) with cross-attention (Q from one sequence, K and V from another, as in encoder–decoder models).

## Further reading

- Vaswani et al., "Attention Is All You Need", NeurIPS 2017.
- Jay Alammar, "The Illustrated Transformer" (2018).
- Dao et al., "FlashAttention", NeurIPS 2022.
