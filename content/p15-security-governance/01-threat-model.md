---
title: Threat-Modelling AI Systems — the OWASP LLM Top 10
summary: Where AI applications are attacked, mapped onto a Spring architecture, with the control for each risk and where in this course you built it.
minutes: 40
prereqs: [p14-llmops/07-deployment]
tags: [security, owasp, threat-model]
quiz:
  - q: Which is ranked first in the OWASP Top 10 for LLM Applications (2025)?
    options: [Model theft, Prompt injection, Denial of service, Insecure plugins]
    answer: 1
    why: LLM01 Prompt Injection — direct and indirect — remains the top risk.
  - q: What is "excessive agency"?
    options:
      - A model that is too verbose
      - Giving an LLM-based system more functionality, permissions or autonomy than needed, so manipulated outputs cause damage
      - Too many users
      - A slow agent
    answer: 1
    why: Controls are least privilege, narrow tools and human approval (Phases 10–11).
  - q: Why is the LLM itself not a security boundary?
    options:
      - It is too slow
      - Its behaviour can be steered by any text in its context; enforcement must happen in deterministic code around it
      - It cannot read permissions
      - It has no memory
    answer: 1
    why: Put authorisation, validation and limits in code paths the model cannot talk its way around.
interview:
  - q: How do you threat-model a new RAG assistant with tool access?
    a: |
      Draw the data flow: user → app → retrieval (sources, vector store) → prompt assembly → model provider → output handling → tools → downstream systems, plus logs and caches. Mark trust boundaries: user input, retrieved documents and tool outputs are untrusted; the model is untrusted as a decision-maker. For each element apply STRIDE plus the OWASP LLM Top 10: prompt injection (direct/indirect), sensitive information disclosure (cross-user retrieval, logs, caches), supply chain (models, MCP servers, libraries), data/model poisoning (ingested content), improper output handling (XSS, SQL, command injection from model output), excessive agency (tools), system prompt leakage, vector/embedding weaknesses (permission bypass, inversion), misinformation and unbounded consumption. Record mitigations, owners and tests; red-team before launch and after major changes.
    level: architect
---

## Why it matters

AI features introduce a component that follows instructions found *anywhere* in its context. Classic application security still applies — and a new class of attacks targets the model's instruction-following itself. A structured threat model keeps both in view.

## Concept

```mermaid
flowchart LR
    U[user input — untrusted] --> APP[Spring app]
    DOCS[retrieved documents — untrusted] --> APP
    TOOLOUT[tool outputs — untrusted] --> APP
    APP -->|prompt| LLM[model — untrusted decision-maker]
    LLM -->|output — untrusted| APP
    APP -->|validated actions only| SYS[(systems of record)]
    APP --> LOGS[(logs, caches, memory)]
```

### OWASP Top 10 for LLM Applications (2025) → controls in this course

| Risk | Control | Built in |
|---|---|---|
| LLM01 Prompt injection | Delimit data, heuristics, least privilege, approvals | Phase 7 L1, this phase L2, Phase 10 L3 |
| LLM02 Sensitive information disclosure | Permission-aware retrieval, PII redaction, scoped caches | Phase 9 L6, this phase L3, Phase 14 L4 |
| LLM03 Supply chain | Vetted models and MCP servers, safe formats (no pickle), pinned versions | Phase 3 L7, Phase 11 L5 |
| LLM04 Data and model poisoning | Curated ingestion and training data, provenance | Phase 9 L2, Phase 13 L3 |
| LLM05 Improper output handling | Validate/escape output, structured output, output guard | Phase 6 L5, this phase L4 |
| LLM06 Excessive agency | Narrow tools, run as user, approvals, budgets | Phase 10 L3/L6 |
| LLM07 System prompt leakage | No secrets in prompts, canary detection | Phase 7 L1, this phase L4 |
| LLM08 Vector and embedding weaknesses | Filters inside retrieval, tenant isolation | Phase 8 L5 |
| LLM09 Misinformation | Grounding, citations, faithfulness evals, refusals | Phase 9, Phase 12 |
| LLM10 Unbounded consumption | Token budgets, rate limits, max tokens, timeouts | Phase 14 L2/L3 |

## Build it

This phase adds the remaining controls as small, testable components:

| Component | Lesson |
|---|---|
| `InjectionDetector` | 2 — prompt injection |
| `PiiRedactor` | 3 — personal data |
| `OutputGuard` | 4 — output guardrails |
| `AiActTriage` | 5 — governance |
| `AuditLog` | 6 — audit and red-teaming |

## Now with the library

Security for AI services is mostly ordinary Spring Security plus discipline: OAuth2 resource servers, method security on tool beans, `ToolContext` for identity, network policies for egress, secret managers for keys. Managed guardrail services and open-source guard models (classifiers for injection, toxicity, PII) can add layers — evaluate them like any classifier (precision at your base rate, Phase 1 lesson 4).

## Production notes

- **Defence in depth:** no single control stops prompt injection; layer them.
- **Assume breach of the model's judgement:** design so a fully manipulated model can still only do what the user could do anyway.
- **Threat-model again** whenever you add a tool, a data source or an MCP server.

## Exercises

1. **Easy** — For the course Tutor, list its untrusted inputs and where each enters the prompt.
2. **Medium** — Produce a STRIDE + OWASP LLM table for the Liferay RAG assistant from Phase 9 with one mitigation per row.
3. **Hard** — Pick three OWASP risks and write automated tests (with fakes) proving your Phase 9 or Phase 10 code mitigates them.

## Common mistakes

- Treating prompt instructions as security controls.
- Forgetting caches, logs and memory as disclosure paths.
- Rendering model output as HTML without escaping (XSS) — the course app escapes it (`AiService.toHtml`).

## Further reading

- OWASP Top 10 for LLM Applications 2025: <https://genai.owasp.org/llm-top-10/>
- MITRE ATLAS: <https://atlas.mitre.org/>
