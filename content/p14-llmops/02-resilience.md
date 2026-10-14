---
title: Resilience — Timeouts, Retries with Jitter, Circuit Breakers
summary: Treat model providers as unreliable remote dependencies — retry only what can succeed, back off with jitter, fail fast with circuit breakers, and define fallbacks.
minutes: 45
prereqs: [p14-llmops/01-ai-gateway-and-routing]
tags: [llmops, resilience, retries, circuit-breaker]
quiz:
  - q: Which provider errors should be retried?
    options:
      - All of them
      - 429 rate limits, 5xx server errors and timeouts — not 400 bad requests or 401/403 auth errors
      - Only 400
      - None
    answer: 1
    why: Retrying a malformed request or a bad key wastes time and quota; the test asserts a 400 is called exactly once.
  - q: Why add random jitter to exponential backoff?
    options:
      - To make tests harder
      - So many clients that failed at the same moment don't retry in lock-step and overload the recovering provider
      - It reduces token cost
      - Providers require it
    answer: 1
    why: Synchronised retries create a thundering herd; jitter spreads them out.
  - q: What does an OPEN circuit breaker do?
    options:
      - Retries faster
      - Fails calls immediately without contacting the provider until a cool-down passes, then allows a trial call
      - Switches models automatically
      - Logs only
    answer: 1
    why: Failing fast protects threads, latency budgets and the struggling provider.
interview:
  - q: A provider has a 30-minute outage. Describe how your Spring service behaves.
    a: |
      Requests time out quickly (connect and read timeouts sized to the use case, not the default minutes); retryable failures are retried once or twice with jittered backoff; after a threshold the circuit breaker for that provider opens and calls fail fast; the router falls back to a secondary model or provider (evaluated in advance) for routes that allow it, and to a graceful degraded experience otherwise (show retrieved sources without a generated answer, queue the request, or a clear message). Streaming endpoints close cleanly. Alerts fire on breaker state and error rate; dashboards show fallback share. When the provider recovers, half-open trial calls close the circuit and traffic returns gradually. Afterwards, review the incident and tune thresholds.
    level: architect
---

## Why it matters

LLM APIs have higher latency variance, stricter rate limits and more frequent incidents than most dependencies you know. Applying classic resilience patterns correctly is the difference between a degraded feature and a site outage.

## Concept

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> CLOSED: success
    CLOSED --> OPEN: failures ≥ threshold
    OPEN --> OPEN: calls fail fast
    OPEN --> HALF_OPEN: cool-down elapsed
    HALF_OPEN --> CLOSED: trial call succeeds
    HALF_OPEN --> OPEN: trial call fails
```

| Pattern | Purpose | LLM-specific notes |
|---|---|---|
| Timeouts | Bound waiting | Separate connect / time-to-first-token / total; streaming needs idle timeouts |
| Retry + jittered backoff | Ride out transient errors | Retry 429/5xx/timeouts only; respect `Retry-After` |
| Circuit breaker | Fail fast during outages | One per provider/model |
| Fallback | Keep serving | Another model, cached answer, sources-only, or queue |
| Bulkhead | Isolate | Separate pools/limits per feature so one feature can't starve others |

**Full-jitter backoff:** wait a random duration in $[0, \min(\text{cap}, \text{base} \cdot 2^{attempt})]$.

## Build it from scratch

<!-- code: lessons/p14-llmops/src/main/java/dev/aieng/p14/Resilience.java#retry -->

<!-- code: lessons/p14-llmops/src/main/java/dev/aieng/p14/Resilience.java#breaker -->

The tests use an injected `Sleeper` and a movable `Clock`, so they verify timing logic without sleeping: two jittered waits within their ceilings before success; a 400 never retried; the breaker opening after three failures, failing fast without calling the provider, and closing again after the cool-down.

## Now with the library

- **Spring AI** has built-in retry for provider calls (`spring.ai.retry.*` properties: max attempts, backoff, which status codes to retry).
- **Resilience4j** provides circuit breakers, rate limiters, bulkheads and time limiters with Spring Boot integration and Micrometer metrics:

```java
@CircuitBreaker(name = "openai", fallbackMethod = "fallback")
public String answer(String q) { return largeClient.prompt().user(q).call().content(); }

String fallback(String q, Throwable t) { return smallClient.prompt().user(q).call().content(); }
```

- **HTTP client timeouts:** configure the `RestClient`/`WebClient` used by the provider (Spring AI exposes customisation hooks) — the defaults are often far too long for interactive use.

## Production notes

- **Don't stack retries:** Spring AI retry + Resilience4j retry + gateway retry can multiply into dozens of attempts.
- **Budget the latency:** retries must fit the user-facing timeout.
- **Alert on breaker state changes** and fallback rates, not just errors.

## Exercises

1. **Easy** — Honour a `Retry-After` header: extend `ModelException` with an optional duration and use it instead of the computed backoff.
2. **Medium** — Configure Spring AI's retry properties for a service and explain each value.
3. **Hard** — Wrap a `ChatClient` call with Resilience4j `CircuitBreaker` and `TimeLimiter`, test the fallback with a fake model that sleeps, and expose the breaker metrics through Actuator.

## Common mistakes

- Retrying non-retryable errors.
- Default multi-minute timeouts on interactive endpoints.
- No fallback, so a provider outage becomes your outage.

## Further reading

- AWS Architecture Blog, "Exponential Backoff and Jitter" (2015).
- Resilience4j documentation; Spring AI retry properties.
