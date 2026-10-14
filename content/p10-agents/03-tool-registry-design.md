---
title: Designing a Safe Tool Registry
summary: Tools as a governed catalogue — descriptions, validation at the trust boundary, read vs write risk, approvals, and errors as observations.
minutes: 45
prereqs: [p10-agents/02-react-agent-from-scratch]
tags: [agents, tools, security, design]
quiz:
  - q: Where must tool arguments be validated?
    options:
      - In the prompt
      - In the registry/tool at invocation time — arguments come from the model and are untrusted input
      - Nowhere; models produce valid JSON
      - Only in the UI
    answer: 1
    why: The tool boundary is a trust boundary, like a REST controller receiving external input.
  - q: What should a side-effecting tool require that a read-only tool does not?
    options:
      - A longer description
      - An approval decision (human or policy) before execution, plus an audit record
      - A different model
      - Nothing
    answer: 1
    why: Separating risk levels lets you grant autonomy for reads while keeping humans in control of writes.
  - q: An agent has 60 tools available on every step. What problems does this cause?
    options:
      - None
      - Tool definitions consume many tokens per step and the model chooses less reliably among many similar tools
      - The model becomes faster
      - Tools stop working
    answer: 1
    why: Keep catalogues small and task-specific, or select tools dynamically per task.
interview:
  - q: How would you expose existing enterprise APIs as agent tools safely?
    a: |
      Don't expose raw APIs. Design task-level tools ("get_order_status(orderId)", "draft_refund(orderId, reason)") that wrap APIs with validation, narrow parameters and summarised outputs. Execute them under the end user's identity with the same authorisation as the UI, never a superuser. Classify each tool's risk; read-only tools can run autonomously, write tools return drafts or require approval, irreversible tools may be excluded. Add rate limits, idempotency keys, timeouts and circuit breakers, return errors as structured observations, and log every invocation. Publish them through a registry (or an MCP server, Phase 11) with versioned descriptions that are reviewed like API contracts.
    level: architect
---

## Why it matters

Tools are where an agent touches the real world. The registry is the place to enforce what the model may do — and the place most agent incidents could have been prevented.

## Concept

Think of the registry as a **small `BeanFactory` with a security manager**: tools are registered by name with metadata, resolved at run time, and every invocation passes through validation and policy.

```mermaid
flowchart LR
    M[model: Action + JSON args] --> R{registry.invoke}
    R -->|unknown tool| E1["ERROR observation"]
    R -->|missing or oversized args| E2["ERROR observation"]
    R -->|SIDE_EFFECT| AP{approver}
    AP -->|denied| E3["DENIED observation"]
    AP -->|approved| X[execute as the user]
    R -->|READ_ONLY| X
    X -->|exception| E4["ERROR observation"]
    X --> OK[result observation]
```

Design rules:

| Rule | Why |
|---|---|
| Task-level, narrow tools | `ticket_status(id)` is safe; `http_get(url)` is an exfiltration channel |
| Clear names, descriptions, parameter docs | Descriptions are the model's API documentation |
| Validate every argument | Model output is untrusted |
| Risk metadata (read vs write) | Different autonomy per risk class |
| Errors as observations | The model can recover; the loop does not crash |
| Run as the end user | Authorisation stays where it already works |
| Audit every call | Accountability and debugging |

## Build it from scratch

<!-- code: lessons/p10-agents/src/main/java/dev/aieng/p10/ToolRegistry.java#invoke -->

The tests check that unknown tools and missing parameters produce `ERROR` observations, and (`sideEffectsNeedApproval`) that a denied `close_ticket` call is **never executed** — the agent receives `DENIED…` and explains the outcome to the user instead.

## Now with the library

| Concept | Spring AI | LangChain4j |
|---|---|---|
| Tool definition | `@Tool(description=…)` + `@ToolParam` | `@Tool` + `@P` |
| Per-call context (user id, tenant) | `ToolContext` | `@ToolMemoryId`, invocation parameters |
| Return directly to user | `@Tool(returnDirect = true)` | — |
| Tool errors | `ToolExecutionExceptionProcessor` | Error handlers on AiServices |
| Approval | Your own wrapper / advisor before execution | Your own wrapper |

A common Spring pattern for approvals: write tools that create **pending actions** (a row in a table with a confirmation token) instead of acting; a separate, human-triggered endpoint executes them.

## Production notes

- **Version tool descriptions** with your prompts (Phase 7) — a description change changes agent behaviour.
- **Rate-limit and make writes idempotent** (idempotency keys), since agents retry.
- **Keep catalogues small** (5–15 tools per agent). Use routing (lesson 5) to pick the right specialised agent and toolset.

## Exercises

1. **Easy** — Add a `maxCallsPerRun` limit per tool and return `ERROR: limit reached` beyond it.
2. **Medium** — Implement the pending-action pattern: `close_ticket` returns a confirmation token and a description; a separate `confirm(token)` method executes it. Test that nothing executes before confirmation.
3. **Hard** — Add JSON-schema-like type validation (`int`, `enum`, regex) to `Tool` parameter definitions and generate the catalogue description from it.

## Common mistakes

- Generic tools that can do anything.
- Running tools with a service account that bypasses user permissions.
- Exceptions that abort the agent instead of informing it.

## Further reading

- OWASP Top 10 for LLM Applications 2025: LLM06 "Excessive Agency".
- Spring AI tool calling: <https://docs.spring.io/spring-ai/reference/api/tools.html>
