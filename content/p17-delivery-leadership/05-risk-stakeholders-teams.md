---
title: Risk, Stakeholders and Building AI Teams
summary: An AI-aware risk register, communicating probabilistic systems to stakeholders, vendor risk, change management, and the team structures that work.
minutes: 40
prereqs: [p17-delivery-leadership/04-roi-and-business-case]
tags: [leadership, risk, stakeholders, teams]
quiz:
  - q: Which risk is specific to AI products and often missing from classic risk registers?
    options:
      - Budget overrun
      - Silent quality regression after a provider updates the model behind the same name
      - Staff holidays
      - Office relocation
    answer: 1
    why: Pin versions where possible, monitor quality and run evals on a schedule.
  - q: How should you explain an assistant's error rate to business stakeholders?
    options:
      - Promise it will be perfect
      - In business terms with ranges — e.g. "about 1 in 12 answers needs correction; here is how users notice and what happens next" — plus the mitigation design
      - Avoid the topic
      - Only show the best examples
    answer: 1
    why: Calibrated expectations prevent loss of trust at the first visible mistake.
  - q: Which team topology works well for scaling AI across an enterprise?
    options:
      - One central team building every AI feature
      - Stream-aligned product teams building AI features on a platform team's paved road, with an enabling team for skills and governance
      - No coordination at all
      - Outsourcing everything
    answer: 1
    why: Platform + enabling teams scale capability without becoming a bottleneck.
interview:
  - q: How do you manage stakeholder expectations for a generative AI project?
    a: |
      Early and concretely. In discovery, show real outputs — good and bad — on their data, not vendor demos. Agree on success metrics, acceptable error types and the human-in-the-loop design together. Communicate quality as rates with examples ("in the pilot, 92% of answers were rated correct; wrong answers mostly involved outdated pages, which we are addressing by…"). Use probabilistic schedules. Involve legal, works councils and data protection early where employees or customers are affected. Provide a feedback channel and show how feedback changes the system. Report cost and adoption alongside quality. Celebrate stopping unpromising ideas quickly.
    level: architect
---

## Why it matters

Technology rarely sinks AI programmes on its own; unmanaged risk, mismatched expectations and the wrong team structure do. This is where your PMP and Scrum experience adds the most value to AI engineering.

## Concept: AI risk register (additions to a classic register)

| Risk | Likelihood driver | Mitigation | Owner |
|---|---|---|---|
| Quality below threshold | Novel task, poor data | Feasibility spike, kill criteria | Product Owner |
| Silent model change by provider | Unpinned model aliases | Pin versions, scheduled evals, alerts | Tech lead |
| Data leakage across permissions | Complex permission models | Filters + source checks + red-team | Security |
| Regulatory non-compliance | High-risk use case | AI Act triage, legal review, documentation | Compliance |
| Vendor lock-in / price change | Single provider | Abstraction (Spring AI), second provider evaluated | Architect |
| Cost overrun | Usage growth, long prompts | Budgets, routing, caching, dashboards | Product + FinOps |
| Low adoption | Poor UX, distrust | Pilot, training, feedback loop | Product Owner |
| Workforce concerns | Automation fears | Early communication, works council, reskilling | Sponsor |

```mermaid
flowchart LR
    ID[identify risks in discovery] --> ASSESS[assess: likelihood × impact]
    ASSESS --> PLAN[mitigate: design controls, gates, owners]
    PLAN --> MON[monitor: evals, dashboards, incidents]
    MON --> ID
```

## Stakeholder communication

- **Show, don't promise:** real outputs on real data, including failures.
- **Rates with examples** instead of adjectives.
- **Explain the safety net:** citations, human approval, feedback, monitoring.
- **Make trade-offs explicit:** quality vs cost vs latency vs autonomy.

## Vendor risk

Assess providers like any critical supplier: data processing terms (retention, training on your data, sub-processors), regions, security certifications, SLAs, model deprecation policies, price change terms, and exit options. Keep a second provider evaluated through the same eval suite.

## Team structure

```mermaid
flowchart TB
    PLAT["AI platform team: gateway, search, evals, observability, MCP servers"] --> SA1[stream-aligned team: support]
    PLAT --> SA2[stream-aligned team: intranet / Liferay]
    PLAT --> SA3[stream-aligned team: finance documents]
    EN["enabling team: AI CoE — skills, patterns, governance, reviews"] -.-> SA1 & SA2 & SA3
```

Skills to grow in product teams: evaluation design, prompt and context engineering, retrieval, AI security — and domain experts embedded for labelling and review.

## Build it

Use this course's artefacts as your team kit: lesson templates for onboarding, the eval runner and CI gates as DoD enforcement, the AI Act triage in design reviews, the cost model and business case in planning, and the Monte Carlo forecaster in steering committees.

## Production notes

- **Review the risk register at every gate,** not only at kickoff.
- **Post-incident reviews** for AI failures, blameless and data-driven, feeding evals and guardrails.
- **Measure capability growth:** number of teams shipping AI features on the paved road.

## Exercises

1. **Easy** — Add five AI-specific risks to a register for one of your projects, with owners.
2. **Medium** — Write a one-page stakeholder update for an AI pilot: quality with examples, cost, adoption, risks, next steps.
3. **Hard** — Design the team topology and first-year roadmap for an AI platform team in your organisation, including what the paved road offers in quarter 1.

## Common mistakes

- Risk registers without AI-specific risks.
- Over-promising after a great demo.
- A central AI team as a delivery bottleneck.

## Further reading

- Skelton & Pais, *Team Topologies* (2019).
- PMI, *PMBOK Guide* — risk and stakeholder performance domains.
