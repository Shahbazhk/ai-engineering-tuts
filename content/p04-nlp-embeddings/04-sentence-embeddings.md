---
title: Sentence Embeddings and Semantic Search
summary: From token vectors to one vector per text — pooling, contrastive training, Matryoshka truncation, and semantic search with Spring AI's EmbeddingModel.
minutes: 55
prereqs: [p04-nlp-embeddings/03-word-embeddings]
tags: [embeddings, semantic-search, spring-ai]
quiz:
  - q: Why must padding tokens be excluded from mean pooling?
    options:
      - They are too long
      - They carry no content, so including them dilutes the average towards the padding vector and makes the embedding depend on batch padding length
      - Spring AI forbids it
      - They are always zero
    answer: 1
    why: The attention mask marks real tokens. Pooling over it makes the embedding independent of how much padding the batch needed.
  - q: How are modern embedding models typically trained?
    options:
      - Next-token prediction only
      - Contrastively — pulling (query, relevant passage) pairs together and pushing other passages in the batch apart
      - By hand-labelling every dimension
      - With word2vec
    answer: 1
    why: Contrastive objectives such as InfoNCE with in-batch negatives directly optimise the property retrieval needs.
  - q: What does Matryoshka representation learning allow?
    options:
      - Larger vocabularies
      - Truncating embeddings to fewer dimensions (e.g. 768 → 256) with modest quality loss, saving storage and compute
      - Embedding images
      - Faster tokenisation
    answer: 1
    why: The model is trained so that prefixes of the vector are themselves good embeddings. Re-normalise after truncating.
  - q: Some embedding models expect prefixes like "search_query:" and "search_document:". What happens if you omit them?
    options:
      - Nothing
      - Retrieval quality can drop noticeably, because the model was trained to embed queries and documents asymmetrically
      - The API returns an error
      - Vectors become longer
    answer: 1
    why: Always follow the model card. Asymmetric models (e5, nomic-embed, BGE) are trained with these instructions.
interview:
  - q: How would you choose an embedding model for an enterprise RAG system?
    a: |
      Evaluate on your own data, not just leaderboards. Shortlist by MTEB retrieval scores for your languages, context length (chunk size must fit), dimension (storage and latency), licence and deployment constraints (hosted vs local for data residency), and cost. Then build a small labelled set of real queries with their relevant passages and measure recall@k and MRR for each candidate with your chunking. Consider Matryoshka models to trade dimension for cost, and plan for re-embedding: store the model id and version with each vector, because changing models requires rebuilding the whole index.
    level: architect
---

## Why it matters

Semantic search, RAG retrieval, clustering, deduplication, classification heads, recommendation — all start with one vector per piece of text. This lesson connects word vectors to the sentence embeddings behind the course's own semantic search, and writes the search code against Spring AI's provider-neutral API.

## Concept

A transformer encoder (Phase 5) produces one vector **per token**, each informed by the whole sentence. To get one vector per text:

- **Mean pooling:** average the token vectors, ignoring padding (most sentence-transformer models).
- **CLS pooling:** use the vector of a special first token (BERT-style models).
- **Last-token pooling:** for decoder-based embedding models.

```mermaid
flowchart LR
    T["'how do I configure a datasource'"] --> TK[tokenizer]
    TK --> ENC[transformer encoder]
    ENC --> TV["token vectors: 8 × 768"]
    TV --> POOL["mean pool (mask-aware)"]
    POOL --> NORM[L2 normalise]
    NORM --> E["sentence embedding: 768"]
```

**Training** is contrastive: given pairs (query, relevant passage), maximise their similarity relative to every other passage in the batch (InfoNCE loss). The model learns exactly what retrieval needs — paraphrases close, unrelated text far.

**Matryoshka embeddings** are trained so that the first $k$ dimensions are themselves a good embedding. Truncating 768 → 256 dimensions cuts storage and search cost by 3× for a modest quality loss.

**Asymmetric prefixes.** Many models expect instructions: `search_query: …` for queries and `search_document: …` for passages (nomic-embed-text), or `query: …` / `passage: …` (e5). Omitting them silently lowers quality.

## Build it from scratch

Pooling and truncation:

<!-- code: lessons/p04-nlp-embeddings/src/main/java/dev/aieng/p04/SemanticSearch.java#pooling -->

Semantic search against Spring AI's `EmbeddingModel` interface — one batch call to index, one call per query:

<!-- code: lessons/p04-nlp-embeddings/src/main/java/dev/aieng/p04/SemanticSearch.java#index -->

The test uses a small deterministic fake `EmbeddingModel` so it runs offline. Swap in a real bean and the same class works against Ollama, OpenAI or an in-process ONNX model:

```java
@Bean
SemanticSearch semanticSearch(EmbeddingModel embeddingModel) {   // auto-configured by spring-ai-starter-model-ollama
    return new SemanticSearch(embeddingModel);
}
```

## Now with the library

```java
// Spring AI: one text, many texts, or full response with metadata
float[] v = embeddingModel.embed("search_query: how do I configure a datasource");
List<float[]> vs = embeddingModel.embed(List.of("search_document: ...", "search_document: ..."));
EmbeddingResponse r = embeddingModel.embedForResponse(texts);   // includes usage metadata

// LangChain4j equivalent
Embedding e = embeddingModel.embed("how do I configure a datasource").content();
```

Try it live: the course app's **Playground → Embedding similarity** tab embeds whatever you type with `nomic-embed-text`, and **Search → Semantic** ranks lesson sections with the code from this lesson.

## Production notes

- **Batch embedding calls.** One request with 32 texts is far cheaper than 32 requests.
- **Cache embeddings by content hash + model id.** Re-embedding unchanged content on every deploy wastes money.
- **Re-embedding is a migration.** Changing the model invalidates every stored vector; plan blue/green indexes.
- **Watch the context limit.** Text beyond the model's maximum tokens is silently truncated by many providers — your chunk's ending is simply not represented.

## Exercises

1. **Easy** — Add a fourth document to the semantic-search test and a query that should find it.
2. **Medium** — With Ollama running, write a small `main` that indexes the titles of all lessons in this phase with `OllamaEmbeddingModel` and searches them. Compare results with and without the `search_query:` / `search_document:` prefixes.
3. **Hard** — Measure the effect of Matryoshka truncation: embed 200 lesson sections with `nomic-embed-text`, then compare top-5 results at 768, 512, 256 and 128 dimensions against full-dimension results (overlap@5).

## Common mistakes

- Mixing query and document prefixes or forgetting them.
- Comparing vectors from different models or model versions.
- Embedding whole documents instead of chunks — one vector cannot represent fifty pages (Phase 9).

## Further reading

- Reimers & Gurevych, "Sentence-BERT", EMNLP 2019.
- Kusupati et al., "Matryoshka Representation Learning", NeurIPS 2022.
- MTEB leaderboard: <https://huggingface.co/spaces/mteb/leaderboard>
- Spring AI embeddings: <https://docs.spring.io/spring-ai/reference/api/embeddings.html>
