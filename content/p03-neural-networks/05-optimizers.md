---
title: Optimisers — SGD, Momentum, Adam and AdamW
summary: The update rules behind every training run and fine-tune, implemented over tensors, with the hyperparameters you will see in real configs.
minutes: 45
prereqs: [p03-neural-networks/04-tensors]
tags: [neural-networks, optimisers, adam, fine-tuning]
quiz:
  - q: What does Adam keep for each parameter?
    options:
      - Only the last gradient
      - Running averages of the gradient (m) and of the squared gradient (v)
      - A copy of the training data
      - A separate learning rate chosen by the user per parameter
    answer: 1
    why: m gives a smoothed direction, v a per-parameter scale. The step is m / √v, so each parameter effectively gets its own step size.
  - q: Why does Adam apply bias correction (dividing by 1 − β^t)?
    options:
      - To make training deterministic
      - Because m and v start at zero and are biased towards zero in the first steps
      - To implement weight decay
      - To clip gradients
    answer: 1
    why: Without correction, early steps would be much too small. The correction factor approaches 1 as t grows.
  - q: How much optimiser memory does Adam need compared with the model weights (in the same precision)?
    options: ["None", "About the same", "About twice the weights (m and v)", "Ten times"]
    answer: 2
    why: Two extra values per parameter. For a 7B model in FP32 that is 56 GB of optimiser state — one reason parameter-efficient fine-tuning exists.
interview:
  - q: What is the difference between Adam with L2 regularisation and AdamW?
    a: |
      With Adam + L2, the penalty's gradient λw is added to the gradient and then divided by √v like everything else, so parameters with large gradient history get less regularisation — the decay is coupled to the adaptive scaling. AdamW (Loshchilov & Hutter) decouples it: the adaptive step uses only the loss gradient, and weight decay is applied directly as w ← w − lr·λ·w. This behaves like true weight decay, generalises better and is the default for transformer training and fine-tuning.
    level: senior
---

## Why it matters

Fine-tuning configs are full of optimiser settings: `learning_rate=2e-4`, `optim="adamw_torch"`, `weight_decay=0.01`, `warmup_ratio=0.03`, `lr_scheduler_type="cosine"`. This lesson makes each one concrete — and explains why optimiser state dominates GPU memory during full fine-tuning.

## Concept

All optimisers consume the gradient $g_t$ produced by backward and update the parameters $\theta$.

**SGD with momentum:**

$$v_t = \mu v_{t-1} + g_t, \qquad \theta_t = \theta_{t-1} - \eta\, v_t$$

**Adam:**

$$m_t = \beta_1 m_{t-1} + (1-\beta_1) g_t, \qquad v_t = \beta_2 v_{t-1} + (1-\beta_2) g_t^2$$

$$\hat{m}_t = \frac{m_t}{1-\beta_1^t}, \qquad \hat{v}_t = \frac{v_t}{1-\beta_2^t}, \qquad \theta_t = \theta_{t-1} - \eta \frac{\hat{m}_t}{\sqrt{\hat{v}_t} + \epsilon}$$

**AdamW** adds decoupled weight decay: $\theta_t \mathrel{-}= \eta \lambda \theta_{t-1}$.

Typical values: $\beta_1 = 0.9$, $\beta_2 = 0.999$ (often 0.95 for LLMs), $\epsilon = 10^{-8}$, weight decay 0.01–0.1.

## Build it from scratch

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Optimizers.java#sgd -->

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Optimizers.java#adam -->

The optimiser is a small strategy object holding per-parameter state in an identity map — the same design as PyTorch's `optimizer.state`. The test `optimizersReduceAQuadratic` checks both drive a simple quadratic to its minimum.

## Now with the library

| Concept | Hugging Face / PyTorch config | DJL |
|---|---|---|
| Optimiser | `optim="adamw_torch"` | `Optimizer.adamW()` |
| Learning rate | `learning_rate=2e-4` | `.optLearningRateTracker(...)` |
| Schedule | `lr_scheduler_type="cosine"`, `warmup_ratio=0.03` | `Tracker.cosine(...)`, `Tracker.warmUp(...)` |
| Weight decay | `weight_decay=0.01` | `.optWeightDecays(0.01f)` |
| 8-bit optimiser state | `optim="paged_adamw_8bit"` | — |

## Production notes

- **Memory arithmetic for full fine-tuning (mixed precision):** weights (2 bytes) + gradients (2) + FP32 master weights (4) + Adam m and v (8) ≈ **16 bytes per parameter**. A 7B model needs ~112 GB before activations. LoRA and QLoRA (Phase 13) cut this to a few GB.
- **Warm-up** avoids huge early steps while $v$ is still poorly estimated.
- **Learning rate dominates.** If a fine-tune behaves oddly, sweep the learning rate before touching anything else.

## Exercises

1. **Easy** — Compute Adam's first update by hand for $g_1 = 0.5$, $\eta = 0.01$. (Hint: after bias correction the first step is $\approx \eta \cdot \mathrm{sign}(g)$.)
2. **Medium** — Implement a cosine learning-rate schedule with linear warm-up as a `DoubleUnaryOperator` of the step number, and plug it into `adamW`.
3. **Hard** — Implement gradient-norm clipping (scale all gradients so their global L2 norm is at most 1.0) and show that it prevents divergence with a too-high learning rate on the spiral task.

## Common mistakes

- Forgetting `zeroGrad` — gradients from previous batches accumulate.
- Applying weight decay to biases and normalisation parameters (usually excluded).
- Copying a learning rate from a full fine-tune into a LoRA run or vice versa.

## Further reading

- Kingma & Ba, "Adam: A Method for Stochastic Optimization", ICLR 2015.
- Loshchilov & Hutter, "Decoupled Weight Decay Regularization", ICLR 2019.
