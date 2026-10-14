---
title: "Case Study: Code-Review Agent for Java Repositories"
summary: An agent that reviews pull requests in a Spring monorepo — context gathering with tools, focused findings, false-positive control and safe integration with CI.
minutes: 45
prereqs: [p16-architecture/07-case-liferay-ai-search]
tags: [case-study, agents, developer-productivity, system-design]
quiz:
  - q: What is the biggest adoption risk for an AI code reviewer?
    options:
      - Slow builds
      - Too many low-value or wrong comments — developers learn to ignore it
      - Too few tokens
      - Lack of dark mode
    answer: 1
    why: Precision matters more than recall; measure accepted vs dismissed findings.
  - q: Why give the agent read-only tools (read file, search code, view tests) instead of pasting the whole repository?
    options:
      - Repositories are secret
      - Context windows are limited and costly; targeted retrieval of relevant code yields better findings at lower cost
      - Tools are faster to write
      - The model cannot read Java
    answer: 1
    why: Context engineering (Phase 7) applies to code too.
  - q: Should the agent push fixes directly to the main branch?
    options:
      - Yes, if confident
      - No — it comments or proposes suggested changes; humans and CI decide what merges
      - Only on Fridays
      - Only for tests
    answer: 1
    why: Least privilege and human approval for writes.
interview:
  - q: How would you evaluate and improve an AI code-review agent?
    a: |
      Build a benchmark of historical PRs with known issues (bugs fixed later, review comments that led to changes) plus clean PRs. Measure precision (fraction of agent comments developers accept or that match known issues), recall on seeded/known bugs, comment volume per PR, latency and cost. In production, track resolved vs dismissed comments per rule/category and per repository, suppress categories with low acceptance, and add severity thresholds. Improve with better context tools (call graph, tests, related files), project-specific guidelines in the prompt, examples of accepted comments, and verification steps (e.g. compile or run tests for suggested changes) before commenting.
    level: architect
---

## Problem

A platform team maintains a 2-million-line Spring monorepo with 150 contributors and 300 PRs per week. Reviews are slow and inconsistent. They want an agent that leaves a small number of **high-value** review comments within 10 minutes of a PR opening — bugs, security issues, missing tests — and nothing else.

## Architecture

```mermaid
flowchart LR
    PR[pull request opened] -->|webhook| Q[(queue)]
    Q --> AG["review-agent (Spring AI): plan → gather context → analyse → verify → comment"]
    AG -->|read-only tools| T1[get_diff]
    AG --> T2[read_file / search_code]
    AG --> T3[find_tests / call_graph]
    AG --> T4[run_static_checks]
    AG --> GW[model gateway]
    AG --> FILTER["finding filter: severity ≥ threshold, dedupe, max 5 per PR"]
    FILTER --> COMMENT[PR comments + suggested changes]
    COMMENT --> FB[(accepted / dismissed feedback)]
```

## Design highlights

| Element | Decision | Course link |
|---|---|---|
| Agent loop | Bounded steps, token budget, read-only tools | Phase 10 L2/L6 |
| Context | Diff + targeted file reads + related tests, not whole repo | Phase 7 L4 |
| Output | Structured findings (`file`, `line`, `severity`, `category`, `explanation`, `suggestion`) | Phase 6 L5 |
| Precision control | Severity threshold, max comments per PR, category kill-switches from feedback | Phase 12 |
| Verification | Suggested changes compiled/tests run in a sandbox before posting where feasible | Phase 15 |
| Security | Repo token scoped to read + comment; no merge or push rights; secrets never in prompts | Phase 15 |

## Evaluation

- **Offline benchmark:** 200 historical PRs with known issues + 100 clean PRs; measure precision, recall, comments per PR.
- **Online:** accepted vs dismissed comments per category; time-to-merge; developer survey.
- **Gate:** precision ≥ 70% on benchmark before enabling in a repository; per-category disablement below 50% acceptance.

## Risks

| Risk | Mitigation |
|---|---|
| Noise erodes trust | Strict thresholds, comment caps, feedback-driven suppression |
| Injection via code comments or PR descriptions | Treat repo text as data; tools read-only; no external network |
| Leaking proprietary code to providers | Approved provider with DPA, region, no training; or self-hosted model |
| Cost spikes on huge PRs | Size limits; summarise-only mode for large diffs |

## Exercises

1. **Easy** — Define the `Finding` record and validation rules.
2. **Medium** — Write the system prompt with project guidelines and examples of good and bad comments.
3. **Hard** — Build a small version with Phase 10's `ReactAgent`: tools `get_diff` and `read_file` over a local git checkout, a scripted fake model in tests, and a finding filter.

## Further reading

- Phase 10 (agents), Phase 12 (evals), Phase 15 (security) of this course.
