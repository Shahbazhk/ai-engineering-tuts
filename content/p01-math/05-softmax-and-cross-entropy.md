---
title: Softmax, Entropy and Cross-Entropy
summary: How raw scores become probabilities, how temperature reshapes them, and the loss function that trains every LLM.
minutes: 50
prereqs: [p01-math/04-probability]
tags: [math, softmax, loss, temperature]
quiz:
  - q: Why does a numerically stable softmax subtract max(z) from every logit first?
    options:
      - It makes the probabilities more accurate in theory
      - It prevents exp() from overflowing to Infinity, and it cancels out in the ratio so the result is unchanged
      - It sharpens the distribution
      - It is required for cross-entropy
    answer: 1
    why: exp(1000) overflows a double. exp(z − max) is at most exp(0) = 1, and the common factor exp(−max) cancels between numerator and denominator.
  - q: A model's average cross-entropy loss on held-out text is ln(8) ≈ 2.08 nats. What is its perplexity?
    options: ["2.08", "8", "0.125", "ln(2.08)"]
    answer: 1
    why: Perplexity = exp(average cross-entropy) = 8. The model is as uncertain as if choosing uniformly among 8 tokens.
  - q: What happens to softmax(z / T) as temperature T approaches 0?
    options:
      - It becomes uniform
      - It puts nearly all probability on the largest logit (greedy decoding)
      - It returns the logits unchanged
      - It becomes undefined for all inputs
    answer: 1
    why: Dividing by a tiny T magnifies differences between logits, so the largest one dominates.
  - q: Which loss is minimised during LLM pre-training?
    options: [Mean squared error, Cross-entropy of the next token, Hinge loss, KL divergence to a uniform distribution]
    answer: 1
    why: Pre-training is next-token prediction with cross-entropy loss, averaged over all positions in the training text.
interview:
  - q: Explain temperature to a product manager, and then precisely to an engineer.
    a: |
      Product manager: temperature controls how adventurous the model is — low gives the most likely wording every time, high gives more varied and creative but riskier output. Engineer: the model produces logits z over the vocabulary; sampling uses softmax(z/T). T < 1 scales logits up, sharpening the distribution towards the arg-max; T > 1 flattens it towards uniform; T → 0 is greedy decoding. It does not change what the model knows — only how the next token is drawn — so it cannot fix factual errors, and high T raises the chance of low-probability (often wrong) tokens.
    level: senior
---

## Why it matters

At the end of every forward pass an LLM produces one raw score — a **logit** — per vocabulary token. **Softmax** turns those into probabilities. **Cross-entropy** measures how much probability the model gave to the token that actually came next; minimising it over trillions of tokens is what "training an LLM" means. **Temperature** is a knob on softmax you set in every API call.

## Concept

**Softmax.**

$$\mathrm{softmax}(z)_i = \frac{e^{z_i}}{\sum_j e^{z_j}}$$

Every output is positive and they sum to 1. Larger logits get exponentially more probability.

**Temperature.** Divide logits by $T$ before softmax: $\mathrm{softmax}(z / T)$.

| T | Effect on logits [1, 2] | Probabilities |
|---|---|---|
| 0.1 | [10, 20] | ≈ [0.00005, 0.99995] — near greedy |
| 1.0 | [1, 2] | [0.27, 0.73] |
| 10 | [0.1, 0.2] | [0.475, 0.525] — near uniform |

**Entropy** — the average surprise of a distribution:

$$H(p) = -\sum_i p_i \log p_i$$

Zero when one outcome is certain; $\log n$ when all $n$ are equally likely.

**Cross-entropy loss** for one example with correct class $y$:

$$L = -\log \mathrm{softmax}(z)_y$$

If the model gave the right token probability 1, the loss is 0. If it gave it 0.01, the loss is 4.6. **Perplexity** $= e^{\bar{L}}$ is the same quantity on a more intuitive scale: "effectively choosing among N tokens".

**KL divergence** $D_{KL}(p \,\|\, q) = \sum_i p_i \log \frac{p_i}{q_i}$ measures how different two distributions are. It appears in distillation (Phase 13) and in RLHF, which keeps a fine-tuned model close to its base model.

## Build it from scratch

<!-- code: lessons/p01-math/src/main/java/dev/aieng/p01/Information.java#softmax -->

Note `logSoftmax`: computing `Math.log(softmax(z))` loses precision when probabilities are tiny (and gives `-Infinity` at 0). The log-sum-exp form stays accurate. Every framework's cross-entropy uses it.

<!-- code: lessons/p01-math/src/main/java/dev/aieng/p01/Information.java#entropy -->

The tests check the known values: uniform logits over 4 classes give loss $\ln 4$ and perplexity exactly 4; logits of 1000+ do not overflow; low temperature is nearly greedy.

## Now with the library

You see these quantities in practice through API parameters and responses:

```java
// Lower temperature for deterministic extraction tasks
String json = chatClient.prompt()
        .user("Extract the invoice number from: ...")
        .options(ChatOptions.builder().temperature(0.0))
        .call()
        .content();
```

Some providers also return **log-probabilities** of generated tokens (`logprobs`). They are $\log \mathrm{softmax}(z)_y$ for each chosen token — useful for confidence heuristics and classification-by-LLM.

## Production notes

- **Temperature is not a creativity dial for facts.** It changes the sampling, not the model's knowledge.
- **Use log-probabilities for cheap confidence signals** — for example, flag classifications where the top label's probability is below 0.7. Calibrate on your own data first.
- **Perplexity is a model-level metric,** useful when comparing fine-tuned checkpoints on your domain text; it says little about whether answers are helpful.

## Exercises

1. **Easy** — Compute softmax([2, 1, 0]) by hand to two decimals, then with `Information.softmax`.
2. **Medium** — Plot (as a text table) the entropy of softmax([1, 2, 3]) for temperatures 0.1, 0.5, 1, 2, 5. Explain the trend.
3. **Hard** — Show mathematically that the gradient of cross-entropy with respect to the logits is $\mathrm{softmax}(z) - \mathrm{onehot}(y)$. Verify it numerically with `Gradients.gradient`. This one-line gradient is why softmax + cross-entropy is used everywhere.

## Common mistakes

- Applying softmax twice (once in the model, once in the loss).
- Computing `log(softmax(x))` instead of `logSoftmax(x)`.
- Comparing perplexities between models with different tokenizers — the numbers are not on the same scale.

## Further reading

- Christopher Olah, "Visual Information Theory" (colah.github.io, 2015).
- Goodfellow, Bengio & Courville, *Deep Learning*, section 6.2.2.
