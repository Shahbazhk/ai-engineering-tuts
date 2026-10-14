---
title: From Scalars to Tensors
summary: Vectorise the autograd engine — one graph node per matrix operation, the matmul backward identities, broadcasting and a fused softmax cross-entropy.
minutes: 60
prereqs: [p03-neural-networks/03-activations-and-initialisation]
tags: [neural-networks, tensors, autograd, performance]
quiz:
  - q: For C = A @ B, what is the gradient of the loss with respect to A?
    options: ["dC @ B", "dC @ Bᵀ", "Aᵀ @ dC", "B @ dC"]
    answer: 1
    why: dL/dA = dL/dC @ Bᵀ and dL/dB = Aᵀ @ dL/dC. Checking shapes is the easiest way to remember which transpose goes where.
  - q: A bias row (1 × m) is broadcast-added to every row of an (n × m) matrix. What is the bias gradient?
    options:
      - The output gradient's first row
      - The column-wise sum of the output gradient over all n rows
      - The mean of the output gradient
      - Zero
    answer: 1
    why: The bias was used n times (once per row); gradients from every use are summed.
  - q: Why fuse softmax and cross-entropy into one operation?
    options:
      - It looks nicer
      - The combined gradient is simply (softmax − onehot)/batch, and it avoids computing log of tiny probabilities
      - It reduces model accuracy
      - It is required by Java
    answer: 1
    why: Fusing is both faster and numerically safer; every framework provides a fused cross-entropy for this reason.
interview:
  - q: Why are deep-learning frameworks built around tensors instead of scalar autograd?
    a: |
      Overhead and hardware. A scalar graph creates one object and closure per arithmetic operation — billions per training step for a real model — so bookkeeping dwarfs the math. A tensor node represents a whole matrix operation, so the graph has hundreds of nodes and each forward/backward step is one large kernel (BLAS on CPU, cuBLAS/tensor cores on GPU) that the hardware executes near peak efficiency. The gradient rules are the same chain rule, expressed as matrix identities like dA = dC·Bᵀ.
    level: senior
---

## Why it matters

The scalar engine is perfect for understanding and hopeless for performance. Real frameworks operate on **tensors** — n-dimensional arrays — so that one node in the graph is a whole matrix multiply. The backward rules become matrix identities you will meet in every paper and every framework's source code.

## Concept

A 2-D tensor is a matrix with a gradient matrix of the same shape. Operations and their backward rules:

| Forward | Backward |
|---|---|
| $C = AB$ | $\nabla_A = \nabla_C B^\top$, $\;\nabla_B = A^\top \nabla_C$ |
| $Y = X + \mathbf{1} b$ (broadcast bias row) | $\nabla_X = \nabla_Y$, $\;\nabla_b = \sum_{\text{rows}} \nabla_Y$ |
| $Y = \mathrm{relu}(X)$ | $\nabla_X = \nabla_Y \odot [X > 0]$ |
| $L = \frac{1}{n}\sum_r \mathrm{CE}(\mathrm{softmax}(Z_r), y_r)$ | $\nabla_Z = (\mathrm{softmax}(Z) - \mathrm{onehot}(y)) / n$ |

**Shape check trick:** $A$ is $(n \times k)$, $\nabla_C$ is $(n \times m)$, $B$ is $(k \times m)$. The only product of those that yields $(n \times k)$ is $\nabla_C B^\top$.

## Build it from scratch

Matrix multiply with its backward closure — both gradients computed in the same loop nest:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Tensor.java#matmul -->

The fused loss:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Tensor.java#loss -->

The test `tensorGradientsPassNumericCheck` builds `tanh(x @ w + b)` → softmax cross-entropy and checks **every element** of the gradients of `x`, `w` and `b` against finite differences. Whenever you add an operation, add it to that check.

## Now with the library

| This course | DJL | PyTorch |
|---|---|---|
| `Tensor` | `NDArray` | `torch.Tensor` |
| `matmul` | `a.matMul(b)` | `a @ b` |
| `addRow` (broadcast) | `a.add(b)` | `a + b` |
| `softmaxCrossEntropy` | `SoftmaxCrossEntropyLoss` | `F.cross_entropy` |
| `backward()` | `GradientCollector.backward` | `loss.backward()` |

Real tensors are n-dimensional with general broadcasting, run on GPUs, and use highly tuned kernels — but the semantics match what you built.

## Production notes

- **Batching amortises overhead.** A batch of 32 rows costs barely more than one row on parallel hardware; that is why inference servers batch requests.
- **Shapes are the API.** Most bugs in model code are shape bugs. Assert shapes at boundaries, as `matmul` does.
- **Memory layout matters.** Row-major storage and contiguous access patterns (Phase 1, lesson 2) apply directly to tensor kernels.

## Exercises

1. **Easy** — Write out the shapes for `x @ w1 + b1` with a batch of 32, 2 inputs and 64 hidden units, and for each gradient.
2. **Medium** — Add element-wise `add(Tensor)` and `mul(Tensor)` operations (same shapes) with backward rules and numeric checks.
3. **Hard** — Add `layerNorm()` (normalise each row to mean 0, variance 1, with learnable gain and bias). Deriving its backward pass is a classic interview exercise — gradient-check it.

## Common mistakes

- Swapping the transposes in the matmul backward.
- Forgetting to sum the bias gradient over the batch.
- Computing `softmax` and then `log` separately.

## Further reading

- Justin Johnson, "Backpropagation for a Linear Layer" (Stanford CS231n notes).
- DJL NDArray API: <https://javadoc.io/doc/ai.djl/api/latest/ai/djl/ndarray/NDArray.html>
