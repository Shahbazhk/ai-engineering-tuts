---
title: Rate Limiting and Cost Control in Tokens
summary: Token-bucket limits per tenant, reserve-then-reconcile accounting, hard monthly budgets and cost attribution by feature.
minutes: 40
prereqs: [p14-llmops/02-resilience]
tags: [llmops, rate-limiting, cost, finops]
quiz:
  - q: Why limit by tokens rather than by requests?
    options:
      - Tokens are easier to count
      - Requests vary from tens to tens of thousands of tokens; token limits track real cost and provider quotas
      - Providers forbid request limits
      - Requests cannot be counted
    answer: 1
    why: A request limit lets one huge prompt consume a whole budget.
  - q: Why reserve estimated tokens before the call and reconcile afterwards?
    options:
      - To double-charge users
      - The real usage is only known after the call; reserving prevents many concurrent requests from overshooting the limit
      - It is required by HTTP
      - To reduce latency
    answer: 1
    why: Reconciliation then returns unused reservation (or charges the excess).
  - q: Which tag is most useful on token metrics for FinOps?
    options: [Random request id, The product feature (and tenant) that consumed the tokens, The JVM version, The pod IP]
    answer: 1
    why: Cost attribution per feature and tenant drives pricing, budgeting and optimisation decisions.
interview:
  - q: Design cost controls for a multi-tenant AI SaaS feature.
    a: |
      Layer them. Per-request: max input tokens (trim context), max output tokens, and model choice by plan. Per-tenant: token-bucket rate limits for burst control and monthly token or currency budgets tied to the subscription, with soft alerts at 80% and hard stops or degradation (cheaper model) at 100%. Global: provider quota awareness and a kill switch. Accounting: reserve estimated tokens, reconcile with actual usage from the response, and emit metrics tagged by tenant, feature and model; reconcile monthly against provider invoices. Expose usage to tenants in the UI. Store limits in configuration or a database so sales and support can adjust them without deployments.
    level: architect
---

## Why it matters

LLM costs scale with usage you don't fully control: a single tenant, a buggy agent loop or a scraping bot can burn a month's budget in an afternoon. Rate limits and budgets are financial safety mechanisms, not just fairness tools.

## Concept

**Token bucket:** each tenant has a bucket of capacity $C$ tokens that refills at $C/60$ tokens per second (for a per-minute limit). A request may proceed if the bucket holds at least its estimated tokens.

```mermaid
sequenceDiagram
    participant R as Request (tenant acme)
    participant B as TokenBudget
    participant M as Model
    R->>B: tryAcquire(acme, estimate 2,000)
    alt enough tokens and monthly budget
        B-->>R: allowed (bucket −2,000)
        R->>M: call
        M-->>R: usage: 500 tokens
        R->>B: reconcile(estimate 2,000, actual 500) → +1,500 back
    else
        B-->>R: "rate limited, retry in 10s" or "monthly budget exhausted"
    end
```

| Control | Protects against |
|---|---|
| Max input/output tokens per request | Giant prompts, runaway generations |
| Token bucket per tenant/user | Bursts, abuse, noisy neighbours |
| Monthly budget per tenant/feature | Spend beyond plan or forecast |
| Global quota guard | Provider rate limits for the whole organisation |
| Kill switch | Incidents |

## Build it from scratch

<!-- code: lessons/p14-llmops/src/main/java/dev/aieng/p14/TokenBudget.java#acquire -->

The test drives it with a movable clock: a 6,000-tokens-per-minute bucket admits 5,000, refuses a further 2,000 with "retry in 10s", serves another tenant independently, refills after ten seconds, returns unused reservation on reconcile, and finally refuses with "monthly budget exhausted".

## Now with the library

- **Resilience4j RateLimiter** limits *calls* per period — combine it with a token budget like this one for cost.
- **Distributed limits:** a single JVM's map does not limit a cluster; use Redis (e.g. Bucket4j with a Redis backend) for shared buckets.
- **Spring AI usage:** `ChatResponse.getMetadata().getUsage()` gives prompt and completion tokens for reconciliation.

## Production notes

- **Estimate conservatively** (prompt tokens + max_tokens) and reconcile — never trust estimates for billing.
- **Tag every token metric** with tenant, feature and model (next lessons).
- **Degrade before you deny:** switch over-budget tenants to a cheaper model if the product allows.
- **Reconcile with invoices** monthly; differences reveal untracked usage.

## Exercises

1. **Easy** — Add a soft-limit warning at 80% of the monthly budget to `Decision`.
2. **Medium** — Implement the same bucket with Bucket4j and compare behaviour in a test.
3. **Hard** — Make the budget distributed with Redis (Testcontainers) and prove that two `TokenBudget` instances share one tenant's bucket.

## Common mistakes

- Request-count limits only.
- Per-instance limits in a scaled-out deployment.
- No cost attribution, so nobody knows which feature is expensive.

## Further reading

- Bucket4j documentation.
- FinOps Foundation, "FinOps for AI" working group materials.
