---
title: Scrum for AI — Experiments, Evals and Definition of Done
summary: Adapt Scrum to probabilistic work — hypothesis-driven backlog items, metric-based sprint goals, eval thresholds in the Definition of Done, and roles on an AI team.
minutes: 40
prereqs: [p17-delivery-leadership/02-estimating-under-uncertainty]
tags: [leadership, scrum, agile, definition-of-done]
quiz:
  - q: What is a good sprint goal for an AI feature in its experimental phase?
    options:
      - Write 2,000 lines of code
      - Raise retrieval recall@8 on the eval set from 0.72 to at least 0.85, or learn why it is not achievable
      - Finish all stories
      - Deploy to production
    answer: 1
    why: Outcome-based goals fit work whose path is uncertain; "learn why not" is a valid outcome.
  - q: Which item belongs in an AI feature's Definition of Done?
    options:
      - The demo looked good
      - Eval suite thresholds met with no regression versus baseline, and prompt/model versions recorded
      - The model is the newest
      - The PR has many comments
    answer: 1
    why: Quality gates must be measurable and automated.
  - q: How should an experiment backlog item be written?
    options:
      - As a vague research task
      - As a hypothesis with a measurable expected result and a time box (e.g. "We believe reranking raises nDCG@5 by 0.05; 3 days")
      - As a user story only
      - Without acceptance criteria
    answer: 1
    why: Hypotheses make experiments comparable, time-boxed and reviewable.
interview:
  - q: As a Scrum Master on an AI team, what changes in your events and artefacts?
    a: |
      The Product Backlog mixes user stories with hypothesis-based experiment items (time-boxed, with measurable expected outcomes). Sprint goals are often metric movements. The Definition of Done adds eval thresholds, regression checks against the baseline, safety tests, versioned prompts/models and observability. Refinement includes data and eval-set work as first-class items. Sprint Reviews show metric dashboards and failure examples, not just demos. Retrospectives cover experiment hygiene (one change at a time, held-out data). Cross-functional roles include domain experts for labelling and evaluation. Forecasts are probabilistic. Empiricism — transparency, inspection, adaptation — fits AI work very well when the evidence is eval data.
    level: senior
---

## Why it matters

Scrum is built on empiricism, which suits AI well — but only if the "inspect" step uses real evidence. That means evals in the Definition of Done, experiments in the backlog, and metric dashboards in the Review.

## Concept

```mermaid
flowchart LR
    PB["Product Backlog: stories + experiments (hypothesis, metric, time box)"] --> SP["Sprint Planning: metric-based sprint goal"]
    SP --> DEV["Sprint: one change at a time, eval after each"]
    DEV --> DOD{"Definition of Done: tests + eval thresholds + no regression + versions recorded"}
    DOD --> REV["Sprint Review: dashboard + failure examples + cost"]
    REV --> RET["Retrospective: experiment hygiene"]
    RET --> PB
```

### Experiment backlog item template

```text
Hypothesis: Adding a cross-encoder reranker raises nDCG@5 on the support eval set by ≥ 0.05
            without p95 latency above 4 s.
Time box:   3 days
Measure:    nDCG@5, p95 latency, cost per query (eval run on fixed dataset v12)
Decision:   adopt if both thresholds met; otherwise record result and drop
```

### Definition of Done for AI increments

- Unit tests with fake models pass (deterministic logic).
- Eval suite: thresholds met, no regression beyond tolerance vs production baseline (Phase 12).
- Safety/red-team regression cases pass (Phase 15).
- Prompt, model, retrieval settings versioned; ADR updated if a decision changed.
- Observability: token, latency and quality metrics emitted; dashboards updated.
- Cost per request within budget.

### Team roles

| Role | Contribution |
|---|---|
| Product Owner | Success metrics, thresholds, prioritising experiments vs features |
| AI/software engineers | Pipelines, prompts, retrieval, evals, integration |
| Domain experts | Labelling, eval cases, failure review — part of the team, not a ticket queue |
| Platform/SRE | Gateway, observability, capacity |
| Security/compliance | Risk tier, red-teaming, governance artefacts |

## Build it

Wire the Definition of Done into CI: the Phase 12 `EvalRunner` gate and the course app's `TutorRetrievalEvalTest` are examples of DoD items that fail the build automatically. A DoD that a pipeline enforces is a DoD that holds.

## Production notes

- **Review failures, not just averages,** in Sprint Reviews — show five bad outputs and what was learned.
- **Protect the eval set:** changing it is a backlog item with review, not a casual edit.
- **Limit WIP on experiments** — parallel experiments on the same metric confound results.

## Exercises

1. **Easy** — Rewrite three vague AI backlog items as hypotheses with time boxes.
2. **Medium** — Draft your team's AI Definition of Done and mark which items CI can enforce.
3. **Hard** — Design a Sprint Review dashboard for an AI feature (metrics, failure gallery, cost) and the data needed to populate it.

## Common mistakes

- Demo-driven reviews.
- Stories with "make it smarter" acceptance criteria.
- Domain experts outside the team.

## Further reading

- The Scrum Guide (2020) — empiricism and the Definition of Done.
- Hamel Husain, "A Field Guide to Rapidly Improving AI Products" (2025).
