---
title: Modular Monolith, Microservices and Event-Driven AI
summary: Where AI capabilities should live in your service landscape — Spring Modulith boundaries, when to split services, and Kafka-driven ingestion and enrichment pipelines.
minutes: 45
prereqs: [p16-architecture/01-reference-architecture]
tags: [architecture, spring-modulith, kafka, event-driven]
quiz:
  - q: When is a separate AI microservice justified?
    options:
      - Always
      - When it has different scaling, deployment cadence, runtime (GPU, Python) or ownership from the host application
      - Never
      - When the team is bored
    answer: 1
    why: Otherwise a well-bounded module inside the existing application is simpler.
  - q: Why use events for content ingestion instead of synchronous calls from the CMS?
    options:
      - Events are faster
      - Embedding and indexing are slow and failure-prone; events decouple the CMS from the AI pipeline, allow retries and replay, and keep publishing fast
      - The CMS cannot make HTTP calls
      - It removes the need for a vector store
    answer: 1
    why: The editor's publish action must not wait on an embedding provider.
  - q: What does Spring Modulith help enforce?
    options:
      - GPU scheduling
      - Module boundaries inside one Spring Boot application, with verifiable dependencies and application events between modules
      - Database sharding
      - Prompt versioning
    answer: 1
    why: It gives you microservice-like boundaries without distribution costs, and an easy path to extract modules later.
interview:
  - q: An existing Spring monolith needs AI features. Monolith module or new services?
    a: |
      Start with a well-bounded module in the monolith (Spring Modulith) when the AI feature is tightly coupled to domain data and transactions, uses hosted models through HTTP, and is owned by the same team — you avoid network hops, duplicated security and distributed data. Extract into separate services the parts with different operational profiles: ingestion workers (batchy, scale on backlog), self-hosted model servers (GPU), Python training jobs, and shared platform components used by many applications (gateway, embedding service, MCP servers). Use application events inside the monolith and Kafka between services so extraction later is mostly a transport change.
    level: architect
---

## Why it matters

AI features tempt teams into new microservices for everything. The right boundaries depend on scaling, runtime and ownership — and AI pipelines are naturally event-driven.

## Concept

```mermaid
flowchart LR
    subgraph Monolith["Spring Boot application (Spring Modulith)"]
        ORD[orders module] -- "OrderPlaced event" --> AIM[ai-assist module: prompts, retrieval, guardrails]
        TIC[tickets module] -- "TicketCreated event" --> AIM
    end
    AIM -->|HTTP| GW[model gateway]
    subgraph Pipeline["event-driven ingestion (separate services)"]
        CMS[Liferay / CMS] -->|content.changed| K[(Kafka)]
        K --> ING[ingestion worker: extract, chunk, embed]
        ING --> IDX[(vector + search index)]
        K --> ENR[enrichment worker: classify, summarise, tag]
        ENR -->|content.enriched| K
    end
    AIM --> IDX
```

| Put in a module (same app) | Extract to a service |
|---|---|
| Domain-coupled AI logic (triage of this app's tickets) | Workloads with different scaling (ingestion, batch enrichment) |
| Prompts and guardrails for this domain | GPU model servers, embedding services |
| Calls to hosted models via gateway | Python training/inference |
| | Shared platform pieces (gateway, MCP servers) |

**Event-driven patterns for AI:**

- **Ingestion on change:** `content.changed` → extract/chunk/embed → upsert; `content.deleted` → delete chunks.
- **Asynchronous enrichment:** classify, summarise or tag new records without blocking users; write results back via events.
- **Outbox pattern:** publish events transactionally with the domain change (Spring Modulith's event publication registry or a transactional outbox).
- **Replay:** re-embed everything after a model change by replaying events or a backfill topic.

## Build it

Spring Modulith events between modules (sketch):

```java
// tickets module
@Transactional
public Ticket create(NewTicket cmd) {
    Ticket t = repository.save(Ticket.from(cmd));
    events.publishEvent(new TicketCreated(t.id(), t.text()));   // persisted by the event publication registry
    return t;
}

// ai-assist module
@ApplicationModuleListener                                       // async, after commit, retried if it fails
void on(TicketCreated e) {
    var triage = ticketTriage.triage(e.text());                  // Phase 6 TicketTriage
    events.publishEvent(new TicketTriaged(e.id(), triage.category(), triage.priority()));
}
```

The same `TicketTriaged` event can later be externalised to Kafka (Spring Modulith supports event externalisation) if triage moves to its own service.

## Production notes

- **Idempotent consumers** (deterministic chunk ids, upserts) — events are delivered at least once.
- **Dead-letter topics** for documents that fail extraction or embedding; alert on growth.
- **Back-pressure on providers:** consumer concurrency sized to embedding rate limits.
- **Ordering:** key Kafka messages by document id so updates and deletes for one document stay in order.

## Exercises

1. **Easy** — List the modules of an application you know and mark which would host AI logic.
2. **Medium** — Implement `TicketCreated` → triage with Spring application events and a fake `ChatModel`, using `@TransactionalEventListener`.
3. **Hard** — Design the Kafka topics, keys, retention and DLQ strategy for ingesting Liferay content changes into a vector index, including replay for re-embedding.

## Common mistakes

- Synchronous embedding inside the publish transaction.
- Non-idempotent consumers creating duplicate chunks.
- Microservices for every prompt.

## Further reading

- Spring Modulith reference documentation.
- Chris Richardson, "Transactional outbox" pattern.
