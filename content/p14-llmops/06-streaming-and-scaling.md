---
title: Concurrency and Scaling — Virtual Threads, Streaming, Load Testing
summary: Serve many slow, long-lived LLM calls efficiently — virtual threads vs WebFlux, connection pools, back-pressure, and load testing that reflects real token distributions.
minutes: 40
prereqs: [p14-llmops/05-observability]
tags: [llmops, virtual-threads, scaling, performance]
quiz:
  - q: Why are virtual threads a good fit for LLM-calling services?
    options:
      - They make the model faster
      - LLM calls block for seconds on I/O; virtual threads make blocking cheap, so thousands of concurrent calls don't need thousands of platform threads
      - They reduce token usage
      - They replace HTTP
    answer: 1
    why: The bottleneck is waiting, not CPU. Virtual threads let simple blocking code scale.
  - q: What usually limits throughput first when calling a hosted LLM?
    options: [JVM heap, Provider rate limits (requests and tokens per minute) and connection pool size, CPU, Disk]
    answer: 1
    why: Scale tests must model provider limits; adding replicas does not raise your provider quota.
  - q: What should a load test for an LLM endpoint vary?
    options:
      - Only the number of users
      - Prompt and output token lengths drawn from real traffic, concurrency, and streaming vs non-streaming
      - Only the URL
      - Nothing — use one fixed prompt
    answer: 1
    why: Latency and cost depend heavily on token lengths; a single short prompt gives misleading results.
interview:
  - q: Your AI endpoint must handle 2,000 concurrent streaming sessions. What do you check?
    a: |
      End-to-end concurrency limits: provider rate limits and quota (or GPU capacity and KV-cache memory if self-hosted), HTTP client connection pool size per provider, server connection limits and idle timeouts for SSE, reverse proxy buffering and timeouts, and per-instance memory for in-flight responses. Use virtual threads (or WebFlux) so waiting doesn't consume platform threads, apply back-pressure and admission control (queue or reject with retry-after when token budgets are exhausted), shed load gracefully, and autoscale on concurrency or queue depth rather than CPU. Validate with a load test using realistic token distributions and streaming clients, measuring TTFT and inter-token latency percentiles.
    level: architect
---

## Why it matters

LLM calls are slow (seconds), long-lived (streaming) and rate-limited by someone else. Classic thread-per-request sizing and CPU-based autoscaling fail here; the right concurrency model and realistic load tests prevent painful launches.

## Concept

```mermaid
flowchart LR
    U[2,000 users] --> LB[load balancer: SSE-aware timeouts]
    LB --> APP["Spring Boot replicas: virtual threads"]
    APP --> POOL["HTTP client pool per provider"]
    POOL --> RL{"provider limits: RPM / TPM"}
    RL --> P[(LLM provider or GPU pool)]
    APP --> ADM["admission control: token budget, queue, 429 + Retry-After"]
```

| Concern | Guidance |
|---|---|
| Threads | `spring.threads.virtual.enabled=true`; avoid `synchronized` around blocking calls in hot paths (pinning) on older JDKs |
| Reactive | WebFlux end-to-end if the stack is already reactive; Spring MVC can still return `Flux` for SSE |
| Connections | Size the provider HTTP pool for expected concurrency; set keep-alive and idle timeouts |
| Back-pressure | Bounded queues; reject early with `Retry-After` instead of timing out late |
| Autoscaling | On in-flight requests or queue depth, not CPU |
| Self-hosted GPUs | Throughput limited by batch size and KV cache (Phase 5); use continuous batching engines |

## Build it

The course app runs on virtual threads (`spring.threads.virtual.enabled: true` in its `application.yml`), and Phase 10's `Workflows.parallel` fans out model calls on a virtual-thread executor. A streaming endpoint from Phase 6:

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> stream(@RequestParam String q) {
    return assistant.stream(q);          // ChatClient ... .stream().content() with a timeout
}
```

### Load testing realistically

Generate load with Gatling (Java DSL), k6 or JMeter, drawing prompts from a sample of real (anonymised) traffic:

```java
// Gatling Java DSL (reference snippet)
ScenarioBuilder tutor = scenario("tutor")
        .feed(csv("questions.csv").random())
        .exec(http("ask").post("/tutor").formParam("question", "#{question}"));
setUp(tutor.injectOpen(rampUsers(500).during(120))).protocols(http.baseUrl("http://localhost:8080"));
```

Point it at a **fake model server** (Phase 0's `HttpServer` fake with artificial delays and token-proportional latency) to load-test your own service without paying for tokens; then run a smaller test against the real provider to verify limits.

## Production notes

- **Know your provider quotas** (RPM/TPM per model and region) and request increases before launch.
- **Separate pools per feature** (bulkheads) so a heavy batch job can't starve interactive users.
- **Measure TTFT and inter-token latency** for streaming, not just total time.

## Exercises

1. **Easy** — Calculate how many concurrent requests one provider key supports at 200k tokens/minute with 3,000 tokens per request and 8 s average latency.
2. **Medium** — Build a fake LLM HTTP server whose latency is `200 ms + 20 ms × output tokens`, and load-test the course Tutor against it with 200 concurrent users on virtual threads vs a fixed pool of 50 platform threads.
3. **Hard** — Add admission control to the Tutor endpoint: when more than N requests are in flight, return 429 with `Retry-After`; verify with the load test.

## Common mistakes

- CPU-based autoscaling for I/O-bound AI services.
- Load tests with one short prompt.
- Ignoring proxy buffering and timeouts for SSE.

## Further reading

- JEP 444: Virtual Threads.
- Gatling Java DSL documentation.
