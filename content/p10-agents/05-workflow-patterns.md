---
title: The Five Workflow Patterns
summary: Prompt chaining, routing, parallelisation, orchestrator-workers and evaluator-optimiser — implemented in plain Java with virtual threads.
minutes: 50
prereqs: [p10-agents/04-agent-memory]
tags: [agents, workflows, patterns, concurrency]
quiz:
  - q: Which pattern fits "classify the request, then use a specialised prompt and model for each class"?
    options: [Prompt chaining, Routing, Evaluator-optimiser, Parallelisation]
    answer: 1
    why: Routing separates concerns so each handler can be optimised (and cheaper models used for easy classes).
  - q: In orchestrator-workers, why cap the number of sub-tasks the planner can create?
    options:
      - Planners are always wrong
      - The model must not decide how much money and time a request may consume; budgets belong to your code
      - Workers can only run twice
      - It improves accuracy
    answer: 1
    why: Dynamic plans need explicit limits, like any user-controlled loop.
  - q: Parallel voting with three guard prompts blocks content if any votes unsafe. What does this trade?
    options:
      - Nothing
      - Higher recall of unsafe content for more false positives and three times the guard cost
      - Lower latency for lower accuracy
      - Fewer tokens
    answer: 1
    why: Aggregation rules encode the precision/recall trade-off (Phase 2, lesson 4).
interview:
  - q: How would you implement these patterns in a Spring Boot service?
    a: |
      As ordinary service code: each step is a method using a ChatClient with its own prompt, options and output type. Chaining is sequential calls with validation between steps; routing is a classifier call returning an enum, then a strategy map of handler beans; parallelisation uses virtual threads (Executors.newVirtualThreadPerTaskExecutor or structured concurrency) with timeouts; orchestrator-workers uses a planner with structured output (List of sub-tasks) capped by configuration; evaluator-optimiser loops with a rubric-based evaluator and a max-rounds limit. Add Micrometer observations per step, persist intermediate state for long workflows (or use a workflow engine like Spring Batch, Temporal or Camunda when durability and human tasks matter), and test each step with fake models plus an end-to-end eval.
    level: architect
---

## Why it matters

Most production "agentic" systems are compositions of these five patterns. They are predictable, testable and cheaper than open-ended agents — and they combine: a router can send hard cases to an agent and easy cases to a single call.

## Concept

```mermaid
flowchart TB
    subgraph Chain["1. Prompt chaining"]
        C1[extract] --> G1{gate} --> C2[transform] --> C3[format]
    end
    subgraph Route["2. Routing"]
        R0[classify] --> R1[billing handler]
        R0 --> R2[technical handler]
        R0 --> R3[fallback]
    end
    subgraph Par["3. Parallelisation"]
        P0[input] --> P1[worker A] & P2[worker B] & P3[worker C] --> PA[aggregate / vote]
    end
    subgraph Orch["4. Orchestrator-workers"]
        O0[planner decides sub-tasks] --> O1[worker] & O2[worker] --> O3[synthesiser]
    end
    subgraph Eval["5. Evaluator-optimiser"]
        E1[generate] --> E2{evaluate}
        E2 -->|feedback| E1
        E2 -->|approved| E3[done]
    end
```

| Pattern | Use when | Watch out for |
|---|---|---|
| Chaining | Task decomposes into fixed steps | Error propagation — add gates |
| Routing | Inputs fall into distinct classes | Misrouting — have a fallback |
| Parallelisation | Independent sub-tasks, or voting | Cost multiplies |
| Orchestrator-workers | Sub-tasks unknown until run time | Unbounded plans — cap them |
| Evaluator-optimiser | Clear quality criteria, iterative improvement helps | Endless loops — cap rounds |

## Build it from scratch

Chaining with a gate:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Workflows.java#chain -->

Routing:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Workflows.java#route -->

Parallelisation on virtual threads:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Workflows.java#parallel -->

Orchestrator-workers with a capped plan:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Workflows.java#orchestrator -->

Evaluator-optimiser:

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/Workflows.java#evaluator -->

The test `workflowPatterns` exercises each one with plain lambdas standing in for model calls — including a planner that proposes four sub-tasks and is capped at two, and an optimiser that stops when the third draft is approved.

## Now with the library

In Spring, each lambda becomes a service method using `ChatClient`:

```java
enum Route { BILLING, TECHNICAL, OTHER }

Route route = classifier.prompt().user(ticket).call().entity(Route.class);   // structured output
String reply = switch (route) {
    case BILLING -> billingAssistant.draft(ticket);       // cheap model, billing prompt
    case TECHNICAL -> technicalRag.answer(ticket);        // RAG over product docs
    case OTHER -> humanQueue.enqueue(ticket);
};
```

For long-running, durable workflows with human tasks, use a workflow engine (Spring Batch, Temporal, Camunda/Flowable) and call LLMs from its activities — don't reinvent durability in memory.

## Production notes

- **Instrument every step** (latency, tokens, outcome) — workflows are easy to observe, so do it.
- **Timeouts per parallel branch,** with a defined result when one fails.
- **Cache classifier results** for identical inputs.

## Exercises

1. **Easy** — Add a timeout to `parallel` (`Future.get(timeout)`) and decide what the aggregate receives when a branch times out.
2. **Medium** — Implement routing for support tickets with a fake classifier and three handlers, including a confidence threshold that sends low-confidence cases to the fallback.
3. **Hard** — Rewrite `parallel` with Java's structured concurrency API (`StructuredTaskScope`, preview in recent JDKs) so that a failing branch cancels the others.

## Common mistakes

- Unbounded orchestrator plans and evaluator loops.
- Running dependent steps in parallel.
- Using an agent where routing would do.

## Further reading

- Anthropic, "Building effective agents" (2024) — the source of this taxonomy.
- JEP 505: Structured Concurrency.
