---
title: Datasets and Metrics — Retrieval and Text
summary: Build golden datasets, then measure retrieval with recall@k, precision@k, MRR and nDCG, and answers with exact match, token F1 and key-fact recall.
minutes: 50
prereqs: [p12-evals/01-eval-driven-development]
tags: [evals, metrics, retrieval, datasets]
quiz:
  - q: For a RAG system, which retrieval metric matters most?
    options: [Precision@1, Recall@k for the k you put in the prompt — if the evidence is not retrieved, the answer cannot be right, Latency only, Vocabulary size]
    answer: 1
    why: Generation can only use what retrieval provides. Precision matters secondarily (token cost, distraction).
  - q: Ranked list [d3, d1, d7, d2], relevant {d1, d2}. What is the reciprocal rank?
    options: ["1", "0.5", "0.25", "0"]
    answer: 1
    why: The first relevant item (d1) is at rank 2, so 1/2.
  - q: Why use token F1 in addition to exact match?
    options:
      - It is faster
      - It gives partial credit to answers that are close but phrased differently
      - It replaces human review
      - It measures latency
    answer: 1
    why: Exact match is strict; F1 on normalised tokens is more forgiving for short factual answers.
interview:
  - q: How do you build an evaluation dataset for an enterprise assistant when you have no labelled data?
    a: |
      Combine sources. Mine real questions from search logs, support tickets, chat transcripts or FAQ pages (with privacy review). Have subject-matter experts write expected answers and, for RAG, mark the source passages. Generate synthetic questions from documents with an LLM to cover breadth, then have humans filter and correct them. Add adversarial and out-of-scope questions (should refuse) and permission tests (should not see content). Tag every case by category and difficulty, keep a held-out portion untouched by prompt authors, and version the set. 50–200 curated cases is a strong start; grow from production failures.
    level: architect
---

## Why it matters

A metric is only as good as the dataset under it. This lesson builds both: how to create a golden set, and the deterministic metrics you should exhaust before reaching for an LLM judge.

## Concept

### Golden dataset

| Field | Example |
|---|---|
| id | `vpn-003` |
| input | "Which port does the VPN proxy use on Windows?" |
| expected | "8443" (or key facts, or a rubric) |
| relevant sources | `kb-1#2` |
| tags | `vpn`, `windows`, `single-fact` |

Cover: main categories, edge cases, **unanswerable** questions (expect refusal), **permission** cases (expect no leak), multilingual inputs, and adversarial prompts.

### Retrieval metrics

| Metric | Question it answers |
|---|---|
| Recall@k | Did the relevant evidence make it into the top k? |
| Precision@k | How much of the top k is relevant (token waste)? |
| MRR | How high is the first relevant result? |
| nDCG@k | Are the *most* relevant items at the very top (graded relevance)? |

$$\text{nDCG@k} = \frac{\sum_{i=1}^{k} \frac{2^{rel_i}-1}{\log_2(i+1)}}{\text{IDCG@k}}$$

### Answer metrics (cheap and deterministic)

| Metric | Use |
|---|---|
| Exact match (normalised) | Short factual answers, classifications |
| Token F1 | Short answers with phrasing variation |
| Key-fact recall | Longer answers that must mention specific facts |
| Schema / regex checks | Structured outputs, formats, citations present |

## Build it from scratch

<!-- code: lessons/p12-evals/src/main/java/dev/aieng/p12/RetrievalMetrics.java#metrics -->

<!-- code: lessons/p12-evals/src/main/java/dev/aieng/p12/TextMetrics.java#text -->

The tests work through the example from the quiz (`[d3, d1, d7, d2]` with relevant `{d1, d2}`: recall@2 = 0.5, MRR = 0.5), show nDCG penalising a swapped order, and show token F1 giving 0.5 to a verbose but correct answer.

## Now with the library

- Spring AI includes evaluator interfaces (`Evaluator`, `RelevancyEvaluator`, `FactCheckingEvaluator`) that use a model as judge — next lesson.
- For retrieval metrics, plain Java like the above is usually all you need; keep it in your test sources.
- Data can live as JSON/YAML files in `src/test/resources/evals/`, versioned with the code.

## Production notes

- **Keep a held-out set** that prompt authors never look at while iterating.
- **Tag everything** and report metrics per tag; averages hide category failures.
- **Refresh periodically:** a dataset frozen for a year no longer reflects user behaviour.

## Exercises

1. **Easy** — Compute recall@3, precision@3 and MRR by hand for ranked `[a, b, c, d, e]` with relevant `{c, e}`.
2. **Medium** — Write a JSON loader for eval cases with Jackson and a test that fails on duplicate ids or missing tags.
3. **Hard** — Build a 30-case retrieval dataset for this course (question → expected lesson id) and compute recall@5 and MRR for the app's keyword, semantic and hybrid search modes.

## Common mistakes

- Datasets made only of easy questions.
- Measuring generation quality when retrieval is the bottleneck.
- No refusal or permission cases.

## Further reading

- Manning et al., *Introduction to Information Retrieval*, chapter 8 (evaluation).
- Rajpurkar et al., "SQuAD" — the origin of EM/F1 normalisation for QA.
