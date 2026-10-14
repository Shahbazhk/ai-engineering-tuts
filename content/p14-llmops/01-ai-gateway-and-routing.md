---
title: AI Gateways and Model Routing
summary: Centralise model access — routing by difficulty, fallback chains, per-route policies — and decide between an in-app router and a shared gateway.
minutes: 45
prereqs: [p13-fine-tuning/06-serving-and-distillation]
tags: [llmops, routing, gateway, architecture]
quiz:
  - q: What is the main purpose of a model fallback chain?
    options:
      - Better answers from every model
      - Availability — when the preferred model fails or its circuit is open, another model serves the request
      - Lower token counts
      - Prompt caching
    answer: 1
    why: Providers have outages and rate limits; a chain degrades gracefully instead of failing.
  - q: Why route easy requests to a smaller model?
    options:
      - Small models are always better
      - Most traffic is easy; serving it cheaply and quickly while reserving the strong model for hard cases cuts cost and latency
      - To test the small model
      - Large models cannot answer easy questions
    answer: 1
    why: Routing is often the single biggest cost lever — validated with evals per route.
  - q: Which concern belongs in a shared AI gateway rather than in each application?
    options:
      - Business-specific prompts
      - Provider credentials, quotas, central logging, PII redaction policy and cost attribution across teams
      - UI layout
      - Domain validation rules
    answer: 1
    why: Cross-cutting, organisation-wide concerns centralise well; product logic stays in the application.
interview:
  - q: Should an enterprise build a central AI gateway?
    a: |
      Usually yes once several teams consume models. A gateway (self-built on Spring Cloud Gateway, or a product such as LiteLLM, Kong AI Gateway, Apigee or a cloud provider's offering) centralises provider credentials, routing to approved models, fallbacks, quotas and budgets per team, request/response logging policy, PII redaction, caching and cost attribution, and exposes one API (often OpenAI-compatible) so Spring AI applications switch providers via configuration. Applications keep product-specific logic: prompts, retrieval, tools and validation. Risks: a single point of failure (run it highly available), added latency (keep it thin), and a bottleneck for innovation if governance is too heavy — make the approved path the easy path.
    level: architect
---

## Why it matters

Once AI features go beyond a prototype, every team needs the same things: credentials, routing, fallbacks, quotas, logging and cost reports. Doing it per application duplicates effort and loses control; a gateway or a shared router library centralises it.

## Concept

```mermaid
flowchart LR
    subgraph Apps
        A1[Tutor service]
        A2[Triage service]
        A3[Agent service]
    end
    A1 & A2 & A3 --> GW["AI gateway / router: auth, routing, fallbacks, quotas, redaction, logging, cost attribution"]
    GW -->|easy| SMALL[small / self-hosted model]
    GW -->|hard| LARGE[frontier hosted model]
    GW -->|fallback| BACKUP[second provider]
```

**Routing strategies:**

| Strategy | Example |
|---|---|
| Static per feature | Triage → fine-tuned 1B; Tutor → mid-size model |
| Heuristic | Long or "compare/analyse" prompts → strong model |
| Classifier | A small model or embedding classifier predicts difficulty |
| Cascade | Try the cheap model; escalate on low confidence or failed validation |
| Cost/latency aware | Choose by current price, queue depth or p95 latency |

## Build it from scratch

<!-- code: lessons/p14-llmops/src/main/java/dev/aieng/p14/ModelRouter.java#route -->

Each model carries its own circuit breaker (next lesson), and every attempt is recorded. The test routes "Reset my password" to the small model, and "Compare the two contracts step by step" to the hard route, where the primary model is down — the router falls back to the backup and reports `[large:overloaded, backup:ok]`.

## Now with the library

In Spring AI, a router is a service holding several `ChatClient`s:

```java
@Configuration
class Models {
    @Bean @Qualifier("small") ChatClient small(OllamaChatModel m) { return ChatClient.create(m); }
    @Bean @Qualifier("large") ChatClient large(OpenAiChatModel m) { return ChatClient.create(m); }
}
```

(With several providers on the classpath, set `spring.ai.model.chat` to the primary and create the others' `ChatModel` beans explicitly, or point both at an OpenAI-compatible gateway with different model names.) For a shared gateway, Spring Cloud Gateway with custom filters, or a dedicated product exposing an OpenAI-compatible API, lets every Spring AI application use `spring.ai.openai.base-url` to reach it.

## Production notes

- **Evaluate per route.** A router that sends 80% of traffic to a small model must prove that 80% meets the quality bar.
- **Log the routing decision** (route, model, attempts) with every request.
- **Fallback models need their own evals** — a fallback that answers badly is an outage in disguise.

## Exercises

1. **Easy** — Add a route for prompts containing code blocks (```` ``` ````) that prefers a code-specialised model.
2. **Medium** — Implement a cascade: call the small model with structured output; escalate to the large model when validation fails. Test with fakes.
3. **Hard** — Replace `looksHard` with an embedding-based classifier trained on 100 labelled prompts (logistic regression from Phase 2 on embeddings) and measure routing accuracy.

## Common mistakes

- Routing without per-route evaluation.
- Fallback to a model with very different behaviour and no warning.
- A gateway that becomes a single point of failure.

## Further reading

- Ong et al., "RouteLLM: Learning to Route LLMs with Preference Data", 2024.
- LiteLLM, Kong AI Gateway documentation.
