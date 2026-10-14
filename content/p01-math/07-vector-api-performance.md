---
title: SIMD in Pure Java with the Vector API
summary: How the JVM can run AI math at near-native speed — the Vector API, memory bandwidth, and where Java inference engines get their performance.
minutes: 40
prereqs: [p01-math/02-matrices]
tags: [math, performance, vector-api, jvm]
quiz:
  - q: What does SIMD stand for, and what does it do?
    options:
      - Single Instruction, Multiple Data — one CPU instruction operates on several values at once
      - Single Integer Memory Dispatch — faster integer loads
      - Synchronous Instruction Multi-Dispatch — runs threads in lock-step
      - Simple Iterative Matrix Decomposition
    answer: 0
    why: A 256-bit AVX2 register holds 8 floats; one fused multiply-add instruction processes all 8 lanes.
  - q: Why does the SIMD dot product need a scalar "tail" loop?
    options:
      - To handle array lengths that are not a multiple of the vector lane count
      - To check the result
      - Because the Vector API cannot add numbers
      - For thread safety
    answer: 0
    why: loopBound() rounds the length down to a multiple of the lane count; the remaining few elements are processed one at a time.
  - q: During LLM token generation on a CPU, what usually limits speed?
    options: [Integer division, Memory bandwidth — every weight is read once per token, Garbage collection, Disk I/O]
    answer: 1
    why: Generating one token reads all weights once and does relatively little math per byte, so it is memory-bandwidth bound. That is why quantisation speeds up inference.
interview:
  - q: Can Java be competitive for ML inference? What are the options and trade-offs?
    a: |
      Yes, for many workloads. Options: (1) call a native runtime through Java bindings — ONNX Runtime, PyTorch via DJL, llama.cpp-based servers like Ollama — giving native speed with JNI or HTTP overhead; (2) pure-Java engines such as Jlama, which use the Vector API for SIMD and the Foreign Function & Memory API for off-heap memory; (3) a hosted API. Trade-offs: native runtimes are fastest and most complete but bring native dependencies and platform-specific builds; pure Java is simpler to deploy and debug but usually lags native GPU performance and is still on an incubator API. For enterprise RAG, embedding inference with ONNX Runtime in-process is a very practical sweet spot.
    level: architect
---

## Why it matters

The previous lessons used plain loops. That is fine for learning, but real inference needs hardware-level parallelism. Modern CPUs can multiply 8 (AVX2), 16 (AVX-512) or 4 (ARM NEON) floats in **one instruction**. The JDK's **Vector API** (Project Panama) exposes that from pure Java — it is how Jlama runs LLMs on the JVM with no native code.

## Concept

```mermaid
flowchart LR
    subgraph Scalar["Scalar: 8 instructions"]
        a1["a0·b0"] --> a2["a1·b1"] --> a3["…"] --> a8["a7·b7"]
    end
    subgraph SIMD["SIMD (AVX2): 1 instruction"]
        v["[a0..a7] ⊙ [b0..b7] + acc"]
    end
```

- A **species** describes the vector shape: element type and lane count (e.g. 8 floats in 256 bits). `SPECIES_PREFERRED` picks the best for the running CPU.
- **FMA** (fused multiply-add) computes `acc + a * b` in one instruction with one rounding.
- **Reduction** (`reduceLanes(ADD)`) sums the lanes at the end.

The Vector API is still an **incubator module** — you must pass `--add-modules jdk.incubator.vector` to `javac` and `java`. This phase's `pom.xml` shows how to do that in Maven.

## Build it from scratch

<!-- code: lessons/p01-math/src/main/java/dev/aieng/p01/SimdDot.java#simd -->

The test `simdMatchesScalar` uses a length of 1,003 — deliberately not a multiple of any lane count — to exercise the tail loop. The results differ in the last decimals because the SIMD version adds in a different order; floating-point addition is not associative. Run `SimdDot.main` to see your CPU's lane count and a rough speed comparison (use JMH for real measurements).

## Now with the library

| Library | How it uses the hardware |
|---|---|
| **Jlama** | Pure-Java LLM inference; Vector API for matrix-vector products, FFM API for memory-mapped weights |
| **ONNX Runtime Java** | Native C++ kernels (MLAS, oneDNN, CUDA) via JNI |
| **DJL** | Delegates to PyTorch, ONNX Runtime or other native engines |
| **Lucene** | Uses the Vector API for vector-search distance functions (since Lucene 9.7) |

The last row matters for enterprise search: Elasticsearch and OpenSearch run on Lucene, so their vector search already uses this technique.

## Production notes

- **Inference is usually memory-bound, not compute-bound.** Generating one token reads every weight once. Halving bytes per weight (FP16 → INT8 → 4-bit) speeds generation up nearly proportionally — more than any SIMD trick.
- **Incubator APIs change between JDK releases.** Isolate Vector API code behind your own small interface so upgrades are local.
- **Measure on the target hardware.** Containers on cloud VMs may not expose AVX-512; `SPECIES_PREFERRED` adapts at run time, but your throughput estimates must too.

## Exercises

1. **Easy** — Run `SimdDot.main` and record the lane count and the speed ratio.
2. **Medium** — Write a JMH benchmark (`org.openjdk.jmh`) comparing `scalar` and `simd` for lengths 64, 1,024 and 1,048,576. Explain why small sizes gain little.
3. **Hard** — Implement a SIMD matrix-vector product (`float[] rows × float[] x`) and compare it with `Matrix.apply`. Then make it multi-threaded with a parallel stream over rows.

## Common mistakes

- Forgetting `--add-modules jdk.incubator.vector` at **run** time (the compile succeeds, the run fails).
- Expecting bit-identical results between scalar and SIMD code.
- Benchmarking with `System.nanoTime` loops and drawing conclusions — the JIT may eliminate or reorder your code.

## Further reading

- JEP 508: Vector API (Tenth Incubator) and its predecessors: <https://openjdk.org/jeps/508>
- Jlama: <https://github.com/tjake/Jlama>
- Aleksey Shipilëv, JMH samples: <https://github.com/openjdk/jmh/tree/master/jmh-samples>
