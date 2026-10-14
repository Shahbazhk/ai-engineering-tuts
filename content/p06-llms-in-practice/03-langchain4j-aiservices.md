---
title: LangChain4j AiServices — Declarative LLM Interfaces
summary: Describe an AI capability as a Java interface and let LangChain4j implement it — annotations, templates, typed returns — and when to choose it over Spring AI.
minutes: 40
prereqs: [p06-llms-in-practice/02-spring-ai-chatclient]
tags: [langchain4j, aiservices, framework-comparison]
quiz:
  - q: How does AiServices.create(Translator.class, model) produce an implementation?
    options:
      - It generates source code at build time
      - It creates a dynamic proxy that turns each method call into a chat request using the method's annotations and parameters
      - It requires a Spring context
      - It downloads code from the provider
    answer: 1
    why: The same technique as Spring Data repositories and Feign clients — a java.lang.reflect.Proxy interpreting annotations at call time.
  - q: In LangChain4j templates, how is a method parameter referenced?
    options: ["${name}", "{{name}}", "{name}", "#name"]
    answer: 1
    why: LangChain4j uses Mustache-style {{name}} with @V("name") on parameters; a single unannotated parameter can be referenced as {{it}}. Spring AI templates use {name}.
  - q: When is LangChain4j a better fit than Spring AI?
    options:
      - Never
      - In non-Spring runtimes (Quarkus, Micronaut, plain Java, Liferay OSGi modules) or when you prefer its declarative AiServices style and integrations
      - Only for Python projects
      - Only for embedding models
    answer: 1
    why: Both are mature. Spring AI fits Boot applications natively; LangChain4j is framework-agnostic and also has Spring Boot and Quarkus integrations.
interview:
  - q: Spring AI or LangChain4j — how do you decide for a new enterprise project?
    a: |
      Decide by runtime, team and ecosystem rather than features, since both cover chat, embeddings, RAG, tools, memory, structured output and MCP. In a Spring Boot estate, Spring AI gives auto-configuration, Boot-native observability, consistent configuration properties and familiar idioms. For Quarkus, Micronaut, plain Java or OSGi environments such as Liferay modules, LangChain4j is the natural choice and its AiServices proxies are very productive. Whichever you pick, isolate it behind your own small domain interfaces so prompts, evals and business code do not depend on framework types — that keeps a later switch cheap.
    level: architect
---

## Why it matters

LangChain4j is the other major Java LLM framework. Its **AiServices** style — declare an interface, get an implementation — is very productive, and it runs anywhere: plain Java, Quarkus, Micronaut, Spring, or inside Liferay's OSGi container where a full Spring Boot stack is not available.

## Concept

```mermaid
flowchart LR
    CALL["translator.translate(text, 'German')"] --> PROXY["AiServices dynamic proxy"]
    PROXY --> TPL["@SystemMessage / @UserMessage templates + @V params"]
    TPL --> REQ["ChatRequest: messages + parameters"]
    REQ --> CM["ChatModel (Ollama, OpenAI, …)"]
    CM --> RESP["AiMessage"]
    RESP --> MAP["map to return type: String, enum, record, List…"]
    MAP --> CALL
```

| LangChain4j | Spring AI equivalent |
|---|---|
| `ChatModel` | `ChatModel` |
| `AiServices` interface | A service class using `ChatClient` |
| `@SystemMessage`, `@UserMessage`, `@V` | `.system(...)`, `.user(u -> u.text(...).param(...))` |
| Typed return (enum, POJO) | `.call().entity(Type.class)` |
| `@Tool` (dev.langchain4j.agent.tool) | `@Tool` (org.springframework.ai.tool.annotation) |
| `ChatMemory` | `ChatMemory` + `MessageChatMemoryAdvisor` |

## Build it

<!-- code: lessons/p06-llms-in-practice/src/main/java/dev/aieng/p06/LangChainAssistant.java#service -->

Two details:

- Template variables use `{{name}}`, bound by `@V("name")`. A single unannotated parameter is `{{it}}`.
- The **return type drives parsing.** For an enum, LangChain4j instructs the model to answer with one of the constants and maps the reply.

The tests use a tiny LangChain4j `ChatModel` double that records the messages it receives:

```java
static final class RecordingLc4jModel implements ChatModel {
    @Override
    public ChatResponse doChat(ChatRequest request) {
        last = request.messages();
        return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
    }
}
```

and assert that the system message and the rendered user message (`"Translate to German:\nHello world"`) are exactly what the annotations promise.

## Now with a real model

```java
ChatModel model = OllamaChatModel.builder()            // langchain4j-ollama
        .baseUrl("http://localhost:11434")
        .modelName("llama3.2")
        .temperature(0.0)
        .build();
Translator translator = AiServices.create(Translator.class, model);
```

With `langchain4j-spring-boot-starter`, an interface annotated `@AiService` becomes a Spring bean automatically.

## Production notes

- **Isolate framework types.** Your domain code should call `Translator`, not `ChatModel`. That keeps a framework switch local.
- **Enum and record returns still need validation** — the model can answer outside the expected set; decide whether to retry, default or escalate.
- **Liferay:** LangChain4j's core has no Spring dependency, so it can be embedded in an OSGi module (with its dependencies bundled or wrapped) to add AI features inside the portal itself; alternatively call a separate Spring Boot AI service over REST — usually easier to operate and scale.

## Exercises

1. **Easy** — Add a `summarize(String text, int maxWords)` method to a new interface and test the rendered prompt.
2. **Medium** — Add a method that returns a record `Summary(String title, List<String> bullets)` and test parsing with a JSON reply from the fake.
3. **Hard** — Implement the same `FeedbackAnalyzer` with Spring AI's `ChatClient` and compare: lines of code, testability, and how each framework phrases the enum instructions (inspect the recorded prompts).

## Common mistakes

- Mixing `{name}` (Spring AI) and `{{name}}` (LangChain4j) template syntax.
- Letting framework-specific types leak into domain APIs.
- Assuming typed returns cannot fail.

## Further reading

- LangChain4j AI Services: <https://docs.langchain4j.dev/tutorials/ai-services>
- LangChain4j Spring Boot integration: <https://docs.langchain4j.dev/tutorials/spring-boot-integration>
