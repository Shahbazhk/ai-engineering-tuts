---
title: Embedding Models in Production
summary: Choosing, hosting, versioning and costing embedding models — hosted APIs, Ollama sidecars and in-process ONNX — behind one Spring AI interface.
minutes: 40
prereqs: [p04-nlp-embeddings/04-sentence-embeddings]
tags: [embeddings, production, architecture, cost]
quiz:
  - q: You switch from embedding model A to model B. What must happen to the vector index?
    options:
      - Nothing
      - Every stored vector must be re-embedded with model B — vectors from different models are not comparable
      - Only new documents use model B
      - Normalise the old vectors again
    answer: 1
    why: Each model defines its own vector space. Mixing them returns garbage rankings without any error.
  - q: Roughly how much storage do 10 million 1,024-dimension float32 embeddings take?
    options: ["4 GB", "41 GB", "410 GB", "4 TB"]
    answer: 1
    why: 10,000,000 × 1,024 × 4 bytes ≈ 41 GB, before index overhead. Int8 quantisation or Matryoshka truncation can cut this by 4× or more.
  - q: What is a sensible cache key for computed embeddings?
    options:
      - The document title
      - A hash of the exact embedded text plus the model id and version
      - The current date
      - The vector itself
    answer: 1
    why: Any change in text or model must miss the cache; unchanged content should never be re-embedded.
interview:
  - q: Design the embedding pipeline for 5 million Liferay documents that change daily.
    a: |
      Ingest change events (Liferay model listeners or audit/search events into Kafka) rather than full re-crawls. For each changed document: extract text, chunk by tokens, compute a content hash per chunk, and only embed chunks whose (hash, model-version) is new — a cache table keyed by hash avoids recomputation. Batch embedding requests and rate-limit them; use a local model (ONNX/TEI/Ollama on GPU nodes) if data residency or cost requires. Write vectors with metadata (document id, chunk id, permissions, locale, model version) to the vector store with upserts and delete chunks of removed or unpublished documents. Plan model upgrades as blue/green: build a new index in parallel, evaluate recall on a labelled set, switch the alias, then delete the old index.
    level: architect
---

## Why it matters

Embeddings are infrastructure. Once millions of vectors depend on one model, changing it is a migration; running it badly costs money and latency on every query. This lesson turns the previous four into deployment decisions.

## Concept: three deployment shapes

```mermaid
flowchart LR
    subgraph App["Spring Boot service"]
        C["code depends on EmbeddingModel interface"]
    end
    C -->|HTTPS| H["Hosted API (OpenAI, Voyage, Cohere, Azure, Bedrock…)"]
    C -->|localhost HTTP| S["Sidecar: Ollama / Text Embeddings Inference"]
    C -->|in-process JNI| O["ONNX Runtime / DJL in the JVM"]
```

| Shape | Latency | Ops effort | Data leaves your network? | Best for |
|---|---|---|---|---|
| Hosted API | 50–300 ms + network | Lowest | Yes | Prototypes, non-sensitive data, top quality |
| Sidecar (Ollama, TEI) | 5–50 ms | Medium | No | Shared GPU, polyglot clients |
| In-process ONNX | 2–20 ms (CPU, small models) | Medium | No | Low latency, simple deployment, small models |

Because Spring AI abstracts all three behind `EmbeddingModel`, the choice is a configuration and bean decision — not a code rewrite.

## Choosing a model

| Criterion | Question |
|---|---|
| Retrieval quality | MTEB retrieval score in your language(s) — then **your own eval set** |
| Max input tokens | Does your chunk size fit (512? 8,192?) |
| Dimensions | Storage = vectors × dims × bytes; Matryoshka support? |
| Licence | Commercial use allowed? |
| Data residency | Can text leave your network? |
| Cost | Price per million tokens × corpus size × re-embedding frequency |

## Build it: the cost and storage calculator

Back-of-envelope numbers you should be able to produce in a meeting:

```java
long docs = 5_000_000, chunksPerDoc = 6, tokensPerChunk = 400, dims = 1024;
long chunks = docs * chunksPerDoc;                       // 30 M chunks
double storageGb = chunks * dims * 4 / 1e9;              // ≈ 123 GB float32 (≈ 31 GB as int8)
double tokens = chunks * (double) tokensPerChunk;        // 12 B tokens for a full embed
double costUsd = tokens / 1e6 * 0.02;                    // ≈ $240 at $0.02 per M tokens (hosted, illustrative price)
```

Embedding the corpus once is usually cheap; **re-embedding frequently and storing many dimensions** is what gets expensive.

## Now with the library

Switching providers in Spring Boot:

```yaml
# Local (default in this course)
spring.ai.model.embedding: ollama
spring.ai.ollama.embedding.model: nomic-embed-text

# Hosted OpenAI-compatible
spring.ai.model.embedding: openai
spring.ai.openai.embedding.options.model: text-embedding-3-small
spring.ai.openai.embedding.options.dimensions: 512      # Matryoshka truncation server-side
```

For in-process ONNX, Spring AI's `spring-ai-transformers` module provides `TransformersEmbeddingModel`, configured with a model and tokenizer URI.

## Production notes

- **Version everything.** Store `embedding_model` and `embedding_version` on every vector row; refuse queries that would mix versions.
- **Blue/green re-indexing.** Build the new index alongside the old, evaluate recall on a labelled set, switch an alias, then delete.
- **Batch, rate-limit and retry** embedding calls in ingestion jobs (Spring Batch or a Kafka consumer with back-pressure).
- **Monitor drift.** If the average query–top-hit similarity drops over weeks, the content or the users' language changed.

## Exercises

1. **Easy** — Compute storage for your largest content repository at 384, 768 and 1,536 dimensions, float32 and int8.
2. **Medium** — Write a `CachingEmbeddingModel` decorator implementing Spring AI's `EmbeddingModel` that caches by SHA-256 of text + model id in a `ConcurrentHashMap`, with a unit test proving the delegate is called once per unique text.
3. **Hard** — Design (diagram + one page) a blue/green re-embedding process for a 30-million-chunk index with zero downtime, including how you validate the new index before switching.

## Common mistakes

- Hard-coding one provider's client into business code instead of depending on `EmbeddingModel`.
- Re-embedding everything on every deployment.
- Silent truncation of long chunks by the model's token limit.

## Further reading

- Spring AI embedding models overview: <https://docs.spring.io/spring-ai/reference/api/embeddings.html>
- Hugging Face Text Embeddings Inference: <https://github.com/huggingface/text-embeddings-inference>
