---
title: RAG with Spring AI's Modular Components
summary: RetrievalAugmentationAdvisor, VectorStoreDocumentRetriever, document post-processors and ContextualQueryAugmenter — the same pipeline as composable Spring beans.
minutes: 50
prereqs: [p09-rag/03-rag-from-scratch]
tags: [rag, spring-ai, advisors]
quiz:
  - q: Where does Spring AI's RAG pipeline plug into a ChatClient call?
    options:
      - As a separate REST service
      - As an advisor (RetrievalAugmentationAdvisor) that retrieves documents and rewrites the user message before the model call
      - Inside the vector store
      - In the tokenizer
    answer: 1
    why: Like memory and tools, RAG is an interceptor around the model call.
  - q: What does a DocumentPostProcessor do?
    options:
      - Embeds documents
      - Receives the query and retrieved documents and returns a modified list — for reranking, deduplication, compression or permission checks
      - Writes documents to the store
      - Splits documents into chunks
    answer: 1
    why: It is the post-retrieval stage of the modular RAG pipeline.
  - q: With ContextualQueryAugmenter.allowEmptyContext(false) and no documents retrieved, what happens?
    options:
      - The question is sent unchanged
      - The prompt is replaced with an instruction telling the model it cannot answer from its knowledge base
      - An exception is thrown
      - Random documents are added
    answer: 1
    why: The test springRagWithNoRelevantContextTellsTheModelNotToAnswer checks this behaviour.
interview:
  - q: How do you enforce per-user permissions in a Spring AI RAG pipeline?
    a: |
      In two layers. First, pre-filter in the retriever: store permission metadata (tenant, site/group ids, roles, visibility) on every chunk at ingestion and build the VectorStoreDocumentRetriever's filterExpression per request from the authenticated user's attributes — a Supplier-based filter expression lets it be computed per call. Second, for systems with complex or fast-changing permissions (like Liferay), add a DocumentPostProcessor that re-checks the final candidates against the source system with the user's identity and drops anything not readable. Never rely on the model or the prompt to hide content, and never let retrieved content from one user's scope be cached and served to another.
    level: architect
---

## Why it matters

Spring AI turns each RAG stage into a replaceable component. You can start with a few lines and later swap in query rewriting, multi-query expansion, rerankers or permission filters without rewriting the pipeline.

## Concept

```mermaid
flowchart LR
    U[user message] --> QT["QueryTransformer(s): rewrite, compress, translate"]
    QT --> QE["QueryExpander: multi-query (optional)"]
    QE --> DR["DocumentRetriever: VectorStoreDocumentRetriever (topK, threshold, filter)"]
    DR --> DJ["DocumentJoiner: merge results of expanded queries"]
    DJ --> PP["DocumentPostProcessor(s): permissions, rerank, dedupe"]
    PP --> QA["QueryAugmenter: ContextualQueryAugmenter writes context into the prompt"]
    QA --> M[model]
```

All of these are bundled in one `RetrievalAugmentationAdvisor`, added to a `ChatClient` like any other advisor.

## Build it

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/SpringRag.java#advisor -->

The tests run it against `SimpleVectorStore`, a keyword fake embedding model and a capturing fake chat model:

- `springRagAdvisorInjectsOnlyPermittedSameSiteContext` indexes the VPN guide for `site-a` and a conflicting document (`port 9999`) for `site-b`. The prompt sent to the model contains `8443`, **not** `9999` (filter expression), and not the Mac chunk (removed by a post-processor).
- `springRagWithNoRelevantContextTellsTheModelNotToAnswer` asks about the canteen: no document passes the threshold, so the augmenter replaces the prompt with an instruction to decline.

## Making filters per-request

The filter above is fixed per `SpringRag` instance. In a web application, compute it from the current user on every call:

```java
VectorStoreDocumentRetriever.builder()
        .vectorStore(store)
        .filterExpression(() -> {
            var user = SecurityContextHolder.getContext().getAuthentication();
            var f = new FilterExpressionBuilder();
            return f.and(f.eq("tenant", tenantOf(user)), f.in("group", groupsOf(user).toArray())).build();
        })
        .build();
```

(The `Supplier` variant runs at query time. If retrieval runs on another thread — the advisor can use a `TaskExecutor` — make sure the security context propagates, or pass the user explicitly through the advisor context.)

## Now with more components

```java
RetrievalAugmentationAdvisor.builder()
        .queryTransformers(RewriteQueryTransformer.builder().chatClientBuilder(cheapClientBuilder).build())
        .queryExpander(MultiQueryExpander.builder().chatClientBuilder(cheapClientBuilder).numberOfQueries(3).build())
        .documentRetriever(retriever)
        .documentPostProcessors(permissionFilter, reranker)
        .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(false).build())
        .build();
```

Use a **cheaper model** for query transformation and expansion; they are simple tasks.

## Production notes

- **Return the retrieved documents with the answer.** `ChatClientResponse.context()` carries the retrieved documents (key `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`) so you can show sources and log ids.
- **Order post-processors deliberately:** permissions first (never rerank what the user cannot see), then rerank, then dedupe/trim.
- **Watch latency:** every transformer/expander adds a model call before retrieval.

## Exercises

1. **Easy** — Raise `similarityThreshold` to 0.9 in `SpringRag` and observe the effect on the first test.
2. **Medium** — Implement a `DocumentPostProcessor` that keeps at most two chunks per `sourceId` (diversity across documents) and test it.
3. **Hard** — Read the retrieved documents from `chatClientResponse().context()` and return an `AnswerWithSources` record from `SpringRag.ask`, including titles and URLs from metadata.

## Common mistakes

- Static filters in multi-user applications.
- Reranking before permission filtering.
- Using the expensive answer model for query rewriting.

## Further reading

- Spring AI Retrieval Augmented Generation: <https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html>
