---
title: Preparing Fine-Tuning Data in Java
summary: Chat-format JSONL, deduplication, PII filtering, quality gates and train/validation splits — the part of fine-tuning that decides whether it works.
minutes: 45
prereqs: [p13-fine-tuning/02-lora-from-scratch]
tags: [fine-tuning, data, privacy, jsonl]
quiz:
  - q: Why deduplicate near-identical training examples?
    options:
      - To save disk space only
      - Duplicates over-weight one behaviour, encourage memorisation and can leak into the validation set, inflating scores
      - Duplicates break JSON
      - Trainers reject duplicates
    answer: 1
    why: Normalised hashing (case, whitespace, punctuation) catches the common near-duplicates.
  - q: A training example contains a customer's email address. What should happen?
    options:
      - Keep it — more data is better
      - Remove or redact it; models can memorise and later reproduce training data
      - Encrypt it in the JSONL
      - Move it to the validation set
    answer: 1
    why: Training data can be extracted from models. Personal data needs a legal basis and is usually excluded or pseudonymised.
  - q: What does the "messages" JSONL format contain per line?
    options:
      - A single prompt string
      - One complete conversation — system, user and assistant messages — as a JSON object
      - Only the answers
      - Embeddings
    answer: 1
    why: It matches chat templates; trainers apply the model's template and can train on assistant tokens only.
interview:
  - q: How do you build a high-quality fine-tuning dataset for an enterprise task?
    a: |
      Start from real inputs (tickets, emails, documents) sampled to cover categories and edge cases; get target outputs from expert labellers, from a strong model with human review (distillation), or from historical decisions after cleaning. Normalise to one consistent output format and style — inconsistency is the most common quality problem. Deduplicate, filter low-quality and contradictory examples, remove or pseudonymise personal data under a documented legal basis, balance classes, and keep a validation split (by time or entity where leakage is possible) plus a separate held-out eval set. Version the dataset with provenance (source, labeller, date), and inspect samples manually before every training run. A few hundred to a few thousand clean examples often beat tens of thousands of noisy ones.
    level: architect
---

## Why it matters

Fine-tuning learns exactly what the data shows — including duplicates, inconsistencies, personal data and mistakes. Data preparation is ordinary data engineering, which is why it belongs in your Java/Spring estate, close to the source systems.

## Concept

```mermaid
flowchart LR
    SRC["sources: tickets, emails, KB, past decisions"] --> NORM["normalise text"]
    NORM --> DEDUP["dedupe (hash of normalised input)"]
    DEDUP --> GATE["quality gates: length, PII, format"]
    GATE --> LABEL["targets: experts / strong model + review"]
    LABEL --> SPLIT["train / validation split"]
    SPLIT --> JSONL["messages JSONL + provenance"]
```

The chat JSONL format (one conversation per line):

```json
{"messages":[{"role":"system","content":"You triage tickets."},
             {"role":"user","content":"Login fails after the upgrade to 7.4"},
             {"role":"assistant","content":"{\"category\":\"TECHNICAL\"}"}]}
```

Trainers apply the model's chat template to this and (with `assistant_only_loss`) compute the loss only on the assistant's tokens.

## Build it from scratch

<!-- code: lessons/p13-fine-tuning/src/main/java/dev/aieng/p13/DatasetBuilder.java#build -->

The test feeds seven raw examples: one near-duplicate (different case and punctuation) is dropped, one containing an email address and one too-short prompt are rejected, and the remaining four are split 3/1 into train and validation JSONL.

A subtle bug the test caught: the first version built each message with `Map.of(...)`, whose key order is unspecified, so lines came out as `{"content":…,"role":…}` in arbitrary order. Trainers don't care, but diffs, hashes and reviews do. Records serialise in declaration order, so the fix was `record Message(String role, String content)`.

## Now with the library

- **Spring Batch** for large datasets: a reader over the source system, processors for normalisation, PII filtering and labelling, and a writer producing sharded JSONL files with restartability.
- **Labelling with a strong model** (distillation): call a frontier model through `ChatClient` with structured output to propose targets, then sample for human review before training.
- **PII:** combine regexes (as here) with a dedicated detector (Phase 15) — regexes alone miss names and addresses.

## Production notes

- **Provenance:** store source id, labeller (human or model), date and dataset version for every example.
- **Consistency beats volume:** one output format, one style guide.
- **Leakage:** never let eval-set inputs appear in training data; check with the same hashing.

## Exercises

1. **Easy** — Add a check rejecting responses that are not valid JSON when the task requires JSON output.
2. **Medium** — Add class balancing: cap each category at the size of the smallest category × 3 and report the final distribution.
3. **Hard** — Write a Spring Batch job that reads tickets from a CSV, applies `DatasetBuilder` gates in a processor, and writes train/validation JSONL with a provenance sidecar file.

## Common mistakes

- Training on raw historical data with inconsistent formats.
- Personal data in training sets.
- Evaluating on data that also appears in training.

## Further reading

- Hugging Face TRL, dataset formats documentation.
- Zhou et al., "LIMA: Less Is More for Alignment", 2023 — small, curated datasets.
