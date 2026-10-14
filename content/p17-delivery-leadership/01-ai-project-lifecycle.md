---
title: The AI Project Lifecycle — Discovery to Operations
summary: How AI initiatives differ from ordinary software projects, the phases and gates that de-risk them, and how this maps onto PMP and Scrum practices you know.
minutes: 40
prereqs: [p16-architecture/08-case-code-review-agent]
tags: [leadership, lifecycle, discovery, pmp]
quiz:
  - q: What is the main difference between an AI feature and a classic CRUD feature from a delivery perspective?
    options:
      - AI features need no testing
      - Feasibility and quality are uncertain until measured on real data, so early phases are experiments with go/no-go gates
      - AI features are always larger
      - AI features need no requirements
    answer: 1
    why: You cannot know upfront whether 85% accuracy is reachable; plan to find out cheaply.
  - q: What should a discovery phase produce before significant build investment?
    options:
      - Production code
      - A defined success metric, an evaluation set, a feasibility spike result and a cost estimate
      - A marketing plan
      - A fine-tuned model
    answer: 1
    why: These turn an idea into a decision with evidence.
  - q: Which PMP concept maps most directly onto AI go/no-go gates?
    options: [Resource levelling, Phase gates / stage reviews with explicit exit criteria, Earned value only, Procurement]
    answer: 1
    why: Stage gates with measurable criteria (eval thresholds, cost per task) suit AI's uncertainty.
interview:
  - q: How do you run an AI initiative from idea to production as a delivery lead?
    a: |
      In gated stages. Discovery (1–3 weeks): problem, users, success metric tied to a business KPI, data availability and classification, risk tier, rough cost; build an initial eval set with domain experts. Feasibility spike (1–3 weeks): simplest approach (prompt + RAG) measured on the eval set; go/no-go against thresholds. MVP/pilot (4–8 weeks): production-grade slice with guardrails, observability and feedback for a pilot group; gate on offline and online metrics. Scale: more users, sources, languages; cost optimisation (routing, caching, distillation). Operate: monitoring, eval regressions, incident handling, periodic model and prompt updates. Throughout, Scrum cadence with experiments as backlog items, governance reviews proportional to risk, and transparent forecasts as distributions.
    level: architect
---

## Why it matters

Most failed AI initiatives fail at management level: no success metric, no evaluation data, a demo mistaken for a product, or a plan that assumes the hardest part is certain. Your PMP and PSM toolkits apply — once adapted to uncertainty.

## Concept

```mermaid
flowchart LR
    D["Discovery: problem, metric, data, risk tier, eval set v0"] --> G1{gate}
    G1 -->|go| F["Feasibility spike: simplest approach vs eval set"]
    F --> G2{"thresholds met?"}
    G2 -->|no, iterate| F
    G2 -->|no, stop| STOP[stop / pivot — a cheap, good outcome]
    G2 -->|yes| P["Pilot: production slice + guardrails + feedback"]
    P --> G3{"offline + online metrics?"}
    G3 -->|yes| S["Scale: users, sources, cost optimisation"]
    S --> O["Operate: monitor, evaluate, update models/prompts"]
    O -->|drift / new needs| F
```

| AI-specific uncertainty | Management response |
|---|---|
| Can the model reach the quality bar? | Feasibility spike with eval set before committing |
| Is the data available, clean, allowed? | Data assessment in discovery; legal early |
| Will users trust and adopt it? | Pilot with feedback, staged autonomy |
| Will costs scale acceptably? | Cost model from day one (Phase 6) |
| Will the model/provider change under us? | Abstractions, evals, ADR revisit triggers |

## Mapping to what you know

| PMP / Scrum | AI adaptation |
|---|---|
| Business case | ROI model with quality as an explicit input (lesson 4) |
| Phase gates | Go/no-go on eval thresholds and cost per task |
| Scope statement | Success metric + eval set + out-of-scope categories |
| Risk register | Adds model, data, safety and regulatory risks (lesson 5) |
| Definition of Done | Includes eval thresholds and safety tests (lesson 3) |
| Sprint goal | Often "move metric X from A to B", not "build feature Y" |
| Stakeholder management | Expectation setting about probabilistic behaviour |

## Build it

A one-page **AI initiative canvas** to complete in discovery:

| Field | Example (support triage) |
|---|---|
| Problem / user | Agents spend 2 min classifying each ticket |
| Success metric | Triage accuracy ≥ 90%; handling time −15% |
| Data | 200k labelled historical tickets; contains PII → pseudonymise |
| Approach to test first | Prompted model with few-shot + structured output |
| Eval set | 300 tickets, stratified by category, 50 edge cases |
| Risk tier | Limited (internal, human in the loop) |
| Cost guardrail | ≤ €0.01 per ticket |
| Kill criteria | < 80% accuracy after two iterations of the spike |

## Production notes

- **Celebrate cheap "no" decisions** — a stopped spike after two weeks is a success of the process.
- **Demos are not evidence;** eval results are.
- **Plan operations from the start** — an AI feature needs ongoing evaluation and updates like a living service.

## Exercises

1. **Easy** — Fill in the canvas for an AI idea from your organisation.
2. **Medium** — Define the exit criteria for each gate of that initiative.
3. **Hard** — Write a stage-gate review template for your PMO that includes eval results, cost model, risk tier and governance status.

## Common mistakes

- Committing a fixed scope and date before feasibility is known.
- No kill criteria.
- Treating the pilot as the finish line.

## Further reading

- CRISP-DM process model (and its limits for LLM products).
- Google, "People + AI Guidebook".
