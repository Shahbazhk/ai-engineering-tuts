---
title: Multi-Tenant AI Systems
summary: Isolate tenants across every AI layer — retrieval, prompts, memory, caches, fine-tuned adapters, quotas and costs — and choose between shared and dedicated resources.
minutes: 40
prereqs: [p16-architecture/02-modular-monolith-and-events]
tags: [architecture, multi-tenancy, saas, security]
quiz:
  - q: In a shared vector index, how is tenant isolation enforced?
    options:
      - By asking the model to ignore other tenants
      - With a mandatory tenant filter inside every retrieval query, set from the authenticated context (or separate indexes per tenant)
      - By embedding the tenant name
      - It cannot be
    answer: 1
    why: Isolation is a query-level guarantee, enforced in code, tested with cross-tenant cases.
  - q: Which AI-specific stores also need tenant scoping?
    options:
      - Only the database
      - Conversation memory, response and semantic caches, eval datasets, logs and fine-tuned adapters
      - None
      - Only the UI
    answer: 1
    why: Every place text or vectors are stored can leak across tenants.
  - q: When do you give a tenant dedicated resources (index, model deployment)?
    options:
      - Always
      - For contractual isolation, data residency, very large scale or noisy-neighbour problems — at higher cost
      - Never
      - Only for free-tier tenants
    answer: 1
    why: Shared-by-default with dedicated options is the common SaaS pattern.
interview:
  - q: Design tenant isolation for an AI assistant offered as SaaS to 300 enterprise customers.
    a: |
      Tenant id from the authenticated token drives everything; no API accepts a tenant id from the client. Retrieval: shared index with mandatory tenant filters for small tenants, dedicated indexes (or namespaces/collections) for large or regulated tenants; ingestion writes tenant metadata and deletion works per tenant. Prompts: shared templates with tenant configuration (tone, terminology), never other tenants' examples. Memory, caches, logs and eval datasets keyed by tenant; caches include the tenant in the scope. Models: shared gateway with per-tenant quotas and budgets; optional per-tenant LoRA adapters on shared base models; data-residency tenants routed to regional endpoints. Cost attribution and usage dashboards per tenant. Automated cross-tenant leakage tests in CI and red-team exercises before onboarding sensitive customers.
    level: architect
---

## Why it matters

Liferay DXP is built for multi-site, multi-tenant (virtual instance) deployments, and many enterprise AI products are SaaS. A single cross-tenant leak — one customer's document in another's answer — can end a product.

## Concept

```mermaid
flowchart TB
    TOKEN["OAuth2 token → tenant = acme (server-side only)"] --> APP[AI application]
    APP --> RET["retrieval: filter tenant == 'acme'"]
    APP --> MEM["memory: key acme:user:conversation"]
    APP --> CACHE["cache scope: acme|role|prompt-v3"]
    APP --> GW["gateway: quota + budget for acme"]
    GW --> MODEL["shared base model + optional acme LoRA adapter"]
    RET --> IDX[(shared index or acme-dedicated index)]
```

| Layer | Shared (default) | Dedicated (premium / regulated) |
|---|---|---|
| Vector/search index | One index, tenant filter | Index or collection per tenant |
| Model | Shared endpoint, per-tenant quotas | Dedicated deployment / region |
| Customisation | Prompt configuration per tenant | Per-tenant LoRA adapter |
| Keys | Platform key | Tenant-provided key (BYOK) |

## Build it

Tenant isolation patterns you have already implemented:

| Pattern | Where |
|---|---|
| Mandatory metadata filter in retrieval | `TenantSearch` (Phase 8, lesson 5) |
| Per-user/tenant memory | `Memory.LongTerm` (Phase 10), `ConversationService` ids (Phase 6) |
| Scoped semantic cache | `SemanticCache` scope key (Phase 14) |
| Per-tenant token budgets | `TokenBudget` (Phase 14) |

The architectural rule tying them together: **tenant id comes only from the server-side security context**, flows explicitly through every layer, and every store's key or filter includes it.

## Production notes

- **Cross-tenant tests in CI:** seed two tenants with conflicting content and assert no leakage across retrieval, memory and caches.
- **Per-tenant deletion** must be complete — indexes, memories, caches, logs, adapters.
- **Noisy neighbours:** per-tenant quotas and concurrency limits protect shared capacity.
- **Liferay virtual instances** map naturally to tenants; sites to sub-scopes within a tenant.

## Exercises

1. **Easy** — List every store in the course app and say how you would add a tenant dimension.
2. **Medium** — Write a JUnit test template that seeds two tenants and runs a list of queries against a `TenantSearch`, failing on any cross-tenant hit.
3. **Hard** — Design per-tenant LoRA serving: how adapters are trained, stored, versioned and selected per request, and how you evaluate each tenant's adapter.

## Common mistakes

- Tenant id as a request parameter.
- Forgetting caches and logs.
- Global few-shot examples drawn from one tenant's data.

## Further reading

- AWS, "SaaS tenant isolation strategies" whitepaper.
- Liferay DXP virtual instances and sites documentation.
