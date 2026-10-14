---
title: Semantic Caching Safely
summary: Reuse answers for equivalent questions — embeddings, strict thresholds, scopes that never cross tenants or permissions, TTLs and invalidation.
minutes: 35
prereqs: [p14-llmops/03-rate-limiting-and-cost-control]
tags: [llmops, caching, cost, security]
quiz:
  - q: What must be part of a semantic cache's scope key?
    options:
      - Only the question text
      - Tenant, permission set (or user group) and prompt/model version — anything that could make the right answer differ
      - The time of day
      - The HTTP method
    answer: 1
    why: The test shows a hit becoming a miss when the tenant or prompt version changes.
  - q: Why is a high threshold (e.g. 0.9+) needed?
    options:
      - Low thresholds are slower
      - Questions can be lexically similar yet need different answers; a loose threshold returns wrong answers confidently
      - Embeddings require it
      - To save memory
    answer: 1
    why: A wrong cached answer is worse than a cache miss.
  - q: When must cached answers be invalidated beyond TTL expiry?
    options: [Never, When the underlying source content, prompt or model changes, Only on restarts, Every second]
    answer: 1
    why: Include versions in the scope or purge entries derived from changed documents.
interview:
  - q: Where does semantic caching pay off, and where should you avoid it?
    a: |
      It pays off for high-volume, repetitive, non-personalised questions — FAQ-style support, documentation Q&A, product information — where many users ask the same thing in different words and answers change slowly. Avoid it for personalised or permission-dependent answers unless the scope key captures the personalisation, for conversational turns that depend on history, for agentic actions with side effects, and for fast-changing data. Measure hit rate and also wrong-hit rate (sample hits and check equivalence) before enabling broadly; start with exact-match caching, which has no false hits.
    level: senior
---

## Why it matters

Support and documentation assistants receive the same questions thousands of times in different words. A semantic cache can serve a large share from memory in milliseconds — if it never serves the wrong answer to the wrong person.

## Concept

```mermaid
flowchart LR
    Q["question + scope (tenant | role | prompt v3)"] --> E[embed]
    E --> L{"same scope AND similarity ≥ 0.9 AND not expired?"}
    L -->|hit| A[cached answer, ms latency]
    L -->|miss| M[model call] --> S[store with scope + TTL] --> A2[answer]
```

Rails:

1. **Scope** — exact match on tenant, permission set and prompt/model version.
2. **Threshold** — strict, calibrated on labelled pairs of equivalent / non-equivalent questions.
3. **TTL and invalidation** — expire entries, and purge when source content changes.
4. **Eligibility** — only cache answers that are not personalised and had no side effects.

## Build it from scratch

<!-- code: lessons/p14-llmops/src/main/java/dev/aieng/p14/SemanticCache.java#cache -->

The test stores one answer and shows: a paraphrase in the same scope hits; another tenant misses; a new prompt version misses; an unrelated question misses; and after the TTL the entry is gone.

## Now with the library

- **Spring Cache** (`@Cacheable`) for exact-match caching (Phase 7) — always the first step.
- **Vector stores with TTL** (Redis, Elasticsearch) for semantic caches at scale; Spring AI's `VectorStore` with metadata filters on the scope.
- **Provider prompt caching** (Phase 7) is complementary: it reduces cost of *misses* with shared prefixes.

## Production notes

- **Measure wrong hits,** not just hit rate: sample cached responses and verify equivalence.
- **Never cache errors, refusals caused by outages, or partial streams.**
- **Privacy:** cached answers may contain personal data; apply the same retention rules as logs.

## Exercises

1. **Easy** — Add `invalidateScope(String scopePrefix)` and test it.
2. **Medium** — Calibrate the threshold: label 40 question pairs (equivalent or not), compute similarities with a real embedding model, and choose the threshold that keeps wrong hits at zero.
3. **Hard** — Implement the cache on Redis with vector search and TTL, using Testcontainers in the test.

## Common mistakes

- Global, unscoped caches.
- Thresholds copied from blog posts.
- Caching personalised answers.

## Further reading

- Bang, "GPTCache", 2023.
- Redis vector similarity search documentation.
