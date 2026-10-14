---
title: Your First LLM Call — With Only the JDK
summary: Call a model over raw HTTP, stream tokens, and read the usage numbers that drive cost and latency.
minutes: 45
prereqs: [p00-setup/02-toolchain]
tags: [llm, http, streaming]
quiz:
  - q: In the Ollama chat API, what does `eval_count` report?
    options: [Number of input tokens, Number of generated output tokens, Number of HTTP retries, Number of messages]
    answer: 1
    why: "`prompt_eval_count` is input tokens; `eval_count` is generated tokens. Hosted APIs report the same split (prompt/completion or input/output tokens) and bill them at different prices."
  - q: Why does streaming make a chat UI feel faster even though total generation time is the same?
    options:
      - Streaming uses a faster model
      - The user sees the first token after the time-to-first-token instead of waiting for the whole answer
      - Streaming compresses the response
      - Streaming skips the prompt processing step
    answer: 1
    why: Perceived latency is dominated by time-to-first-token (TTFT). Total time is TTFT + output tokens / tokens-per-second either way.
  - q: Which role carries instructions that should govern the whole conversation?
    options: [user, assistant, system, tool]
    answer: 2
    why: The system message sets behaviour and constraints. User messages carry the request; assistant messages are prior model turns; tool messages carry tool results.
interview:
  - q: Walk me through what happens on the wire when an application calls a chat model.
    a: |
      The app serialises a list of messages (system, user, prior assistant/tool turns) plus options (model, temperature, max tokens, tools) into JSON and POSTs it. The server tokenises the whole conversation, runs a forward pass over the prompt (prefill — cost grows with input length), then generates tokens one at a time (decode), each conditioned on everything before it, until a stop token or the max-tokens limit. The response returns the generated message plus usage (input/output tokens) and a finish reason; in streaming mode the same content arrives as server-sent events or newline-delimited JSON chunks. The model holds no state between calls — "memory" is the client re-sending the history.
    level: senior
---

## Why it matters

Before using any framework, see the bare mechanics. A chat model is **a stateless HTTP endpoint**: you send the whole conversation, it returns the next message. Everything else — memory, RAG, tools, agents — is the client deciding what to put in that request.

## Concept: request, prefill, decode

```mermaid
sequenceDiagram
    participant App as Your Java app
    participant LLM as Model server
    App->>LLM: POST /api/chat {model, messages[], options}
    Note over LLM: Tokenise all messages
    Note over LLM: Prefill: one forward pass over the prompt
    loop Decode, one token at a time
        LLM-->>App: (streaming) next token chunk
    end
    LLM-->>App: final chunk: done, prompt_eval_count, eval_count
```

Two numbers drive cost and latency:

- **Input (prompt) tokens** — everything you send, including the system prompt, history and retrieved documents. Processed in parallel during *prefill*.
- **Output (completion) tokens** — generated one at a time during *decode*. Usually several times more expensive per token on hosted APIs, and the main driver of latency.

A useful latency model: `latency ≈ TTFT + output_tokens / tokens_per_second`.

## Build it from scratch

The client below is the whole idea: serialise, POST, parse. Records give you the JSON shape for free with Jackson.

<!-- code: lessons/p00-setup/src/main/java/dev/aieng/p00/RawOllamaClient.java#blocking -->

Streaming uses the same endpoint with `"stream": true`. Ollama answers with newline-delimited JSON; hosted APIs typically use Server-Sent Events. Either way you read chunks as they arrive:

<!-- code: lessons/p00-setup/src/main/java/dev/aieng/p00/RawOllamaClient.java#streaming -->

### Testing without a model

The lesson's test starts a fake Ollama with the JDK's built-in `com.sun.net.httpserver.HttpServer`, so it runs in milliseconds with no network:

<!-- code: lessons/p00-setup/src/test/java/dev/aieng/p00/RawOllamaClientTest.java -->

This pattern — **fake the model at the HTTP boundary** — is how you keep AI code testable. You will use it again with Spring's `MockRestServiceServer` and with stub `ChatModel` beans.

## Now with the library

The same call with Spring AI (Phase 6 goes deep):

```java
String answer = ChatClient.create(chatModel)
        .prompt()
        .system("You are a concise assistant for senior Java engineers.")
        .user("In two sentences: what does an embedding model return?")
        .call()
        .content();
```

What Spring AI adds on top of your raw client: provider portability, retries, observation (Micrometer metrics and traces), advisors (interceptors for memory and RAG), structured output conversion and tool calling.

## Production notes

- **Set timeouts.** A local model on a cold start can take 30+ seconds to load. Use a short connect timeout and a generous but bounded request timeout.
- **Log usage, not content.** Token counts belong in metrics. Prompt content may contain personal data; log it only where policy allows.
- **Stream to users, block for machines.** Stream when a person is waiting; use blocking calls for batch jobs and structured output you must parse whole.

## Exercises

1. **Easy** — Run `RawOllamaClient.main` against your local model. Change `temperature` to 0 and run it twice. Are the answers identical?
2. **Medium** — Add a `maxTokens` option (`num_predict` in Ollama) and observe the `done_reason` field when the limit is hit.
3. **Hard** — Measure TTFT and tokens/second in `stream()` using `System.nanoTime()` and print them. Compare a 1B and a 3B model.

## Common mistakes

- Expecting the model to remember the previous call. It does not; you must send the history.
- Parsing streamed chunks as one JSON document. Each line is its own JSON object.
- Treating `temperature: 0` as fully deterministic. It is much more stable, but batching and floating-point effects on GPUs can still change outputs.

## Further reading

- Ollama API reference: <https://github.com/ollama/ollama/blob/main/docs/api.md>
- OpenAI-compatible Chat Completions format (used by many providers): <https://platform.openai.com/docs/api-reference/chat>
