---
title: ROI, Cost Models and Build vs Buy
summary: A transparent business case where quality is an explicit input, the break-even quality bar becomes an eval target, and build-vs-buy is decided on total cost of ownership.
minutes: 40
prereqs: [p17-delivery-leadership/03-scrum-for-ai]
tags: [leadership, roi, business-case, build-vs-buy]
quiz:
  - q: Why include review time per task in the ROI model?
    options:
      - It is required by accounting
      - Checking AI output costs human time; ignoring it overstates savings, sometimes into a loss
      - It increases benefit
      - Review is free
    answer: 1
    why: Net time saved = usable rate × time saved − review time.
  - q: What is the "break-even usable rate"?
    options:
      - The model's accuracy on a public benchmark
      - The minimum share of usable AI outputs at which benefits cover costs — a quality bar your eval suite must prove
      - The adoption rate
      - The discount rate
    answer: 1
    why: It connects the business case directly to evaluation.
  - q: Which cost is most often forgotten in build-vs-buy comparisons?
    options: [Licence fees, Ongoing evaluation, prompt/model maintenance, monitoring and support after launch, Hardware, Training sessions]
    answer: 1
    why: AI features are living systems; run costs continue after delivery.
interview:
  - q: Build or buy an AI assistant for your intranet?
    a: |
      Compare total cost of ownership and fit over 2–3 years. Buy (vendor copilot or SaaS search assistant) wins on time-to-value and maintenance when needs are standard, integrations exist, and data terms, residency and permission handling meet requirements. Build (on a platform like Spring AI + existing search) wins when content and permissions are complex (multi-site Liferay, partner portals), deep integration with internal systems or workflows is needed, differentiation matters, or vendor data terms are unacceptable. Score both on quality (pilot with your eval set — insist vendors run it), security and compliance, integration effort, run cost per user/query, lock-in and exit cost, and roadmap control. Hybrid is common: buy commodity capabilities, build domain-specific ones on shared platform components.
    level: architect
---

## Why it matters

AI business cases are often either wildly optimistic ("saves 50% of everyone's time") or impossible to evaluate. A small explicit model — where every assumption is visible — makes the case debatable, testable and linkable to engineering targets.

## Concept

$$\text{net benefit} = N \cdot a \cdot \left(u \cdot t_{\text{saved}} - t_{\text{review}}\right) \cdot \frac{c_{\text{hour}}}{60} - \left(N \cdot a \cdot c_{\text{AI}} + C_{\text{platform}}\right)$$

with $N$ tasks/month, adoption $a$, usable rate $u$ (from evals and the pilot), minutes saved $t_{\text{saved}}$, review minutes $t_{\text{review}}$, loaded hourly cost $c_{\text{hour}}$, AI cost per task $c_{\text{AI}}$ and fixed platform cost $C_{\text{platform}}$. Payback = build cost / net monthly benefit.

```mermaid
flowchart LR
    EVAL["eval suite → usable rate u"] --> MODEL[ROI model]
    PILOT["pilot → adoption a, review time"] --> MODEL
    COST["cost model (Phase 6) → c_AI"] --> MODEL
    MODEL --> BE["break-even u*: the quality bar"]
    BE --> EVAL
```

## Build it from scratch

<!-- code: lessons/p17-delivery-leadership/src/main/java/dev/aieng/p17/BusinessCase.java#roi -->

`BusinessCase.main` with illustrative support assumptions (180,000 tickets/month, 70% adoption, 75% usable outputs, 4 minutes saved, 0.5 minutes review, €45/hour):

```text
benefit €236,250/month, cost €11,040/month, net €225,210/month, payback 1.1 months
break-even requires >= 15% usable outputs
```

The interesting number is the last one: below 15% usable outputs the feature loses money. That is a concrete quality target for the eval suite, agreed with finance before the build. The test checks the arithmetic by hand and shows a case where a 10% usable rate never pays back.

Treat optimistic outputs like this one sceptically: run sensitivity analysis on adoption, usable rate and review time — the three assumptions most often wrong.

## Build vs buy

| Factor | Buy favours | Build favours |
|---|---|---|
| Requirements | Standard use cases | Complex permissions, domain workflows |
| Time to value | Weeks | Months |
| Data terms / residency | Acceptable vendor terms | Strict internal requirements |
| Integration | Existing connectors | Deep integration (Liferay, ERP, custom systems) |
| Differentiation | Commodity | Competitive advantage |
| Run cost | Per-seat licences | Per-token + platform team |
| Exit | Lock-in risk | Own the stack |

## Production notes

- **Measure the inputs in the pilot** — adoption, usable rate and review time — and update the case.
- **Report actuals monthly** against the case; it builds credibility for the next initiative.
- **Include the platform team** in run costs (evals, monitoring, model updates).

## Exercises

1. **Easy** — Run `BusinessCase.main` with adoption 0.3 and review time 2 minutes. What happens?
2. **Medium** — Add a sensitivity table: net benefit for usable rate 0.4–0.9 and adoption 0.2–0.8.
3. **Hard** — Build a build-vs-buy scorecard for an intranet assistant with weights agreed with stakeholders, and fill it for one vendor and the Phase 16 design.

## Common mistakes

- Ignoring review time and adoption.
- Benchmarks as a proxy for your usable rate.
- One-off build cost without run cost.

## Further reading

- Phase 6 lesson 1 (cost per request) and Phase 12 (evals) of this course.
