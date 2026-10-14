---
title: A Reference Architecture for Enterprise AI
summary: The building blocks of production AI systems on the JVM, how they map to C4 containers, and how to record decisions with ADRs.
minutes: 45
prereqs: [p15-security-governance/06-audit-and-red-teaming]
tags: [architecture, reference-architecture, adr, c4]
quiz:
  - q: In the reference architecture, where do prompts, model ids and retrieval settings live?
    options:
      - Hard-coded in controllers
      - In versioned configuration owned by the AI application, released with evals and canaries
      - In the vector store
      - In the browser
    answer: 1
    why: They are behaviour and must be versioned, reviewed and rolled back like code.
  - q: What is the purpose of an Architecture Decision Record (ADR)?
    options:
      - To replace documentation
      - To capture one significant decision, its context, the options considered and the consequences, so later teams know why
      - To track bugs
      - To list team members
    answer: 1
    why: AI architectures change fast; ADRs make the reasoning (and the conditions for revisiting it) explicit.
  - q: Which component should own permission enforcement for retrieved content?
    options:
      - The model, via the system prompt
      - The retrieval layer (filters) and the source system checks, in deterministic code
      - The UI
      - The embedding model
    answer: 1
    why: Never rely on the model to hide content.
interview:
  - q: Present a reference architecture for AI capabilities across an enterprise.
    a: |
      Four planes. Experience: channels (portal, chat, IDE, APIs) calling AI applications. AI applications: purpose-specific Spring Boot services (assistant, triage, document processing) that own prompts, retrieval configuration, tools, guardrails and evals for their domain. Shared AI platform: model gateway (routing, quotas, credentials, redaction, logging), embedding service, vector/search platform, ingestion framework, MCP servers for systems of record, evaluation and observability stack, prompt/model registry. Foundations: identity (SSO/OAuth2), data platform and governance, security controls, Kubernetes and GPU capacity. Cross-cutting: governance (AI inventory, risk tiers, approvals), FinOps (cost attribution) and SRE. Teams build applications on paved roads provided by the platform; decisions are captured in ADRs with explicit revisit triggers because the technology moves quickly.
    level: architect
---

## Why it matters

Individual techniques — RAG, agents, guardrails — only deliver value inside a coherent architecture with clear ownership. As an architect you will be asked to draw it, defend it and evolve it.

## Concept: the layers

```mermaid
flowchart TB
    subgraph Experience
        PORTAL[Liferay portal / web] --- CHAT[chat UI] --- IDE[IDE assistants] --- API[partner APIs]
    end
    subgraph Apps["AI applications (Spring Boot, one per capability)"]
        ASSIST[assistant] --- TRIAGE[ticket triage] --- DOC[document processing] --- AGENT[workflow agents]
    end
    subgraph Platform["shared AI platform"]
        GW[model gateway: routing, quotas, redaction, logging]
        EMB[embedding service]
        SEARCH[(search + vector platform)]
        ING[ingestion framework]
        MCP[MCP servers for systems of record]
        EVAL[evals + observability + prompt/model registry]
    end
    subgraph Foundations
        IAM[identity: SSO / OAuth2] --- DATA[data platform + governance] --- K8S[Kubernetes + GPU pools] --- SEC[security controls]
    end
    Experience --> Apps
    Apps --> Platform
    Platform --> Foundations
```

### Responsibilities

| Component | Owns |
|---|---|
| AI application | Prompts, retrieval config, tools selection, guardrails, evals for its domain |
| Model gateway | Provider credentials, routing, fallbacks, quotas, central logging policy |
| Search/vector platform | Indexes, hybrid search, permission metadata model |
| Ingestion framework | Connectors, chunking, embedding, change events, deletion |
| MCP servers | Narrow, permission-preserving access to systems of record |
| Eval/observability | Datasets, CI gates, GenAI telemetry, dashboards |

### Architecture Decision Records

```markdown
# ADR-007: Hybrid retrieval on OpenSearch for the intranet assistant
Status: Accepted (2026-10-14) — revisit if recall@5 < 0.85 or index > 50M chunks
Context: Liferay already indexes content in OpenSearch; queries contain product codes and prose.
Options: (1) pgvector only (2) OpenSearch BM25 + kNN with RRF (3) dedicated vector DB
Decision: (2) — one engine, existing operations, hybrid search native.
Consequences: + no new database; + identifier queries work; − HNSW memory on data nodes; − tied to OpenSearch versions.
```

Add a **revisit trigger** to every AI ADR — the landscape changes fast.

## Build it

The course application is itself a miniature of this architecture: a content ingestion step (`ContentLoader`), a search platform (`Bm25Index` + `SemanticIndex` + RRF), an AI application (`AiService` tutor with grounding and citations), safe output rendering, provider selection by configuration, Actuator for operations, and tests that act as quality gates (`TutorRetrievalEvalTest`). Read its code with this lesson's diagram in mind.

## Production notes

- **Platform vs product:** centralise what is truly common (gateway, search, evals); keep domain logic in product teams.
- **Paved roads:** make the compliant path the easiest path — templates, starters, shared libraries.
- **C4 diagrams:** context and container diagrams for every AI system, kept with the ADRs.

## Exercises

1. **Easy** — Draw the C4 container diagram of the course application.
2. **Medium** — Write two ADRs for the Liferay RAG assistant: vector store choice and model hosting choice, with revisit triggers.
3. **Hard** — Map your organisation's current systems onto the four planes and identify the three most valuable platform components to build first.

## Common mistakes

- Every team building its own gateway, ingestion and eval tooling.
- A central AI team that owns all prompts for all products (bottleneck).
- Decisions without recorded rationale.

## Further reading

- Michael Nygard, "Documenting Architecture Decisions" (2011).
- Simon Brown, The C4 model: <https://c4model.com/>
