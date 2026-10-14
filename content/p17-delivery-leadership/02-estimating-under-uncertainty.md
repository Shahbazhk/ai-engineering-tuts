---
title: Estimating Under Uncertainty — PERT vs Monte Carlo
summary: Why single-number estimates fail for AI work, and how a Monte Carlo simulation with rework risk produces honest, percentile-based forecasts.
minutes: 40
prereqs: [p17-delivery-leadership/01-ai-project-lifecycle]
tags: [leadership, estimation, monte-carlo, pmp]
quiz:
  - q: The PERT sum for a plan is 51 days, but the Monte Carlo P80 is 72 days. Why the gap?
    options:
      - A bug in the simulation
      - PERT means hide skew and ignore rework loops; the simulation includes the chance that experiments miss their eval gate and must iterate
      - Monte Carlo always adds 40%
      - PERT uses weeks
    answer: 1
    why: AI experiments have long right tails; percentiles expose them.
  - q: What should you communicate to stakeholders?
    options:
      - The P50 date as a commitment
      - A range with confidence levels (e.g. P50 and P80), the main drivers of risk, and when the forecast will be updated
      - The optimistic estimate
      - No dates at all
    answer: 1
    why: Probabilistic forecasts set honest expectations and invite risk discussions.
  - q: Which input most improves the forecast after the first sprint?
    options: [More optimistic estimates, Actual results — e.g. how many iterations the first experiment needed — fed back into the model, A bigger team, Removing tasks]
    answer: 1
    why: Re-forecast with evidence; uncertainty shrinks as experiments resolve.
interview:
  - q: An executive wants a fixed delivery date for an AI assistant. How do you handle it?
    a: |
      Separate what is certain from what is uncertain. Offer a date-bound commitment for the parts with low uncertainty (platform, integration, pilot infrastructure) and a time-boxed experiment with explicit gates for the uncertain part (reaching the quality threshold). Present a Monte Carlo forecast with P50/P80/P95 and explain the main risk drivers (e.g. 40% chance the first RAG approach misses recall targets). Offer options: fixed date with flexible scope (fewer sources or languages at launch), or fixed scope with a date range. Commit to re-forecasting after each sprint with real data, and agree kill/pivot criteria upfront.
    level: architect
---

## Why it matters

AI tasks have skewed durations: most experiments take about as long as expected, some take far longer because the first approach misses the quality bar. Single-point estimates — even PERT means — hide that tail. Percentile forecasts don't.

## Concept

**PERT** (three-point) mean: $\mu = \dfrac{o + 4m + p}{6}$ — useful, but summing means ignores dependencies, skew compounding and rework loops.

**Monte Carlo:** model each task as a distribution (here triangular from optimistic, most likely and pessimistic), add a probability of rework (an experiment missing its eval gate), simulate the plan 20,000 times, and read percentiles.

```mermaid
flowchart LR
    T["tasks: (o, m, p, rework %)"] --> SIM["simulate 20,000 runs: sample each task (+ rework loops)"]
    SIM --> DIST["distribution of total duration"]
    DIST --> P["P50 / P80 / P95"]
```

## Build it from scratch

<!-- code: lessons/p17-delivery-leadership/src/main/java/dev/aieng/p17/MonteCarloEstimate.java#simulate -->

The triangular sampler uses inverse-CDF sampling — the same technique LLMs use to pick tokens (Phase 1, lesson 4).

`MonteCarloEstimate.main` for a five-task RAG initiative (discovery, ingestion, the RAG experiment with a 40% chance of missing recall targets, guardrails, pilot):

```text
PERT sum 51 days | P50 63 | P80 72 | P95 84
```

The PERT total (51 days) is below even the median. Committing to it means committing to a date you will likely miss. The test `reworkRiskPushesTheForecastBeyondPert` proves the point: PERT is identical with or without rework risk; the simulation's P80 moves by more than three days.

## Now with the tools

- Spreadsheets can do this (random sampling + data tables), but code is versionable and testable.
- Jira/Azure Boards throughput data supports Monte Carlo forecasting from *historical* velocity (number of items finished per week) instead of estimates — useful once the team has history.
- Present forecasts as fan charts or percentile tables, updated every sprint.

## Production notes

- **Estimate experiments as time-boxes** with gates, not as tasks with certain outcomes.
- **Re-forecast every sprint** with actuals; publish the trend of P80 over time.
- **Name the drivers:** "The RAG recall experiment is 60% of the variance" focuses risk reduction.

## Exercises

1. **Easy** — Change the RAG experiment's rework probability to 0.1 and 0.7; record P80.
2. **Medium** — Add task dependencies that run in parallel (the plan finishes when the longest branch finishes) and compare with the sequential model.
3. **Hard** — Replace task estimates with historical throughput: simulate weeks to finish 40 backlog items by sampling weekly throughput from your team's last 15 sprints.

## Common mistakes

- Treating P50 as a commitment.
- Ignoring rework loops for experiments.
- Never updating the forecast.

## Further reading

- Daniel Vacanti, *When Will It Be Done?* (2020).
- PMI, *PMBOK Guide* — quantitative risk analysis (Monte Carlo).
