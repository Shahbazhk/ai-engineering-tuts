---
title: Deep Learning in Java — DJL, ONNX Runtime and Model Formats
summary: Map your hand-built engine onto the production Java options, and learn the model formats you will meet — PyTorch, safetensors, ONNX and GGUF.
minutes: 40
prereqs: [p03-neural-networks/06-training-loop]
tags: [neural-networks, djl, onnx, model-formats]
quiz:
  - q: Which format is designed for portable, framework-independent inference and is widely used to run models from Java?
    options: [Pickle, ONNX, CSV, Parquet]
    answer: 1
    why: ONNX describes the computation graph and weights in a standard format that ONNX Runtime executes on CPU or GPU, with an official Java API.
  - q: Why is loading pickle-based PyTorch checkpoints from untrusted sources dangerous?
    options:
      - They are too large
      - Unpickling can execute arbitrary code
      - They only work on GPUs
      - They lose precision
    answer: 1
    why: Python pickle can run code on load. Safetensors stores only tensors and is safe to load, which is why it became the standard on Hugging Face.
  - q: What is GGUF mainly used for?
    options:
      - Training models
      - Quantised LLM weights for llama.cpp-based runtimes such as Ollama
      - Image compression
      - Storing embeddings in databases
    answer: 1
    why: GGUF packs (often 4–8-bit quantised) weights plus tokenizer and metadata in one file for efficient local inference.
interview:
  - q: You need sentence embeddings inside a Java service with strict data-residency rules. How would you deploy the model?
    a: |
      Run the embedding model in-process or in a sidecar inside the controlled environment, with no external API. Practical options: export a sentence-transformer (e.g. a MiniLM or BGE variant) to ONNX and run it with ONNX Runtime Java plus the Hugging Face tokenizer via DJL's tokenizer bindings; or use DJL's model zoo with the PyTorch engine; or run Ollama/TEI as a sidecar container and call it over localhost. ONNX Runtime in-process gives the lowest latency and simplest operations; batch requests, pin the model version and checksum, warm the session at startup, and expose it through Spring AI's EmbeddingModel interface so the rest of the code is provider-agnostic.
    level: architect
---

## Why it matters

You will rarely train a large model, but you will often **run** models inside Java services — embedding models, rerankers, classifiers, small LLMs — for latency, cost or data-residency reasons. This lesson connects what you built to the tools you will deploy.

## Concept

```mermaid
flowchart LR
    subgraph Training["Training (usually Python)"]
        PT[PyTorch model] --> ST[safetensors]
        PT --> ONNXX[export to ONNX]
        ST --> GG[convert + quantise → GGUF]
    end
    subgraph Serving["Serving from Java"]
        ONNXX --> ORT[ONNX Runtime Java — in-process]
        ST --> DJL[DJL + PyTorch engine — in-process]
        GG --> OLL[Ollama / llama.cpp — sidecar over HTTP]
        GG --> JL[Jlama — pure Java]
    end
    ORT & DJL & OLL & JL --> SAI[Spring AI EmbeddingModel / ChatModel]
```

| Format | Contains | Typical use |
|---|---|---|
| PyTorch `.pt` / `.bin` | Pickled tensors (can run code on load!) | Legacy checkpoints |
| `safetensors` | Raw tensors + header, safe to load | Hugging Face standard |
| ONNX | Computation graph + weights | Portable inference, ONNX Runtime |
| GGUF | Quantised weights + tokenizer + metadata | llama.cpp, Ollama, Jlama |

## Map: your engine vs the libraries

| You built | DJL | ONNX Runtime |
|---|---|---|
| `Tensor` | `NDArray` | `OnnxTensor` |
| `Trainer.Model` | `Block` / `SequentialBlock` | (inference only) |
| `Optimizers.adam` | `Optimizer.adam()` | — |
| Forward pass | `Predictor.predict` | `OrtSession.run` |

## Now with the library

Running an exported embedding model with ONNX Runtime (reference snippet — `com.microsoft.onnxruntime:onnxruntime` and `ai.djl.huggingface:tokenizers` are not build dependencies of this course because they pull in large native binaries):

```java
try (OrtEnvironment env = OrtEnvironment.getEnvironment();
     OrtSession session = env.createSession("all-MiniLM-L6-v2.onnx", new OrtSession.SessionOptions());
     HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance("sentence-transformers/all-MiniLM-L6-v2")) {

    Encoding enc = tokenizer.encode("Spring Boot auto-configuration");
    long[][] ids = {enc.getIds()};
    long[][] mask = {enc.getAttentionMask()};
    long[][] types = {enc.getTypeIds()};
    Map<String, OnnxTensor> inputs = Map.of(
            "input_ids", OnnxTensor.createTensor(env, ids),
            "attention_mask", OnnxTensor.createTensor(env, mask),
            "token_type_ids", OnnxTensor.createTensor(env, types));
    try (OrtSession.Result out = session.run(inputs)) {
        float[][][] tokenVectors = (float[][][]) out.get(0).getValue();  // [batch][tokens][384]
        // mean-pool over tokens (respecting the mask) and L2-normalise -> sentence embedding (Phase 4)
    }
}
```

Spring AI also ships a `TransformersEmbeddingModel` (module `spring-ai-transformers`) that wraps exactly this ONNX + tokenizer pipeline behind the `EmbeddingModel` interface.

## Production notes

- **Never load untrusted pickle checkpoints.** Prefer safetensors, ONNX or GGUF; verify checksums of model files like any other artefact.
- **Warm up** ONNX/DJL sessions at startup — the first inference compiles kernels and allocates memory.
- **Native memory is outside the Java heap.** Size containers for heap + native model memory, and close tensors/sessions (they are `AutoCloseable`) to avoid leaks.
- **Sidecar vs in-process:** in-process is fastest and simplest to secure; a sidecar (Ollama, Text Embeddings Inference) isolates crashes, scales independently and can use a GPU node.

## Exercises

1. **Easy** — For a model you might deploy, list its format, size in GB, and whether it would run in-process or as a sidecar in your environment.
2. **Medium** — Add DJL and ONNX Runtime to a scratch project, run the snippet above with an exported MiniLM model, and implement mean pooling.
3. **Hard** — Wrap the ONNX pipeline as a Spring AI `EmbeddingModel` bean and swap it into the course app in place of Ollama embeddings. Compare semantic-search quality and latency.

## Common mistakes

- Forgetting the tokenizer must match the model exactly.
- Mixing up pooling strategies (CLS vs mean) — the model card tells you which one the model was trained with.
- Not closing native resources.

## Further reading

- DJL: <https://djl.ai/>
- ONNX Runtime Java API: <https://onnxruntime.ai/docs/get-started/with-java.html>
- Hugging Face safetensors: <https://huggingface.co/docs/safetensors>
- GGUF specification: <https://github.com/ggml-org/ggml/blob/master/docs/gguf.md>
