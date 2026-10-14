---
title: Agent Memory — Short-Term, Summaries and Long-Term
summary: Keep agents coherent over long tasks and across sessions — bounded windows, running summaries, and per-user long-term memory with retrieval.
minutes: 40
prereqs: [p10-agents/03-tool-registry-design]
tags: [agents, memory, context-engineering]
quiz:
  - q: What happens to messages evicted from the short-term window in the implementation?
    options:
      - They are lost
      - They are folded into a running summary by a model call, which is prepended as a system message
      - They move to long-term memory automatically
      - They are re-sent anyway
    answer: 1
    why: Summarisation keeps important earlier context at bounded cost.
  - q: Why must long-term memory be scoped per user?
    options:
      - For speed
      - Memories contain personal data and preferences; recalling another user's memories is a privacy breach
      - Embeddings require it
      - It is cheaper
    answer: 1
    why: The test longTermMemoryIsScopedPerUser enforces it.
  - q: What is a risk of agents writing their own long-term memories automatically?
    options:
      - Memories are too small
      - Incorrect or injected content can be persisted and influence all future sessions (memory poisoning)
      - Memories are deleted
      - No risk
    answer: 1
    why: Treat memory writes like data writes — validate, attribute and allow users to view and delete them.
interview:
  - q: How do you give a long-running agent enough context without exceeding budgets?
    a: |
      Combine techniques: keep a bounded window of recent turns; summarise older turns and completed sub-tasks into compact notes; store durable facts and artefacts outside the transcript (a scratchpad file, a task database, long-term memory) and retrieve them when relevant; truncate or summarise large tool outputs before they enter the context; and isolate sub-tasks in sub-agents with fresh context, returning only their conclusions. Track token usage per step and trigger compaction at thresholds. Evaluate that summaries preserve the facts later steps need.
    level: architect
---

## Why it matters

Agents run for many steps; assistants serve users over many sessions. Without a memory strategy, context grows until it is too expensive, too slow, or too long for the model to use well — or the agent forgets crucial facts.

## Concept

| Tier | Holds | Lifetime | Mechanism |
|---|---|---|---|
| Working context | Current task transcript | One run | Window of recent messages |
| Summary | Compressed older turns | One run / session | Model call on eviction |
| Long-term | Facts, preferences, past outcomes | Across sessions | Store + retrieval (embeddings) |
| External state | Files, tickets, task records | Durable | Tools read and write it |

```mermaid
flowchart LR
    NEW[new message] --> W["window (last N)"]
    W -->|evicted| S["summariser → running summary"]
    FACT["explicit save: remember that…"] --> LT[(long-term store, per user)]
    Q[next step] --> CTX["context = summary + window + recalled facts"]
    S --> CTX
    W --> CTX
    LT -->|recall by relevance| CTX
```

## Build it from scratch

Short-term window with summarisation:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Memory.java#shortterm -->

Long-term memory, scoped by user id:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Memory.java#longterm -->

The tests check that the third message evicts the first into a summary, and that recall never crosses users.

## Now with the library

- Spring AI: `MessageWindowChatMemory` + `ChatMemoryRepository` (JDBC, Cassandra, Neo4j…) for short-term; a `VectorStore` with a `userId` metadata filter for long-term recall; `VectorStoreChatMemoryAdvisor` retrieves relevant past messages instead of the latest ones.
- LangChain4j: `MessageWindowChatMemory`, `TokenWindowChatMemory`, and `ChatMemoryProvider` keyed by memory id.

## Production notes

- **Memory is personal data** — retention policies, encryption, user-visible "what I remember about you", deletion on request.
- **Guard memory writes** against injection: only save facts from the user's own messages, never instructions from retrieved documents or tool outputs.
- **Summaries lose detail.** Keep critical identifiers (ticket numbers, amounts) verbatim in structured state, not in prose summaries.

## Exercises

1. **Easy** — Change the window to 4 and show which messages are summarised after 6 additions.
2. **Medium** — Implement a token-based window using `ContextBudget.estimateTokens` from Phase 7.
3. **Hard** — Replace keyword recall with embeddings via a Spring AI `VectorStore`, using `userId` as a filter expression. Write a test proving another user's facts are never returned even when they are the closest vectors.

## Common mistakes

- Unbounded transcripts.
- Global, unscoped memory.
- Letting tool outputs write to long-term memory.

## Further reading

- Packer et al., "MemGPT: Towards LLMs as Operating Systems", 2023.
- Spring AI chat memory documentation.
