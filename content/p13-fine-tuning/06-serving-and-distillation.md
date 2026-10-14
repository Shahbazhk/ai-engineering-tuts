---
title: Serving Custom Models from Java, and Distillation
summary: Serve fine-tuned adapters with Ollama, vLLM, Jlama or ONNX behind Spring AI — and use distillation to turn a frontier model's skill into a small, cheap one.
minutes: 40
prereqs: [p13-fine-tuning/05-quantisation]
tags: [serving, distillation, ollama, spring-ai]
quiz:
  - q: What is knowledge distillation in the LLM context?
    options:
      - Compressing text files
      - Training a smaller student model on outputs (labels or probability distributions) produced by a larger teacher model
      - Removing layers from a model
      - Quantisation
    answer: 1
    why: The student learns the teacher's behaviour on your task, at a fraction of the inference cost.
  - q: How does a Spring AI application use a fine-tuned model served by Ollama?
    options:
      - It needs a new SDK
      - Set spring.ai.ollama.chat.model to the custom model name; the code is unchanged
      - It must be rewritten in Python
      - It cannot
    answer: 1
    why: The provider abstraction makes custom models a configuration change.
  - q: What must you check in the terms of service before distilling from a hosted model?
    options:
      - Nothing
      - Whether the provider permits using its outputs to train other models
      - The colour scheme
      - The latency
    answer: 1
    why: Some providers restrict using outputs to develop competing models; check terms and licences.
interview:
  - q: Design the serving side for a fine-tuned triage model with zero-downtime upgrades.
    a: |
      Serve with a production engine (vLLM or TGI for GPU throughput with LoRA adapter support, or Ollama for simpler deployments) behind an OpenAI-compatible endpoint, so Spring AI's OpenAI provider (or Ollama provider) talks to it via configuration. Version models by name (triage-v3) and deploy new versions alongside old ones; route a percentage of traffic or shadow-traffic to the new version, compare eval and online metrics, then switch the configured model name and drain the old deployment. Keep the prompted frontier-model path as a fallback through a router with circuit breakers. Monitor latency, error rate, token throughput and quality signals per model version.
    level: architect
---

## Why it matters

A fine-tuned model is only valuable once it is served reliably and evaluated against what it replaces. And distillation is often the real business case: frontier-model quality on a narrow task at small-model cost.

## Concept

```mermaid
flowchart LR
    subgraph Distillation
        T["teacher: frontier model + good prompt"] -->|labels on 5k real inputs| D[(training set)]
        D --> S["student: 1–3B model + LoRA"]
    end
    subgraph Serving
        S --> E{"engine"}
        E --> O["Ollama (Modelfile + ADAPTER)"]
        E --> V["vLLM / TGI (OpenAI-compatible, multi-LoRA)"]
        E --> J["Jlama (pure Java, GGUF/safetensors)"]
        E --> X["ONNX Runtime (exported model)"]
    end
    O & V & J & X --> SAI["Spring AI ChatModel — configuration only"]
```

**Distillation variants:**

- **Label distillation (most common):** the teacher produces outputs; the student is fine-tuned on them (lesson 3's pipeline with the teacher as labeller).
- **Logit distillation:** the student matches the teacher's full next-token distributions (minimises KL divergence, Phase 1) — needs access to teacher logits, so mostly for open models.

## Build it

Ollama can serve a LoRA adapter on top of its base model:

<!-- code: lessons/p13-fine-tuning/python/Modelfile -->

```bash
ollama create support-triage -f Modelfile
```

Then point Spring AI at it — no code change in `TicketTriage` from Phase 6:

```yaml
spring:
  ai:
    ollama:
      chat:
        model: support-triage
```

For GPU throughput, vLLM serves an OpenAI-compatible API (with LoRA adapters selectable per request); use Spring AI's OpenAI provider with `spring.ai.openai.base-url` pointing at it.

### Evaluate the switch

Run the same eval set through the old (prompted frontier) and new (distilled) models with `EvalRunner` (Phase 12), and compare quality, latency and cost per 1,000 requests. Promote only if quality is within tolerance and the cost or latency gain is real.

## Now with the library

| Engine | Java integration |
|---|---|
| Ollama | Spring AI Ollama provider; LangChain4j Ollama |
| vLLM / TGI | Spring AI OpenAI provider (OpenAI-compatible API) |
| Jlama | In-process pure Java; LangChain4j Jlama integration |
| ONNX Runtime | In-process via ONNX Runtime Java / DJL (best for encoders, classifiers, small models) |

## Production notes

- **Version model names** (`triage-v3`) and keep the previous version deployable for rollback.
- **Keep a fallback route** to the prompted large model for low-confidence cases.
- **Re-distil** when the teacher, the task or the data distribution changes.
- **Licences and terms:** check that your teacher's terms allow training on its outputs and that the student's base model licence fits your use.

## Exercises

1. **Easy** — Create the `support-triage` model in Ollama from a base model with only the `SYSTEM` and `PARAMETER` lines (no adapter) and call it from `TicketTriage`.
2. **Medium** — Write a distillation job: use a strong model through `ChatClient` with structured output to label 200 tickets, then feed them to `DatasetBuilder`.
3. **Hard** — Add a router: use the distilled model first and escalate to the large model when its structured output fails validation or a confidence field is below 0.7. Measure the escalation rate and blended cost.

## Common mistakes

- Comparing the student with the teacher on different prompts or data.
- No fallback path.
- Ignoring provider terms on training with outputs.

## Further reading

- Hinton, Vinyals & Dean, "Distilling the Knowledge in a Neural Network", 2015.
- Ollama Modelfile reference (ADAPTER instruction).
- vLLM LoRA adapters documentation; Jlama project.
