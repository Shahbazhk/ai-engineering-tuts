---
title: Build an Autograd Engine
summary: A scalar that records how it was computed and can differentiate itself — reverse-mode automatic differentiation in about 150 lines of Java.
minutes: 70
prereqs: [p01-math/03-derivatives-and-gradients, p02-classical-ml/03-logistic-regression]
tags: [neural-networks, autograd, backpropagation]
quiz:
  - q: Why must gradients be accumulated with += rather than assigned with = in each node's backward step?
    options:
      - It is faster
      - A value used in several places receives gradient contributions from each use, and they must be summed
      - Assignment would overflow
      - Java requires it for doubles
    answer: 1
    why: The multivariable chain rule sums the contributions of every path. In f = a + a, df/da = 2; assignment would give 1.
  - q: Why is the graph walked in reverse topological order during backward()?
    options:
      - To save memory
      - So that a node's gradient is complete (all consumers have contributed) before it passes gradient to its own inputs
      - Because the forward pass is reversible
      - It is arbitrary
    answer: 1
    why: Each node can only propagate once its output gradient is final, which is guaranteed if all nodes that use it ran first.
  - q: For c = a · b, what does the backward step add to a.grad?
    options: ["c.grad", "b.data · c.grad", "a.data · c.grad", "1"]
    answer: 1
    why: ∂c/∂a = b, multiplied by the upstream gradient (chain rule).
  - q: What is "reverse-mode" autodiff efficient for?
    options:
      - Functions with many inputs and one output — like a loss over millions of weights
      - Functions with one input and many outputs
      - Only linear functions
      - Integer arithmetic
    answer: 0
    why: One backward pass yields the derivative of the single loss with respect to every parameter. Forward-mode would need one pass per parameter.
interview:
  - q: Explain backpropagation without equations.
    a: |
      During the forward pass every operation records its inputs and how to compute its local derivative. Backpropagation starts at the loss with "sensitivity 1" and walks the recorded graph backwards. Each operation receives how sensitive the loss is to its output, multiplies by its local derivative, and hands the result to its inputs; inputs used in several places sum what they receive. After one backward walk every parameter knows how much the loss would change if it were nudged — its gradient — at roughly the cost of one extra forward pass.
    level: senior
---

## Why it matters

Every model you will use was trained by backpropagation, and every framework — PyTorch, JAX, DJL — is built around an automatic differentiation engine. Building one yourself turns "the framework computes gradients" from magic into a data structure you understand. It also makes later topics concrete: why memory grows with sequence length during training, why LoRA saves memory, why `zero_grad()` exists.

## Concept

Each `Value` stores:

- `data` — the number,
- `grad` — $\partial L / \partial \text{this}$, filled in by backward,
- its **parents** (the inputs it was computed from),
- a small `backward` closure that applies the chain rule for *this one operation*.

```mermaid
flowchart LR
    a(("a = 2")) --> m["× → -6"]
    b(("b = -3")) --> m
    m --> p["+ → 4"]
    c(("c = 10")) --> p
    p --> f["× → 8 = f"]
    a --> f
```

For $f = (a \cdot b + c) \cdot a$ the backward pass walks right to left:

| Node | Local rule | Gradient |
|---|---|---|
| $f$ | seed | 1 |
| $p = ab + c$ | $\partial f / \partial p = a$ | 2 |
| $c$ | $\partial p / \partial c = 1$ | 2 |
| $m = ab$ | $\partial p / \partial m = 1$ | 2 |
| $b$ | $\partial m / \partial b = a$ | $2 \cdot 2 = 4$ |
| $a$ | via $m$: $b \cdot 2 = -6$; via $f$: $p = 4$ | $-6 + 4 = -2$ |

Note $a$: it is used twice, so its gradient is the **sum** of both paths — the reason every backward step uses `+=`.

## Build it from scratch

Each operation creates the output node and attaches its local derivative:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Value.java#ops -->

Backward sorts the graph topologically and runs the closures in reverse:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Value.java#backward -->

Three tests prove it:

- `valueGradientsMatchHandDerivation` — the table above.
- `reusedNodesAccumulateGradients` — $f = a + a$ gives $\partial f / \partial a = 2$.
- `valueGradientsPassNumericCheck` — a composite of `tanh`, `pow`, `exp`, `log` and `relu` checked against finite differences, the gradient-checking technique from Phase 1.

A fourth test builds a 50,000-node chain. A recursive topological sort would throw `StackOverflowError` — the reason the implementation uses an explicit stack. Real frameworks hit the same issue with very deep unrolled graphs.

## Now with the library

Every deep-learning library exposes the same three ideas — record, backward, read gradients:

```java
// DJL (reference snippet; DJL is not a build dependency)
try (NDManager nd = NDManager.newBaseManager();
     GradientCollector gc = Engine.getInstance().newGradientCollector()) {
    NDArray a = nd.create(2f); a.setRequiresGradient(true);
    NDArray b = nd.create(-3f); b.setRequiresGradient(true);
    NDArray c = nd.create(10f);
    NDArray f = a.mul(b).add(c).mul(a);
    gc.backward(f);
    System.out.println(a.getGradient());   // -2.0, same as our table
}
```

PyTorch: `a.requires_grad_()`, `f.backward()`, `a.grad`. Identical model.

## Production notes

- **Training memory ≈ weights + gradients + optimiser state + saved activations.** Backward needs the forward values (our closures capture `data`), so activation memory grows with batch size and sequence length. Gradient checkpointing trades compute for memory by recomputing them.
- **Inference needs no graph.** Frameworks offer "no-grad" modes because recording the graph costs memory and time — always disable it when serving.
- **LoRA's memory savings** come from only needing gradients and optimiser state for small adapter matrices (Phase 13).

## Exercises

1. **Easy** — Draw the graph for $f = \tanh(w x + b)$ and compute all gradients by hand for $w = 0.5$, $x = 2$, $b = -1$. Check with `Value`.
2. **Medium** — Add `sigmoid()` and `sub(double)` operations with correct backward closures, plus numeric gradient tests.
3. **Hard** — Add a `toDot()` method that outputs the graph in Graphviz DOT format with data and grad in each node, and render the graph of a 2-2-1 MLP.

## Common mistakes

- Using `=` instead of `+=` in backward closures.
- Calling `backward()` twice without resetting gradients (the code resets graph gradients, but parameters in a training loop need explicit zeroing — next lessons).
- Forgetting that `relu`'s derivative at exactly 0 is a convention (0 here).

## Further reading

- Andrej Karpathy, "The spelled-out intro to neural networks and backpropagation: building micrograd" (video, 2022) and <https://github.com/karpathy/micrograd>.
- Baydin et al., "Automatic Differentiation in Machine Learning: a Survey", JMLR 18 (2018).
