---
title: A Complete Training Loop
summary: Mini-batches, shuffling, validation, loss curves and debugging — train a neural network on the spiral dataset to 98% validation accuracy.
minutes: 60
prereqs: [p03-neural-networks/05-optimizers]
tags: [neural-networks, training, debugging]
quiz:
  - q: Why shuffle the training data every epoch?
    options:
      - To make the code slower
      - So mini-batches differ each epoch, which reduces gradient bias and helps generalisation
      - To change the labels
      - It is required for validation
    answer: 1
    why: Fixed batch composition can make the optimiser follow the same biased path every epoch.
  - q: Training accuracy is 99.6% and validation accuracy 98.3%. What does that suggest?
    options:
      - Severe overfitting
      - A healthy model with a small generalisation gap
      - Underfitting
      - A data-leakage bug
    answer: 1
    why: A small gap is expected. A large gap would indicate overfitting; equal and low numbers would indicate underfitting.
  - q: A first sanity check before a long training run is to…
    options:
      - Train on the full dataset for days
      - Overfit a tiny batch (e.g. 10 examples) to near-zero loss
      - Deploy the model
      - Remove the validation set
    answer: 1
    why: If the model cannot memorise ten examples, there is a bug in the model, loss or optimiser — no point training longer.
interview:
  - q: A fine-tuning run's training loss decreases nicely but the evaluation metrics get worse. What do you check?
    a: |
      First overfitting: compare training and validation loss curves; stop earlier, lower epochs or learning rate, add data or regularisation (dropout, weight decay, smaller LoRA rank). Then data issues: train/eval distribution mismatch, label noise, duplicates between train and eval (leakage inflates early eval), formatting differences between training examples and evaluation prompts (chat templates). Then the metric itself: loss measures token likelihood, while the eval metric may measure task success — fine-tuning can lower loss by matching style while hurting reasoning. Finally check catastrophic forgetting on general benchmarks.
    level: architect
---

## Why it matters

This is the loop that trains and fine-tunes every model. When you run a fine-tuning job (Phase 13) you configure exactly these pieces — batch size, epochs, learning rate, evaluation strategy — and read exactly these curves.

## Concept

```mermaid
flowchart TD
    D[(training set)] --> SH[shuffle each epoch]
    SH --> MB[take mini-batch of 32]
    MB --> F[forward: logits]
    F --> L[loss: softmax cross-entropy]
    L --> Z[zero gradients]
    Z --> B[backward]
    B --> U[optimiser step]
    U -->|more batches| MB
    U -->|epoch done| E[log loss, evaluate on validation set]
    E -->|more epochs| SH
```

- **Mini-batch gradient descent:** each step uses a small random subset. Cheaper than the full dataset per step, less noisy than one example.
- **Epoch:** one pass over the training data.
- **Validation:** measured on held-out data after each epoch to detect overfitting.

The **spiral dataset** has three interleaved arms — impossible for a linear model, straightforward for a small MLP.

## Build it from scratch

The model is the transformer feed-forward shape at toy scale: Linear → ReLU → Linear.

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Trainer.java#model -->

The loop:

<!-- code: lessons/p03-neural-networks/src/main/java/dev/aieng/p03/Trainer.java#loop -->

`Trainer.main` trains the same architecture with two optimisers:

```text
SGD+momentum  loss 0.0311  train acc 0.988  validation acc 0.975  (epoch 10 loss 0.183)
Adam          loss 0.0151  train acc 0.996  validation acc 0.983  (epoch 10 loss 0.236)
```

Both reach high validation accuracy; Adam ends with lower loss. Note Adam's higher loss at epoch 10 — early progress is not the whole story.

## Debugging a training run

| Symptom | Likely cause | First action |
|---|---|---|
| Loss stays at $\ln(\text{classes})$ | Gradients not flowing, LR too low, labels shuffled separately from inputs | Overfit a tiny batch |
| Loss → NaN | LR too high, numerical instability | Lower LR, check fused loss, clip gradients |
| Train ≫ validation accuracy | Overfitting | More data, regularisation, early stopping |
| Both accuracies low | Underfitting or bug | Bigger model, more epochs, check preprocessing |
| Loss jumps periodically | Bad batches, data errors | Log per-batch loss, inspect outliers |

## Now with the library

A DJL training setup maps one-to-one onto this loop:

```java
// reference snippet (DJL is not a build dependency)
DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
        .optOptimizer(Optimizer.adam().optLearningRateTracker(Tracker.fixed(0.01f)).build())
        .addEvaluator(new Accuracy())
        .addTrainingListeners(TrainingListener.Defaults.logging());
try (Trainer trainer = model.newTrainer(config)) {
    EasyTrain.fit(trainer, 300, trainingSet, validationSet);
}
```

And a Hugging Face fine-tuning config names the same knobs: `per_device_train_batch_size`, `num_train_epochs`, `learning_rate`, `eval_strategy="epoch"`.

## Production notes

- **Log everything** — loss per step, learning rate, gradient norm, validation metrics — to a tracking tool (MLflow, Weights & Biases). Training runs are experiments; treat them like auditable records.
- **Fix seeds** and record data versions for reproducibility, but expect small run-to-run variance on GPUs.
- **Checkpoint regularly** and keep the best checkpoint by validation metric, not the last one.

## Exercises

1. **Easy** — Train with batch sizes 8, 32 and 240 (full batch). Compare final validation accuracy and wall-clock time.
2. **Medium** — Add early stopping: stop when validation accuracy has not improved for 20 epochs and restore the best weights.
3. **Hard** — Run the "overfit one batch" sanity check: train on 10 examples until loss < 0.01. Then introduce a bug (shuffle labels independently of inputs) and observe which symptom from the table appears.

## Common mistakes

- Evaluating with the same data you trained on.
- Not zeroing gradients between batches.
- Comparing optimisers at a single learning rate — tune each one.

## Further reading

- Andrej Karpathy, "A Recipe for Training Neural Networks" (2019 blog post).
- Stanford CS231n notes, "Neural Networks Part 3: Learning and Evaluation".
