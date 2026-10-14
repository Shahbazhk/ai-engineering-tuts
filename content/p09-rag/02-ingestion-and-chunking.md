---
title: Ingestion and Chunking
summary: Extract, clean, chunk and enrich documents — fixed-size with overlap, recursive splitting, heading-aware chunks with contextual headers — and run it as a reliable pipeline.
minutes: 55
prereqs: [p09-rag/01-rag-architecture]
tags: [rag, chunking, ingestion, etl]
quiz:
  - q: What is the purpose of overlap between fixed-size chunks?
    options:
      - To use more storage
      - To reduce the chance that a fact is split across a boundary and lost from both chunks
      - To make embeddings faster
      - It is required by vector stores
    answer: 1
    why: A sentence cut in half may be unretrievable from either half. Overlap of ~10–20% is a common default.
  - q: Why prepend "IT Handbook > VPN > Windows > Proxy" to the chunk "Set the proxy port to 8443."?
    options:
      - For formatting
      - The chunk alone does not mention the VPN; the header carries the context so the chunk is retrievable for "VPN proxy port"
      - Vector stores require titles
      - To increase token count
    answer: 1
    why: Contextual chunk headers are one of the cheapest, most effective retrieval improvements.
  - q: Why use deterministic chunk ids such as sourceId#chunkIndex?
    options:
      - They are shorter
      - Re-ingesting a changed document overwrites (upserts) its chunks instead of creating duplicates, and deletions can target them
      - Embedding models need them
      - For alphabetical sorting
    answer: 1
    why: Idempotent ingestion is essential for pipelines that run on every change.
interview:
  - q: How do you pick a chunk size?
    a: |
      Empirically, within constraints. Constraints: the embedding model's max input tokens, and the prompt budget (k chunks × chunk size must fit). Starting points: 200–500 tokens with 10–20% overlap for prose; structure-aware chunks (by heading, section, table, function) for structured content. Then evaluate: build a labelled query set, try 2–3 sizes and strategies, and measure retrieval recall@k and answer quality. Smaller chunks give precise retrieval but lose context; larger chunks dilute embeddings and cost more tokens. Techniques like contextual headers, parent-document retrieval (retrieve small, return the larger parent) and late chunking soften the trade-off.
    level: senior
---

## Why it matters

Retrieval can only find what ingestion produced. Poor extraction and chunking are the most common root cause of bad RAG answers — and the least glamorous part of the system, so they are often under-engineered.

## Concept

**Extraction:** convert sources to clean text plus metadata — strip navigation and boilerplate, keep headings, handle tables, record title, URL, author, dates, language and **permissions**.

**Chunking strategies:**

| Strategy | How | Good for |
|---|---|---|
| Fixed-size + overlap | N words/tokens per chunk, M repeated | Uniform prose, baseline |
| Recursive | Split by paragraphs, then lines, sentences, words — only as needed | General text |
| Structure-aware | Split at headings/sections; keep heading path | Docs, wikis, web content |
| Semantic | Split where embedding similarity between sentences drops | Long unstructured text |
| Parent–child | Index small chunks, return the larger parent section | Precise retrieval with full context |

**Contextual chunk headers:** prepend `Document title > Section > Subsection` to each chunk before embedding.

```mermaid
flowchart LR
    MD["# VPN / ## Windows / ### Proxy / Set the proxy port to 8443."] --> H["heading-aware split"]
    H --> C["chunk: 'Set the proxy port to 8443.' path: VPN > Windows > Proxy"]
    C --> CH["embed: 'IT Handbook > VPN > Windows > Proxy\nSet the proxy port to 8443.'"]
```

## Build it from scratch

Fixed-size with overlap:

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/Chunker.java#fixed -->

Recursive splitting — coarse separators first:

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/Chunker.java#recursive -->

Heading-aware chunks with contextual headers:

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/Chunker.java#headings -->

The tests check overlap boundaries, that recursive splitting keeps whole paragraphs when they fit, and that the chunk `Set the proxy port to 8443.` carries the path `VPN > Windows > Proxy`.

## Now with the library

Spring AI's ETL pipeline: `DocumentReader` → `DocumentTransformer` → `DocumentWriter`:

```java
List<Document> docs = new TikaDocumentReader(new FileSystemResource("handbook.pdf")).get();  // spring-ai-tika-document-reader
List<Document> chunks = TokenTextSplitter.builder().withChunkSize(400).build().apply(docs);
vectorStore.add(chunks);   // VectorStore is also a DocumentWriter
```

Readers exist for PDF (PDFBox), Tika (Office formats), Markdown, JSON, HTML (jsoup) and more. For large or continuous ingestion, run this inside **Spring Batch** (chunk-oriented steps, restartability, skip policies) or a Kafka consumer reacting to content-change events.

## Production notes

- **Idempotent upserts:** deterministic ids, content hashes to skip unchanged chunks, explicit deletes for removed content.
- **Track lineage:** source id, version and ingestion timestamp on every chunk, so answers can be traced and stale content found.
- **Tables:** convert to Markdown or row-wise sentences; naïve text extraction destroys them.
- **Respect licences and data classification** of every source before ingesting.

## Exercises

1. **Easy** — Chunk a long README with `fixedSize(…, 120, 20)` and with `byHeadings(…, 800)`. Which chunks would you rather retrieve?
2. **Medium** — Implement parent–child retrieval: index `byHeadings` chunks of ≤ 300 characters but return the full section they came from.
3. **Hard** — Write a Spring Batch job that reads Markdown files from a folder, chunks them with `Chunker.byHeadings`, skips unchanged chunks by content hash, and writes to a `VectorStore`. Make it restartable.

## Common mistakes

- Chunking by characters through the middle of words, code or tables.
- Random ids (duplicates on every run).
- Dropping headings and titles — the most informative text on the page.

## Further reading

- Spring AI ETL pipeline: <https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html>
- Anthropic, "Introducing Contextual Retrieval" (2024).
