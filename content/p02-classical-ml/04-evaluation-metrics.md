---
title: Evaluation Metrics That Match the Business
summary: Confusion matrices, precision, recall, F1, ROC AUC, MSE and R² — and how to choose the metric that reflects real costs.
minutes: 45
prereqs: [p02-classical-ml/03-logistic-regression]
tags: [ml, evaluation, metrics]
quiz:
  - q: A model predicts "not fraud" for every transaction on a dataset with 1% fraud. What is its accuracy and recall?
    options: ["99% accuracy, 0% recall", "1% accuracy, 100% recall", "50% accuracy, 50% recall", "99% accuracy, 99% recall"]
    answer: 0
    why: Accuracy rewards the majority class. Recall exposes that not a single fraud case was caught.
  - q: What does ROC AUC = 0.9 mean?
    options:
      - The model is right 90% of the time
      - A random positive example is scored higher than a random negative one 90% of the time
      - The best threshold is 0.9
      - 90% of positives are found
    answer: 1
    why: AUC measures ranking quality independent of any threshold.
  - q: For a RAG guardrail that blocks answers containing confidential data, which error is usually more costly?
    options: [False positive (blocking a safe answer), False negative (letting confidential data through), They are equal, Neither matters]
    answer: 1
    why: A leak is usually far worse than an unnecessary block, so you optimise for recall at an acceptable precision.
interview:
  - q: How would you define success metrics for an AI feature before building it?
    a: |
      Start from the business outcome and the cost of each error type. Define (1) a primary quality metric that maps to that cost (e.g. recall at ≥90% precision for a detector, answer correctness and groundedness for RAG), (2) guardrail metrics that must not regress (latency p95, cost per request, refusal rate, safety violations), and (3) an online metric (deflection rate, time saved, user rating). Write the thresholds down as acceptance criteria, build the evaluation set first, and get stakeholders to agree on them — this is the AI equivalent of a Definition of Done.
    level: architect
---

## Why it matters

The metric *is* the specification. Optimise the wrong one and you ship a model that looks great on a dashboard and fails users. Everything in this lesson carries over to LLM systems: retrieval recall, guardrail precision, answer accuracy — you will build them all in Phase 12.

## Concept

### The confusion matrix

|  | Predicted positive | Predicted negative |
|---|---|---|
| **Actually positive** | True positive (TP) | False negative (FN) — a miss |
| **Actually negative** | False positive (FP) — a false alarm | True negative (TN) |

- **Accuracy** $= \frac{TP + TN}{\text{all}}$ — misleading on imbalanced data.
- **Precision** $= \frac{TP}{TP + FP}$ — of what we flagged, how much was right?
- **Recall** $= \frac{TP}{TP + FN}$ — of what was really there, how much did we find?
- **F1** $= 2 \cdot \frac{P \cdot R}{P + R}$ — high only when both are high.

### Threshold-free: ROC AUC

The ROC curve plots recall (true positive rate) against false positive rate as the threshold sweeps from 1 to 0. **AUC** is the area under it, equal to the probability that a random positive outranks a random negative. 0.5 is random, 1.0 is perfect ranking.

### Regression metrics

- **MSE / RMSE** — average squared error; RMSE is in the units of $y$.
- **MAE** — average absolute error; less sensitive to outliers.
- **R²** — fraction of variance explained; 0 means "no better than predicting the mean".

## Build it from scratch

<!-- code: lessons/p02-classical-ml/src/main/java/dev/aieng/p02/Metrics.java#confusion -->

AUC as a pairwise probability, written for clarity rather than speed:

<!-- code: lessons/p02-classical-ml/src/main/java/dev/aieng/p02/Metrics.java#auc -->

The test `metricsOnAKnownConfusionMatrix` works through a 10-example case by hand: TP 3, FP 1, TN 5, FN 1 gives accuracy 0.8 and precision = recall = F1 = 0.75.

## Mapping metrics to AI products

| AI feature | Costly error | Optimise |
|---|---|---|
| PII / confidential-data guardrail | Missing a leak (FN) | Recall at acceptable precision |
| Auto-closing support tickets | Closing a real problem (FP) | Precision |
| RAG retrieval | Missing the relevant passage | Recall@k (Phase 12) |
| Lead scoring / ranking | Bad ordering | AUC, nDCG |
| Price or effort estimation | Large misses | MAE / RMSE, plus error distribution |

## Now with the library

Most libraries (Tribuo's `LabelEvaluation`, Smile, DJL's evaluators) compute these for you. The value is in **choosing** the metric and threshold, which no library can do.

## Production notes

- **Report per-class metrics** for multi-class problems; macro-averages hide the class users complain about.
- **Report confidence intervals.** With 200 test examples, an accuracy of 0.90 has a 95% interval of roughly ±4 percentage points. Two models 2 points apart may be tied.
- **Online metrics win arguments.** Offline metrics select candidates; A/B tests or shadow deployments decide.

## Exercises

1. **Easy** — Compute precision, recall and F1 for TP = 40, FP = 10, FN = 20.
2. **Medium** — Implement an O(n log n) AUC by sorting scores and counting with ranks (the Mann–Whitney U formulation). Test it against `rocAuc`.
3. **Hard** — Implement bootstrap confidence intervals for accuracy: resample the test set with replacement 1,000 times, compute accuracy each time, report the 2.5th and 97.5th percentiles.

## Common mistakes

- Choosing the threshold on the test set.
- Comparing models on different test sets.
- Celebrating 99% accuracy on a 99/1 class split.

## Further reading

- Google, *Machine Learning Crash Course*: classification metrics module.
- Tom Fawcett, "An introduction to ROC analysis", *Pattern Recognition Letters* 27 (2006).
