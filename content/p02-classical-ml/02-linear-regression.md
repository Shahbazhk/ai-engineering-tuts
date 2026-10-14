---
title: Linear Regression from Scratch
summary: Model, loss and optimiser in their simplest form — and proof that gradient descent finds the same answer as the closed-form solution.
minutes: 45
prereqs: [p02-classical-ml/01-what-is-learning]
tags: [ml, regression, gradient-descent]
quiz:
  - q: What is the gradient of MSE with respect to a weight w_j for one example?
    options: ["2·(ŷ − y)", "2·(ŷ − y)·x_j", "(ŷ − y)²", "x_j"]
    answer: 1
    why: L = (ŷ − y)² and ŷ = w·x + b, so by the chain rule ∂L/∂w_j = 2(ŷ − y)·x_j.
  - q: R² = 0 on a test set means…
    options:
      - The model is perfect
      - The model is no better than always predicting the mean of y
      - The model predicts zero everywhere
      - The data is linear
    answer: 1
    why: R² = 1 − SS_res/SS_tot. If SS_res equals the variance around the mean, R² is 0. It can even be negative on test data.
  - q: Why learn the gradient-descent version when a closed-form solution exists?
    options:
      - The closed form is wrong
      - Neural networks have no closed form; gradient descent is the general method
      - Gradient descent is always faster
      - The closed form needs a GPU
    answer: 1
    why: Least squares is a special case with an analytic solution. For anything non-linear, iterative optimisation is the only option.
interview:
  - q: When is a linear model the right choice in an AI product today?
    a: |
      More often than people think: as a baseline that every complex model must beat; when you need a model that is explainable coefficient by coefficient (pricing, credit, regulated decisions); for very small data; for real-time scoring with microsecond latency; and as a "head" on top of embeddings — a logistic regression over sentence embeddings is a strong, cheap text classifier that is easy to retrain. Start simple, measure, and only add complexity when the evaluation shows a gap.
    level: senior
---

## Why it matters

Linear regression has every ingredient of deep learning — a parameterised model, a loss and an optimiser — with nothing else in the way. Once you can write this loop from memory, a neural network is "the same loop with a bigger model and automatic gradients".

## Concept

**Model:** $\hat{y} = w \cdot x + b$

**Loss (mean squared error):**

$$L(w, b) = \frac{1}{n} \sum_{i=1}^{n} (\hat{y}_i - y_i)^2$$

**Gradients** (chain rule from Phase 1):

$$\frac{\partial L}{\partial w_j} = \frac{2}{n} \sum_i (\hat{y}_i - y_i)\, x_{ij} \qquad \frac{\partial L}{\partial b} = \frac{2}{n} \sum_i (\hat{y}_i - y_i)$$

Read the shape of these formulas: *(prediction − target) × input*. You will see it again in logistic regression and in the output layer of every neural network.

**Closed form (one feature):** $w = \mathrm{cov}(x, y) / \mathrm{var}(x)$ and $b = \bar{y} - w\bar{x}$.

## Build it from scratch

<!-- code: lessons/p02-classical-ml/src/main/java/dev/aieng/p02/LinearRegression.java#fit -->

The data comes from $y = 3x + 2 + \text{noise}$. The test `linearRegressionRecoversTheTrueLine` checks three things:

1. Gradient descent recovers $w \approx 3$, $b \approx 2$.
2. It matches the closed-form solution to within 0.01.
3. $R^2 > 0.98$.

Try changing the learning rate in the test from 0.01 to 0.05: the features range from 0 to 10, so the loss surface is steep in $w$ and training diverges. Standardising features (previous lesson) is what makes one learning rate work for all parameters.

## Now with the library

```java
// reference snippet — Tribuo linear regression with SGD
var trainer = new LinearSGDTrainer(new SquaredLoss(), new AdaGrad(0.1), 10, 1L);
Model<Regressor> model = trainer.train(trainingData);
```

Or, for embeddings-based prediction inside a Spring service, a hand-rolled linear head like this lesson's is often all you need: a few hundred lines of plain Java, no dependency, trivially serialisable.

## Production notes

- **Always build a baseline.** "Predict the mean" and "linear model" are the first two rows of any model comparison table.
- **Inspect coefficients.** A large weight on a feature that should not matter often reveals leakage.
- **Watch for drift.** Linear models degrade predictably when input distributions shift; monitor feature means over time.

## Exercises

1. **Easy** — Add a `predictAll(Dataset)` method and compute MSE and R² on a held-out split.
2. **Medium** — Implement mini-batch gradient descent (batches of 32, shuffled each epoch) and compare convergence with the full-batch version.
3. **Hard** — Implement the multi-feature closed form $w = (X^\top X)^{-1} X^\top y$ with `Matrix` from Phase 1 and Gaussian elimination. Compare with gradient descent when two features are almost identical — what happens to the inverse?

## Common mistakes

- Unscaled features with one shared learning rate.
- Interpreting coefficients causally ("feature X causes Y") — regression shows association only.
- Evaluating on the training data.

## Further reading

- Gareth James et al., *An Introduction to Statistical Learning* (free), chapter 3.
