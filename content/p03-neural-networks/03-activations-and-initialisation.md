---
title: Activations, Initialisation and Vanishing Gradients
summary: Why deep networks used to be untrainable, measured layer by layer — tanh vs ReLU vs GELU, and Xavier/He initialisation.
minutes: 50
prereqs: [p03-neural-networks/02-neurons-and-mlp]
tags: [neural-networks, initialisation, activations]
quiz:
  - q: With weights drawn at std 0.01 in a 10-layer tanh network, what happens to the activations?
    options:
      - They grow without bound
      - They shrink towards zero layer by layer (vanishing signal), and so do the gradients
      - They stay constant
      - They become exactly ±1
    answer: 1
    why: Each layer multiplies the signal's scale by roughly std·√fan_in = 0.01·16 = 0.16, so ten layers shrink it by ~0.16¹⁰.
  - q: Why does He initialisation use √(2/fan_in) for ReLU instead of Xavier's √(1/fan_in)?
    options:
      - ReLU is faster
      - ReLU zeroes about half of its inputs, halving the variance, so weights need twice the variance to compensate
      - ReLU outputs are negative
      - It is arbitrary
    answer: 1
    why: Keeping activation variance constant across layers requires compensating for the half of the signal ReLU removes.
  - q: Which activation do most modern transformers use in their feed-forward blocks?
    options: [Sigmoid, Step function, GELU or SwiGLU variants, Linear]
    answer: 2
    why: GELU (BERT, GPT-2) and gated variants like SwiGLU (Llama, Mistral) give smoother gradients than ReLU and train well at scale.
interview:
  - q: What techniques made very deep networks trainable?
    a: |
      Several, together: (1) variance-preserving initialisation (Xavier/Glorot, He/Kaiming); (2) non-saturating activations (ReLU and smooth successors like GELU) instead of sigmoid/tanh everywhere; (3) normalisation layers (BatchNorm, LayerNorm, RMSNorm) that re-standardise activations; (4) residual connections (x + f(x)), which give gradients an identity path through the network; and (5) adaptive optimisers like Adam with warm-up. Transformers rely on residuals plus LayerNorm/RMSNorm in every block.
    level: senior
---

## Why it matters

Until around 2010, networks deeper than a few layers barely trained. The fixes — careful initialisation, better activations, normalisation and residual connections — are all inside every transformer. When a fine-tuning run's loss refuses to move, these are the concepts you reach for.

## Concept

Each layer multiplies the signal by a weight matrix. If the weights are too small, the signal **shrinks** layer by layer; too large, and it **explodes** — or, for tanh and sigmoid, **saturates** at the flat ends where the derivative is ~0. Either way, gradients flowing backwards through the same layers vanish or explode too.

**Variance-preserving initialisation.** For a layer with `fan_in` inputs, choose weight standard deviation so output variance ≈ input variance:

| Scheme | Std | Use with |
|---|---|---|
| Xavier / Glorot | $1/\sqrt{\text{fan\_in}}$ | tanh, linear |
| He / Kaiming | $\sqrt{2/\text{fan\_in}}$ | ReLU family |

**Activation functions:**

| Function | Formula | Notes |
|---|---|---|
| Sigmoid | $1/(1+e^{-x})$ | Saturates; output layers for binary probability only |
| Tanh | $\tanh x$ | Zero-centred, still saturates |
| ReLU | $\max(0, x)$ | Cheap, no saturation for $x > 0$; "dead" neurons possible |
| GELU | $x \cdot \Phi(x)$ | Smooth ReLU; GPT-2, BERT |
| SwiGLU | $\text{Swish}(xW) \odot xV$ | Gated; Llama, Mistral, most current open LLMs |

## Build it from scratch

The experiment pushes 256 random inputs through ten 256-wide tanh layers and records the standard deviation of the activations:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Init.java#experiment -->

`Init.main` output:

```text
weight std 0.0100 -> layer 1: 0.1560, layer 5: 0.0001, layer 10: 0.0000   (vanishing)
weight std 0.0625 -> layer 1: 0.6276, layer 5: 0.3230, layer 10: 0.2296   (Xavier: alive)
weight std 0.5000 -> layer 1: 0.9488, layer 5: 0.9460, layer 10: 0.9464   (saturated)
```

The saturated row looks healthy — large activations — but almost every value is near $\pm 1$, where $\tanh' = 1 - \tanh^2 \approx 0$. Gradients die there just as surely as in the vanishing row. The test `xavierKeepsActivationsAliveWhileTinyWeightsVanish` pins all three behaviours.

## Now with the library

Frameworks choose sensible initialisers by default (DJL's `Linear` uses Xavier-style init; PyTorch's `nn.Linear` uses a Kaiming-uniform variant). Transformers add two more tools you will build in Phase 5:

- **Residual connections:** $y = x + f(x)$. The gradient of the identity path is 1, so signal and gradient can always flow.
- **Layer normalisation:** re-standardise each token's vector to mean 0 and variance 1 (RMSNorm skips the mean).

## Production notes

- **Loss stuck at its initial value** (e.g. $\ln(\text{classes})$ for cross-entropy) often means vanishing gradients, a too-low learning rate, or frozen parameters. Check gradient norms per layer.
- **Loss spikes or NaN** in fine-tuning: exploding gradients — lower the learning rate, add warm-up, clip gradient norms.
- **Precision matters.** Training in FP16 can underflow small gradients; BF16 (same exponent range as FP32) is the usual choice for LLM fine-tuning.

## Exercises

1. **Easy** — Run `Init.main` with depth 30. At which layer does each scheme fall below 0.01?
2. **Medium** — Add a `relu()` variant of the experiment and compare Xavier with He initialisation.
3. **Hard** — Add a residual connection (`x = x + layer(x)`) to the experiment and show that activations no longer vanish with small weights. (You will need an element-wise `add` on `Tensor`.)

## Common mistakes

- Initialising every weight to zero (all neurons stay identical forever).
- Using sigmoid in hidden layers of deep networks.
- Interpreting large activations as "healthy" without checking saturation.

## Further reading

- Glorot & Bengio, "Understanding the difficulty of training deep feedforward neural networks", AISTATS 2010.
- He et al., "Delving Deep into Rectifiers", ICCV 2015.
- Shazeer, "GLU Variants Improve Transformer" (2020), arXiv:2002.05202.
