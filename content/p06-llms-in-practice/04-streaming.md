---
title: Streaming Responses — Flux, SSE and Virtual Threads
summary: Stream tokens from the model to the browser with Spring AI, choose between WebFlux and MVC with virtual threads, and handle timeouts and cancellation.
minutes: 40
prereqs: [p06-llms-in-practice/03-langchain4j-aiservices]
tags: [spring-ai, streaming, sse, webflux, virtual-threads]
quiz:
  - q: What does chatClient.prompt().user(q).stream().content() return?
    options: ["A String", "A Flux<String> of text chunks", "A CompletableFuture<String>", "An InputStream"]
    answer: 1
    why: A Reactor Flux that emits chunks as the model generates them.
  - q: How can a Spring MVC controller stream a Flux<String> to a browser?
    options:
      - It cannot; WebFlux is required
      - Return the Flux with produces = MediaType.TEXT_EVENT_STREAM_VALUE; Spring MVC writes Server-Sent Events asynchronously
      - Convert it to a List first
      - Use a JSP
    answer: 1
    why: Spring MVC supports reactive return types for streaming. WebFlux is not required, although it avoids holding a thread per stream.
  - q: Why add a timeout to a streaming call?
    options:
      - To speed up the model
      - So a stalled model or network connection does not hold resources forever
      - Streams cannot complete otherwise
      - It reduces token cost
    answer: 1
    why: Long-lived connections need explicit upper bounds; also cancel the upstream call when the client disconnects.
interview:
  - q: Should an LLM gateway service in Spring use WebFlux or MVC with virtual threads?
    a: |
      Both work. LLM calls are slow, I/O-bound and long-lived, so the concern is not holding a platform thread per in-flight request. WebFlux solves that with non-blocking I/O end to end and streams naturally via Flux, but brings reactive complexity and requires non-blocking libraries all the way down (database, HTTP clients). Spring MVC on Java 21 with virtual threads (spring.threads.virtual.enabled=true) lets blocking code scale to many concurrent calls with a familiar programming model, and MVC can still return Flux for SSE streaming. For a team of Spring MVC experts with blocking dependencies, MVC + virtual threads is usually the pragmatic default; choose WebFlux when the whole stack is already reactive or extreme connection counts are expected.
    level: architect
---

## Why it matters

A 400-token answer at 30 tokens/second takes 13 seconds. Streamed, the user sees the first words after the time-to-first-token — often under a second. Streaming is the single biggest perceived-latency improvement for chat UIs.

## Concept

```mermaid
sequenceDiagram
    participant B as Browser (EventSource / fetch)
    participant S as Spring controller
    participant C as ChatClient
    participant M as Model
    B->>S: GET /api/chat/stream?q=...
    S->>C: prompt().stream().content()
    C->>M: streaming request
    loop each chunk
        M-->>C: token(s)
        C-->>S: Flux element
        S-->>B: SSE "data: ..." event
    end
    M-->>C: done
    S-->>B: stream complete
```

- **Server-Sent Events (SSE):** one-way, text-based, auto-reconnecting HTTP stream — perfect for token streaming.
- **Back-pressure and cancellation:** if the browser disconnects, the subscription is cancelled and the upstream model call should stop (saving tokens).

## Build it

<!-- code: lessons/p06-llms-in-practice/src/main/java/dev/aieng/p06/StreamingAssistant.java#stream -->

The test drives it with `StepVerifier` (reactor-test) against a fake that emits three chunks:

```java
StepVerifier.create(new StreamingAssistant(model).stream("hi"))
        .expectNext("Spring ", "AI ", "streams.")
        .verifyComplete();
```

## Now in a controller

```java
@RestController
class ChatStreamController {
    private final StreamingAssistant assistant;
    ChatStreamController(StreamingAssistant assistant) { this.assistant = assistant; }

    // Works in Spring MVC (virtual threads recommended) and in WebFlux.
    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> stream(@RequestParam String q) {
        return assistant.stream(q);
    }
}
```

```javascript
// Browser
const es = new EventSource('/api/chat/stream?q=' + encodeURIComponent(q));
es.onmessage = e => output.textContent += e.data;
```

Enable virtual threads in Spring Boot with `spring.threads.virtual.enabled=true` — the course app does.

## Production notes

- **Timeouts at every layer:** model call, Flux, reverse proxy (nginx/ingress buffering must be disabled for SSE: `X-Accel-Buffering: no`).
- **Validate after streaming completes.** You cannot validate JSON or run output guardrails on half an answer; for structured output, don't stream — or stream to the user and validate the aggregated text before acting on it.
- **Token usage arrives at the end** (or not at all for some providers in streaming mode). Aggregate it for metering.
- **Accessibility:** announce streamed content politely (`aria-live="polite"`), and don't move focus on every chunk.

## Exercises

1. **Easy** — Add `.doOnComplete(...)` logging to `stream` and print the total characters received.
2. **Medium** — Return `Flux<ServerSentEvent<String>>` with an event id per chunk and a final `event: done` message.
3. **Hard** — Implement cancellation propagation: when the client disconnects, assert in a test (with a fake model emitting slowly via `Flux.interval`) that the upstream subscription is cancelled.

## Common mistakes

- Buffering proxies that hold the whole response, making streaming pointless.
- Streaming structured output and parsing partial JSON.
- Forgetting that each open stream is an open connection — set limits.

## Further reading

- Spring AI streaming: <https://docs.spring.io/spring-ai/reference/api/chatclient.html#_streaming_responses>
- Spring Framework, "Server-Sent Events" in Spring MVC: <https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html>
- JEP 444: Virtual Threads.
