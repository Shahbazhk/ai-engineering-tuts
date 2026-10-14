---
title: Evals as Quality Gates in CI
summary: Run eval suites automatically, gate releases on thresholds and baselines with confidence intervals, and close the loop with production data.
minutes: 40
prereqs: [p12-evals/05-testing-nondeterministic-systems]
tags: [evals, ci-cd, devops, quality-gates]
quiz:
  - q: Why gate on both an absolute threshold and the previous baseline?
    options:
      - It is redundant
      - The threshold enforces minimum quality; the baseline catches regressions even when the score is still above the threshold
      - Baselines replace thresholds
      - To slow down CI
    answer: 1
    why: A drop from 0.92 to 0.81 passes a 0.8 threshold but is a significant regression.
  - q: Mean score 0.75 with a 95% CI of [0.50, 1.00] on 4 cases. What should you conclude?
    options:
      - The system is 75% good
      - The dataset is far too small to distinguish meaningful differences
      - The CI is wrong
      - Nothing
    answer: 1
    why: Confidence intervals make dataset size limitations visible; grow the set before trusting small differences.
  - q: Where should per-category results be reported?
    options: [Nowhere, In the eval report alongside the overall mean, Only in logs, Only for failures]
    answer: 1
    why: The 'refusal' category scoring 0 in the test is invisible in a 0.75 overall mean.
interview:
  - q: Design the CI/CD flow for prompt and model changes.
    a: |
      Treat prompts, model ids, retrieval settings and tool descriptions as versioned configuration in the repository. On every pull request: unit tests with fakes and fast component evals (retrieval metrics on a fixed index). On merge or nightly: the full eval suite against the real model(s), producing a report with mean, confidence interval, per-category scores and diffs against the stored baseline; the pipeline fails on threshold or regression breaches. For release, a canary or A/B rollout with online metrics (feedback, escalations, cost, latency) and automatic rollback triggers. Store reports as build artefacts, update the baseline only on approved releases, and require eval reports in the PR description for prompt changes.
    level: architect
---

## Why it matters

Evals only protect you if they run automatically and can block a release. This lesson turns the toolkit into a gate — the AI equivalent of a failing unit test stopping a merge.

## Concept

```mermaid
flowchart LR
    PR[pull request: prompt / model / retrieval change] --> U[unit tests with fakes]
    U --> CE[component evals: retrieval metrics]
    CE --> MERGE[merge]
    MERGE --> NIGHT["full eval suite vs real model"]
    NIGHT --> GATE{"mean ≥ threshold AND mean ≥ baseline − tolerance?"}
    GATE -->|no| BLOCK[block release + report diff]
    GATE -->|yes| CANARY[canary / A-B rollout]
    CANARY --> ONLINE[online metrics: feedback, escalations, cost]
    ONLINE -->|new failures| DATA[(eval dataset)]
    DATA --> NIGHT
```

A good eval report contains: dataset version, system version (prompt hashes, model ids), mean with **confidence interval**, per-category scores, the list of failed cases with outputs, and the diff against the baseline.

## Build it from scratch

<!-- code: lessons/p12-evals/src/main/java/dev/aieng/p12/EvalRunner.java#run -->

The bootstrap confidence interval:

<!-- code: lessons/p12-evals/src/main/java/dev/aieng/p12/EvalRunner.java#bootstrap -->

The test `evalRunnerGatesOnThresholdAndBaseline` runs a four-case dataset where the system answers a canteen question it should have refused. The overall mean is 0.75 — above the 0.7 threshold — but the per-tag report shows `refusal: 0.0`; and the same run **fails** when compared against a 0.90 baseline.

### Dogfooding: the course app's retrieval eval

The course application gates its own Tutor retrieval in the build: `app/src/test/java/.../TutorRetrievalEvalTest.java` runs a set of questions with known answering lessons through the hybrid search and fails the build if recall@5 or MRR drop below thresholds.

## Now with the library

- **JUnit 5:** run eval suites as tests tagged `@Tag("eval")`; include or exclude them by Maven profile.
- **CI:** GitHub Actions, GitLab CI or Jenkins jobs with an Ollama service container or a provider API key from the secret store.
- **Reports:** write JSON/Markdown reports to `target/` and publish as build artefacts; trend them in a dashboard (Langfuse, MLflow, or a simple database table).

## Production notes

- **Budget eval cost:** a 500-case suite with an LLM judge can cost real money per run; run full suites nightly and a smoke subset per PR.
- **Update baselines deliberately** — only on approved releases, never automatically on every run.
- **Flaky evals erode trust:** use enough cases, fixed seeds where possible, and tolerances informed by the confidence interval.

## Exercises

1. **Easy** — Add a `toMarkdown()` method to `Report` that prints mean, CI, per-tag scores and failures.
2. **Medium** — Store the baseline in `src/test/resources/evals/baseline.json` and load it in a JUnit test that runs `EvalRunner`.
3. **Hard** — Add a GitHub Actions workflow with an Ollama service container that pulls a small model and runs `*IT` eval tests nightly, publishing the Markdown report as an artefact.

## Common mistakes

- Evals that run only on a developer's laptop.
- Automatically overwriting the baseline.
- Reporting only the mean.

## Further reading

- Efron & Tibshirani, *An Introduction to the Bootstrap* (1993).
- Langfuse / MLflow LLM evaluation documentation.
