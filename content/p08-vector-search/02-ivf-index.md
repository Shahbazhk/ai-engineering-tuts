---
title: IVF — Clustering-Based Indexes
summary: Partition vectors with k-means and only search the nearest cells — the nlist/nprobe trade-off behind FAISS, Milvus and pgvector's ivfflat.
minutes: 45
prereqs: [p08-vector-search/01-nearest-neighbour-search]
tags: [vector-search, ivf, k-means]
quiz:
  - q: In an IVF index, what does nprobe control?
    options:
      - The number of clusters built
      - How many of the closest cells are scanned per query — higher means better recall and more work
      - The vector dimension
      - The number of results returned
    answer: 1
    why: nprobe = nlist scans everything (exact); nprobe = 1 is fastest but misses neighbours across cell boundaries.
  - q: Why can IVF miss a true nearest neighbour?
    options:
      - Its distance function is wrong
      - The neighbour may sit in a cell whose centroid is not among the nprobe closest to the query
      - It only stores centroids
      - It uses random scores
    answer: 1
    why: Points near cell boundaries are the classic failure; more probes reduce it.
  - q: What must happen when the data distribution shifts a lot after an IVF index was trained?
    options:
      - Nothing
      - Re-train the centroids (rebuild the index), or recall and balance degrade
      - Increase k
      - Lower the dimension
    answer: 1
    why: Centroids reflect the data at training time; heavy drift leaves unbalanced cells and poor partitioning.
interview:
  - q: IVF or HNSW for a 200-million-vector corpus?
    a: |
      It depends on memory, update pattern and latency targets. HNSW gives the best recall/latency trade-off but keeps a graph of ~M links per vector in RAM on top of the vectors and is slow to build; deletes and heavy updates are awkward. IVF (especially with product quantisation, IVF-PQ) has a much smaller memory footprint, builds faster, supports disk-based and GPU implementations, but needs training and tuning of nlist/nprobe and gives lower recall at the same latency. For 200M vectors on a budget, IVF-PQ (FAISS, Milvus) or DiskANN-style indexes are common; with ample RAM and frequent queries, sharded HNSW. Benchmark recall@k and p99 latency with your filters before deciding.
    level: architect
---

## Why it matters

IVF is the simplest serious ANN index and the basis of large-scale systems (FAISS, Milvus, pgvector's `ivfflat`). It shows the core trade-off of every approximate index: **less work for slightly worse recall**, controlled by a knob you must tune.

## Concept

1. **Train:** cluster the vectors into `nlist` cells with k-means. Each cell has a centroid.
2. **Assign:** store each vector in the list (the "inverted file") of its nearest centroid.
3. **Search:** compare the query with all centroids, pick the `nprobe` nearest cells, scan only those vectors exactly.

```mermaid
flowchart TB
    Q((query)) --> C{"compare with nlist centroids"}
    C -->|nearest| C1["cell 7: 210 vectors — scan"]
    C -->|2nd nearest| C2["cell 3: 180 vectors — scan"]
    C -.->|skipped| C3["cells 1, 2, 4 … — never touched"]
    C1 & C2 --> TOP["exact top-k among scanned vectors"]
```

Work per query ≈ `nlist` centroid comparisons + `nprobe × n / nlist` vector comparisons. A common starting point is `nlist ≈ √n` and `nprobe` a few percent of `nlist`, then tune by measuring recall.

**k-means** (Lloyd's algorithm): pick initial centroids, assign every point to its nearest centroid, move each centroid to the mean of its points, repeat. On normalised vectors we re-normalise the means ("spherical k-means").

## Build it from scratch

Training:

<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/IvfIndex.java#build -->

Searching only the nearest `nprobe` cells:

<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/IvfIndex.java#search -->

The test `ivfTradesRecallForWorkViaNprobe` shows both ends: probing every cell gives recall exactly 1.0; probing 2 of 30 cells scans less than a third of the data while still returning most true neighbours.

## Now with the library

```sql
-- pgvector: IVF index; lists ≈ rows/1000 for up to 1M rows is the documented starting point
CREATE INDEX ON chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 1000);
SET ivfflat.probes = 10;   -- per session/transaction: the nprobe knob
SELECT id, content FROM chunks ORDER BY embedding <=> $1 LIMIT 10;
```

Build `ivfflat` indexes **after** loading representative data — the centroids are trained from the rows present at creation time.

## Production notes

- **Rebuild after big data changes** — new content types, a new embedding model, or 10× growth.
- **Product quantisation (PQ)** compresses vectors inside cells (e.g. 768 floats → 64 bytes), enabling billions of vectors in memory at some recall cost.
- **Tune nprobe per use case:** interactive search may accept 0.95 recall; compliance retrieval may need exact search.

## Exercises

1. **Easy** — With the benchmark data, print recall@10 for nprobe = 1, 2, 4, 8, 16 and the number of vectors scanned.
2. **Medium** — Add k-means++ initialisation (choose initial centroids far apart) and compare cell balance (min/max cell sizes).
3. **Hard** — Implement incremental `add` that assigns new vectors to the existing nearest centroid instead of rebuilding, and measure how recall degrades as you add data from a shifted distribution.

## Common mistakes

- Building the index on an empty or tiny table.
- Leaving `probes`/`nprobe` at 1 and blaming the embeddings for poor results.
- Never re-training after the embedding model changes.

## Further reading

- Jégou, Douze & Schmid, "Product Quantization for Nearest Neighbor Search", IEEE TPAMI 2011.
- pgvector documentation, IVFFlat section: <https://github.com/pgvector/pgvector>
