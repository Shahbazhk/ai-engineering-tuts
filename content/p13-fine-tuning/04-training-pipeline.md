---
title: The Training Step — Python, Honestly
summary: Run a LoRA/QLoRA fine-tune with Hugging Face TRL and PEFT — the one place this course uses Python — and integrate it with a Java pipeline.
minutes: 45
prereqs: [p13-fine-tuning/03-dataset-preparation]
tags: [fine-tuning, python, trl, peft, mlops]
quiz:
  - q: Why does this course use Python for the training step only?
    options:
      - Java cannot do maths
      - The GPU training ecosystem (PyTorch, CUDA kernels, bitsandbytes, PEFT, TRL) is Python-first with no realistic Java equivalent
      - Python is faster in general
      - Spring forbids training
    answer: 1
    why: Use the best tool per step — Java for data, evaluation and serving; Python for GPU training.
  - q: In the training config, what does gradient_accumulation_steps=4 with batch size 4 achieve?
    options:
      - Four times the learning rate
      - An effective batch of 16 while only holding 4 examples in GPU memory at a time
      - Four epochs
      - Four GPUs
    answer: 1
    why: Gradients are summed over several micro-batches before each optimiser step.
  - q: What does trainer.save_model(...) save in a PEFT LoRA run?
    options: [The full base model, Only the adapter weights and config — typically a few megabytes, The dataset, Nothing]
    answer: 1
    why: The base model stays unchanged and is referenced by name/version.
interview:
  - q: How do you operationalise fine-tuning in an enterprise with a Java-centric platform team?
    a: |
      Treat training as a batch job in an MLOps pipeline. Java services (Spring Batch) prepare and version datasets in object storage; a CI/CD-triggered job runs the Python training container on a GPU node pool (Kubernetes Job, cloud training service), with pinned library versions and the dataset and base-model versions as inputs; metrics and artefacts go to a tracking server (MLflow); the adapter is stored in a model registry with lineage. A Java evaluation job runs the Phase 12 eval suite against the candidate served in a staging environment and compares it with the production baseline; promotion is a reviewed change to the serving configuration. The Python surface is small, containerised and owned with clear interfaces.
    level: architect
---

## Why it matters

Being honest about tooling builds trust: today, GPU training means Python. The skill for a Java architect is to keep that surface small, reproducible and integrated — data in, adapter out, evaluated by the same Java eval suite as everything else.

## Concept

```mermaid
flowchart LR
    J1["Java: DatasetBuilder / Spring Batch → train.jsonl, validation.jsonl"] --> S3[(object storage, versioned)]
    S3 --> PY["Python container on GPU: TRL SFTTrainer + PEFT LoRA"]
    BASE[(base model, pinned version)] --> PY
    PY --> ADP[(adapter + metrics → model registry)]
    ADP --> SERVE["serve: Ollama / vLLM with adapter"]
    SERVE --> J2["Java: Phase 12 EvalRunner vs baseline"]
    J2 --> GATE{promote?}
```

Key training hyperparameters (all seen earlier in this course):

| Setting | Typical LoRA value | From |
|---|---|---|
| `learning_rate` | 1e-4 – 2e-4 | Phase 3 lesson 5; lesson 2 here |
| `lr_scheduler_type`, `warmup_ratio` | cosine, 0.03 | Phase 3 lesson 5 |
| `num_train_epochs` | 1–3 | Overfitting (Phase 2) |
| `r`, `lora_alpha` | 16, 32 | Lesson 2 |
| `assistant_only_loss` | true | Lesson 3 |
| `eval_strategy`, `load_best_model_at_end` | epoch, true | Phase 3 lesson 6 |

## Build it

The training script (kept in `lessons/p13-fine-tuning/python/`, not run by the Maven build):

<!-- code: lessons/p13-fine-tuning/python/train_lora.py -->

Run it:

```bash
cd lessons/p13-fine-tuning/python
python -m venv .venv && source .venv/bin/activate
pip install torch transformers datasets peft trl bitsandbytes accelerate
python train_lora.py --model meta-llama/Llama-3.2-1B-Instruct --qlora \
    --train ../target/sft/train.jsonl --validation ../target/sft/validation.jsonl --out ./adapter
```

No GPU? Use a hosted fine-tuning service that accepts the same `messages` JSONL, or a notebook environment with a GPU — the Java parts of the pipeline do not change.

## Now with the library

Alternatives to running TRL yourself:

| Option | Notes |
|---|---|
| Unsloth | Faster, lower-memory LoRA training, same data format |
| Axolotl | YAML-configured training on top of the HF stack |
| Hosted fine-tuning APIs | Upload JSONL, receive a model id; data leaves your network |
| Cloud training services | Managed GPUs with your container |

## Production notes

- **Pin everything:** base model revision, library versions, random seed, dataset version.
- **Track experiments** (MLflow): loss curves, eval metrics, hyperparameters, data version.
- **Watch for overfitting:** validation loss rising while training loss falls → fewer epochs, lower rank or more data.
- **Check the licence** of the base model and dataset sources.

## Exercises

1. **Easy** — Generate `train.jsonl` and `validation.jsonl` with `DatasetBuilder` from 50 examples you write, and inspect them.
2. **Medium** — Write a `Dockerfile` for the training script with pinned versions and a non-root user.
3. **Hard** — Run a QLoRA fine-tune of a 1B model on your triage dataset (local GPU or hosted notebook), serve it (lesson 6), and compare it with the prompted base model using `EvalRunner`.

## Common mistakes

- Unpinned library versions (TRL and Transformers APIs change frequently).
- Training without a validation split.
- Judging success by training loss alone.

## Further reading

- Hugging Face TRL SFTTrainer documentation.
- Hugging Face PEFT documentation.
- Unsloth documentation.
