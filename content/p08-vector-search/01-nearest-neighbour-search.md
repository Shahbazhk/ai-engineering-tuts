---
title: Nearest-Neighbour Search and Recall
summary: Exact k-NN with a heap, the cost model that makes it break at scale, and recall@k — the metric every approximate index is judged by.
minutes: 40
prereqs: [p07-prompt-context/05-prompt-caching-and-cost]
tags: [vector-search, knn, recall]
quiz:
  - q: What is the cost of exact (brute-force) k-NN search per query?
    options: ["O(log n)", "O(n · d) — every vector is scored", "O(k)", "O(d)"]
    answer: 1
    why: Each of n vectors needs a d-dimensional dot product. Fine for thousands, too slow for hundreds of millions at low latency.
  - q: An approximate index returns 9 of the true top-10 neighbours on average. What is its recall@10?
    options: ["0.1", "0.9", "9", "Cannot be computed"]
    answer: 1
    why: recall@k = |approximate ∩ exact| / k, averaged over queries. Exact search provides the ground truth.
  - q: Why keep the top k in a min-heap of size k?
    options:
      - It sorts all n results
      - Each candidate only needs comparing with the worst of the current top k, giving O(n log k) instead of O(n log n)
      - It uses less precision
      - Heaps are required by Java
    answer: 1
    why: The heap root is the current k-th best; anything worse is skipped immediately.
interview:
  - q: When is brute-force vector search the right production choice?
    a: |
      More often than vendors suggest: when the collection is small (tens or low hundreds of thousands of vectors), when filters cut each query down to a small subset (per-tenant or per-user search), when perfect recall matters (legal, compliance), or for evaluation baselines. A 100k × 768 float scan is ~300 MB of reads and takes milliseconds with SIMD, it needs no index build or tuning, and inserts and deletes are trivial. Move to ANN when latency or cost targets demand it, and measure recall against the exact baseline when you do.
    level: senior
---

## Why it matters

RAG retrieval, semantic search, recommendations and deduplication are all **nearest-neighbour search**: given a query vector, find the $k$ most similar stored vectors. Before reaching for a vector database, know exactly what it computes and how to tell whether its approximations are acceptable.

## Concept

- **Similarity:** with L2-normalised vectors, dot product = cosine similarity (Phase 1).
- **Exact k-NN:** score all $n$ vectors, keep the best $k$. Cost $O(n \cdot d)$ per query.
- **Approximate nearest neighbour (ANN):** skip most of the vectors using an index (clusters or a graph). Much faster, occasionally misses true neighbours.
- **Recall@k:** the fraction of the true top-$k$ that the ANN index returns, averaged over queries. You **must** measure it — indexes fail silently.

```mermaid
flowchart LR
    Q[query vector] --> BF["brute force: score all n"]
    Q --> ANN["ANN index: score a small subset"]
    BF --> T["true top-k (ground truth)"]
    ANN --> A["approximate top-k"]
    T & A --> R["recall@k = overlap / k"]
```

Rough numbers: $10^6$ vectors × 768 dims × 4 bytes ≈ 3 GB scanned per brute-force query — tens to hundreds of milliseconds on a CPU. ANN indexes answer in about a millisecond.

## Build it from scratch

The interface shared by all indexes in this phase:

<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/VectorIndex.java -->

Exact search with a size-$k$ min-heap:

<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/BruteForceIndex.java#search -->

And the recall metric used throughout this phase:

<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/Benchmark.java#recall -->

## Now with the library

Every vector store offers an exact mode for small collections or evaluation: pgvector without an index (sequential scan), Elasticsearch/OpenSearch `script_score` queries, Qdrant's `exact: true` search parameter. Use it to build your recall baseline.

The course app's own semantic search (`app/.../search/SemanticIndex.java`) is brute force over ~1,000 lesson sections — the right choice at that size.

## Production notes

- **Benchmark on your data and filters.** Recall depends heavily on data distribution; synthetic benchmarks are only a start.
- **Filters change everything.** A highly selective filter can make ANN graphs perform badly; some engines switch to exact search for small filtered sets.
- **Track recall over time** by periodically comparing a sample of production queries against exact search.

## Exercises

1. **Easy** — Time `BruteForceIndex.search` at 1k, 10k and 100k vectors of 384 dimensions. Is it linear?
2. **Medium** — Replace `float[]` dot products with the SIMD version from Phase 1, lesson 7 (requires `--add-modules jdk.incubator.vector`) and measure the speed-up.
3. **Hard** — Store vectors in one contiguous `float[]` (row-major) instead of a `List<float[]>` and measure the effect on cache behaviour.

## Common mistakes

- Comparing un-normalised vectors with dot product while assuming cosine.
- Trusting ANN results without measuring recall.
- Filtering results *after* ANN search (returns fewer than $k$ hits and can leak — lesson 5).

## Further reading

- ANN-Benchmarks: <https://ann-benchmarks.com/>
- Johnson, Douze & Jégou, "Billion-scale similarity search with GPUs" (FAISS), 2017.
