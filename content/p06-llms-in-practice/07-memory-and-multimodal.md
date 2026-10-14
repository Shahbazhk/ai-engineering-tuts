---
title: Conversation Memory and Multimodal Input
summary: Give a stateless model a memory with a chat-memory advisor, bound it with a window, persist it safely — and send images alongside text.
minutes: 40
prereqs: [p06-llms-in-practice/06-tool-calling]
tags: [spring-ai, memory, multimodal, conversation]
quiz:
  - q: Where does conversation "memory" actually live?
    options:
      - Inside the model weights
      - In your application — the memory advisor stores messages per conversation id and re-sends recent history with each prompt
      - In the provider's account settings
      - In the browser only
    answer: 1
    why: Models are stateless; memory is a context-assembly strategy on your side.
  - q: Why use a message window (e.g. last 20 messages) rather than the full history?
    options:
      - Models refuse long histories
      - Cost and latency grow with every re-sent token, and very long contexts degrade answer quality
      - It is required by Spring AI
      - Old messages are always wrong
    answer: 1
    why: Every turn re-sends the history. Windows, summarisation or retrieval of relevant past turns keep it bounded.
  - q: What must the conversation id be tied to in a multi-user application?
    options:
      - The model name
      - The authenticated user (and session), so one user can never load another user's history
      - A random number per request
      - The server hostname
    answer: 1
    why: Conversation stores contain personal data; isolation and access control are mandatory.
interview:
  - q: Design chat memory for an enterprise assistant used by 50,000 employees.
    a: |
      Keep memory in your own store, keyed by conversation id bound to the authenticated user and tenant. Short-term memory: a bounded window of recent messages (or a token budget) in a JDBC/Redis-backed ChatMemoryRepository, re-sent each turn. Long-term memory: summarise older turns and store facts or summaries as embeddings for retrieval when relevant, rather than re-sending everything. Apply retention policies and deletion on request (GDPR), encrypt at rest, exclude secrets and sensitive fields, and never mix histories across users or tenants. Expose "start new conversation" in the UI, and monitor the token cost contributed by history.
    level: architect
---

## Why it matters

Users expect follow-up questions to work ("and what about the second one?"). Because models are stateless (Phase 0), *you* decide what history to send — which drives cost, latency, quality and privacy.

## Concept

```mermaid
sequenceDiagram
    participant U as User
    participant A as ChatClient + MessageChatMemoryAdvisor
    participant S as ChatMemory (window of 20)
    participant M as Model
    U->>A: "What is my name?" (conversation c1)
    A->>S: load history(c1)
    S-->>A: [user: My name is Sam., assistant: Nice to meet you, Sam.]
    A->>M: history + new message
    M-->>A: "Your name is Sam."
    A->>S: append user + assistant messages
    A-->>U: "Your name is Sam."
```

Strategies, from simplest to most capable:

| Strategy | Idea | Trade-off |
|---|---|---|
| Message window | Last N messages | Simple; forgets older facts abruptly |
| Token budget | As many recent messages as fit in K tokens | Predictable cost |
| Summary memory | Summarise older turns into one message | Extra model call; may lose detail |
| Retrieval memory | Embed past turns, retrieve relevant ones | Best recall for long relationships; more infrastructure |

## Build it

<!-- code: lessons/p06-llms-in-practice/src/main/java/dev/aieng/p06/ConversationService.java#memory -->

The test sends two messages in conversation `c1` and asserts that the second prompt contains the first exchange; then a message in conversation `c2` is sent with **no** history — the isolation property you must guarantee.

## Persisting memory

`MessageWindowChatMemory` stores messages in a `ChatMemoryRepository`. The default is in-memory; Spring AI provides JDBC, Cassandra, Neo4j and other repositories:

```java
// spring-ai-starter-model-chat-memory-repository-jdbc auto-configures a JdbcChatMemoryRepository
@Bean
ChatMemory chatMemory(ChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(20).build();
}
```

## Multimodal input

Vision-capable models (e.g. `llama3.2-vision`, `qwen2.5vl` locally, most frontier hosted models) accept images in the user message:

```java
String description = chatClient.prompt()
        .user(u -> u.text("List the error messages visible in this screenshot.")
                    .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/screenshot.png")))
        .call()
        .content();
```

Use cases in enterprise portals: describing screenshots in support tickets, extracting data from scanned forms, generating alt text for Liferay Documents and Media images (with human review).

## Production notes

- **Bind conversation ids to identity.** Derive them server-side from the authenticated user; never accept arbitrary ids from the client without an ownership check.
- **Retention and deletion.** Chat histories are personal data: define retention, support deletion requests, and avoid storing secrets.
- **Watch history cost.** Log how many tokens each turn spends on history; switch to summary or retrieval memory when it dominates.
- **Images are expensive** in tokens and latency; downscale before sending.

## Exercises

1. **Easy** — Lower `maxMessages` to 2 and show in a test that the first message is forgotten after two exchanges.
2. **Medium** — Implement a summary-memory advisor: when history exceeds 10 messages, replace the oldest 8 with one system message summarising them (use the fake model to produce the summary in tests).
3. **Hard** — With a local vision model, write a service that generates alt text for images and returns it with a confidence flag for human review. Add a validation rule rejecting alt text longer than 125 characters.

## Common mistakes

- Using a static conversation id (every user sees everyone's history).
- Unbounded histories.
- Assuming the model "remembers" between separate API calls.

## Further reading

- Spring AI chat memory: <https://docs.spring.io/spring-ai/reference/api/chat-memory.html>
- Spring AI multimodality: <https://docs.spring.io/spring-ai/reference/api/multimodality.html>
