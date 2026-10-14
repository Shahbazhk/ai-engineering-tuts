---
title: Eval-Driven Development
summary: Why AI features need evaluation suites, the layers of testing for LLM systems, and how evals become your Definition of Done.
minutes: 35
prereqs: [p11-mcp/05-mcp-security]
tags: [evals, testing, process]
quiz:
  - q: Why are ordinary unit tests insufficient for an LLM feature?
    options:
      - They are too slow
      - Outputs vary and quality is a distribution, so you need datasets and metrics with thresholds, not single exact assertions
      - JUnit cannot call models
      - They are only for Java
    answer: 1
    why: Unit tests still test your code (with fakes); evals measure the behaviour of the whole system on representative inputs.
  - q: What is the first artefact to create when starting an AI feature?
    options: [The prompt, A small evaluation set of real examples with expected outcomes, The UI, A fine-tuned model]
    answer: 1
    why: Without it you cannot tell whether any change is an improvement.
  - q: Which is an online (production) evaluation signal?
    options: [Recall@5 on a golden set, User thumbs-down rate and escalation rate, Token F1 on test data, Perplexity]
    answer: 1
    why: Offline evals predict quality; online signals confirm it with real users.
interview:
  - q: How do you define "done" for an LLM feature in a Scrum team?
    a: |
      Add measurable quality criteria to the Definition of Done: the feature's eval suite exists and is versioned; it covers the main categories and known edge cases (including refusals and adversarial inputs); offline metrics meet agreed thresholds (e.g. faithfulness ≥ 0.9, retrieval recall@5 ≥ 0.85, refusal accuracy ≥ 0.95) with confidence intervals; no regression versus the current production baseline beyond tolerance; latency and cost per request within budget; safety tests pass; and online monitoring with alerting is in place. Product owners agree on thresholds before work starts — they are acceptance criteria.
    level: architect
---

## Why it matters

In traditional software, correctness is binary and tests prove it. In AI features, quality is **a distribution over inputs**, and every prompt, model, retrieval or chunking change shifts it. Without evals you are guessing — and every "improvement" risks a silent regression elsewhere.

## Concept: testing layers

```mermaid
flowchart TB
    U["Unit tests with fakes — deterministic: prompt assembly, parsing, tool loops, permissions"] --> C["Component evals — retrieval recall, classifier accuracy, extraction F1"]
    C --> E["End-to-end evals — answer correctness, faithfulness, refusals, safety"]
    E --> O["Online evaluation — user feedback, escalations, A/B tests, sampled human review"]
```

| Layer | Runs | Model | Example in this course |
|---|---|---|---|
| Unit | Every build | Fake | `ScriptedChatModel` tests (Phase 6) |
| Component | Every build or nightly | Real or deterministic | Retrieval recall of the course Tutor (lesson 6) |
| End-to-end | Nightly / pre-release | Real | `EvalRunner` with a judge |
| Online | Continuously | Production | Thumbs, escalations, sampled review |

**Eval-driven development** loop:

1. Collect real examples (inputs + expected outcomes or rubrics).
2. Run the current system; read the failures.
3. Change one thing (prompt, retrieval, model).
4. Re-run; keep the change only if the metric improves without regressions.
5. Add every production failure to the dataset.

## Build it

This phase builds the toolkit in plain Java:

| Lesson | Code |
|---|---|
| 2 — datasets and metrics | `RetrievalMetrics`, `TextMetrics` |
| 3 — LLM-as-judge | `LlmJudge` |
| 4 — RAG evaluation | `RagMetrics` |
| 5 — testing non-deterministic systems | Fakes, properties, statistical assertions |
| 6 — evals in CI | `EvalRunner` with thresholds, baselines and confidence intervals |

## Production notes

- **Start small:** 30–50 well-chosen examples beat 1,000 random ones on day one; grow the set from production.
- **Version datasets** with the code and record which dataset version produced each report.
- **Read failures, not just averages.** The single most useful activity in AI engineering is looking at bad outputs.

## Exercises

1. **Easy** — Write ten eval cases (input, expected outcome, category) for the course Tutor.
2. **Medium** — Define the Definition-of-Done thresholds you would require for that Tutor, with justifications.
3. **Hard** — Design an online feedback loop: how thumbs-down answers flow into a review queue and then into the eval dataset, including privacy handling.

## Common mistakes

- Shipping on "vibes" from a few manual tries.
- Only measuring averages.
- Never adding production failures to the eval set.

## Further reading

- Hamel Husain, "Your AI Product Needs Evals" (2024).
- Shreya Shankar et al., "Who Validates the Validators?", 2024.
