---
title: Prompting, RAG or Fine-Tuning?
summary: A decision framework for customising model behaviour — what fine-tuning is good at, what it is bad at, and what it costs.
minutes: 35
prereqs: [p12-evals/06-evals-in-ci]
tags: [fine-tuning, architecture, decision]
quiz:
  - q: A team wants the model to "know" their 3,000 internal policies that change monthly. What is the best approach?
    options: [Fine-tune monthly, RAG over the policies with citations, A longer system prompt, Increase temperature]
    answer: 1
    why: Changing, citable, permissioned knowledge belongs in retrieval. Fine-tuned facts cannot be cited, updated quickly or permission-filtered.
  - q: Which goal is a good fit for fine-tuning?
    options:
      - Adding last week's product prices
      - Consistently producing a specific output format and tone, or making a small model match a large one on a narrow task
      - Hiding confidential documents
      - Answering questions about new laws
    answer: 1
    why: Fine-tuning shapes behaviour — format, style, task skill — and enables distillation into cheaper models.
  - q: What must exist before a fine-tuning project starts?
    options: [A GPU cluster, An evaluation set and a baseline from prompting/RAG to beat, A new model architecture, A Python team]
    answer: 1
    why: Without a baseline and evals you cannot tell whether fine-tuning helped.
interview:
  - q: Make the business case for or against fine-tuning a model for ticket triage.
    a: |
      Compare against the cheapest alternatives on the same eval set. If a prompted frontier model reaches the accuracy target but costs too much at volume, fine-tuning a small open model (or distilling from the frontier model's labels) can cut cost per ticket by 10–100× and latency by several times, with data staying in-house — a strong case when volume is high and the task is narrow and stable. Costs: building and maintaining a labelled dataset, training runs, evaluation, serving infrastructure, and re-training when categories change or the base model is upgraded. If volume is low, categories change often, or prompting already meets targets at acceptable cost, don't fine-tune. Decide with numbers: accuracy, cost per 1,000 tickets, latency, and total cost of ownership over a year.
    level: architect
---

## Why it matters

Fine-tuning is the most over-requested and under-justified technique in enterprise AI. Done for the right reasons it cuts cost and improves consistency dramatically; done for the wrong ones it produces an expensive, stale model that still hallucinates.

## Concept

```mermaid
flowchart TD
    START{What is missing?} -->|Knowledge: facts, documents, data| RAG[RAG / tools]
    START -->|Behaviour: format, tone, task skill| P{Does prompting + few-shot reach the target?}
    P -->|yes| DONE[Ship the prompt]
    P -->|no| FT[Fine-tune]
    START -->|Cost / latency at scale| DIST[Distil into a smaller model via fine-tuning]
    START -->|Live data| TOOLS[Tool calling]
```

| Goal | Prompting | RAG | Fine-tuning |
|---|---|---|---|
| Add changing, citable knowledge | ✗ | ✓✓ | ✗ (stale, uncitable) |
| Consistent format/style | ✓ | – | ✓✓ |
| Domain jargon and conventions | ✓ | ✓ | ✓✓ |
| Narrow task at low cost | – | – | ✓✓ (small model) |
| Data residency with good quality | – | ✓ | ✓✓ (own small model) |
| Time to first result | hours | days | weeks |

**Fine-tuning variants:**

- **Full fine-tuning:** update all weights. Highest capacity, highest cost (Phase 3: ~16 bytes/parameter of GPU memory).
- **Parameter-efficient (PEFT), e.g. LoRA:** train small adapter matrices; the base model stays frozen (next lesson).
- **Preference tuning (DPO etc.):** teach which of two answers is better — for tone and policy.
- **Hosted fine-tuning APIs:** upload JSONL, get a model id — least infrastructure, data leaves your network.

## Build it

This phase walks the full pipeline, keeping Java everywhere except the GPU training step:

| Step | Language | Lesson |
|---|---|---|
| Understand LoRA | Java | 2 |
| Build and clean the dataset (JSONL) | Java | 3 |
| Train the adapter | Python (TRL + PEFT) | 4 |
| Quantise for serving | Java (concept) / llama.cpp tools | 5 |
| Serve and evaluate | Java (Ollama, Jlama, ONNX) + Phase 12 evals | 6 |

## Production notes

- **Establish the baseline first:** best prompt + RAG on the eval set. Fine-tuning must beat it on quality or cost.
- **Plan for re-training:** base model upgrades, new categories and data drift all require a new run.
- **Licences:** check the base model's licence for commercial use and fine-tuning restrictions.

## Exercises

1. **Easy** — For three AI features you know, decide prompting, RAG, fine-tuning or a combination, with one sentence each.
2. **Medium** — Estimate yearly cost for ticket triage at 50,000 tickets/day with (a) a hosted frontier model and (b) a fine-tuned 1B model on one GPU, using Phase 6's cost model.
3. **Hard** — Write the success criteria (metrics and thresholds) a fine-tuned triage model must meet before replacing the prompted baseline.

## Common mistakes

- Fine-tuning to add knowledge.
- No baseline or eval set.
- Forgetting the maintenance cost.

## Further reading

- OpenAI, "Fine-tuning best practices"; Anthropic and Google equivalents.
- Hu et al., "LoRA: Low-Rank Adaptation of Large Language Models", 2021.
