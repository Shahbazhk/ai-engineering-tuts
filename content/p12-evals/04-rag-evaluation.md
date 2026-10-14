---
title: Evaluating RAG — Faithfulness, Context Precision and Recall
summary: Score retrieval and generation separately so every wrong answer points to its root cause.
minutes: 45
prereqs: [p12-evals/03-llm-as-judge]
tags: [evals, rag, faithfulness]
quiz:
  - q: An answer is correct but contains one sentence not supported by the retrieved context. What does faithfulness measure here?
    options:
      - Nothing — the answer is correct
      - It drops below 1.0 because one claim is unsupported, signalling the model added information from elsewhere
      - It measures retrieval only
      - It is undefined
    answer: 1
    why: Faithfulness checks grounding, not truth. Unsupported claims are risky even when they happen to be right.
  - q: Context recall is low but faithfulness is high. Where is the problem?
    options: [Generation, Retrieval — the needed facts never reached the model, The judge, The UI]
    answer: 1
    why: The model faithfully used what it got; it just didn't get the right evidence.
  - q: Which RAG metric captures "too many irrelevant chunks in the prompt"?
    options: [Faithfulness, Context precision, Answer relevance, Recall@k]
    answer: 1
    why: Low context precision means wasted tokens and more distraction for the model.
interview:
  - q: Walk through how you would debug a RAG system whose answer accuracy dropped after a content migration.
    a: |
      Use stage-level metrics on the eval set. If context recall/retrieval recall dropped, retrieval is the cause: check ingestion (did extraction break on the new format? are chunks empty or boilerplate?), metadata and filters (new site ids? permissions?), embeddings (was the index rebuilt with the same model?), and chunk ids (duplicates). If recall is fine but faithfulness or correctness dropped, look at generation: prompt changes, context ordering, chunk content quality, model version. Compare per-category metrics before and after, inspect individual failures, fix, re-run, and add the failing cases permanently to the dataset.
    level: architect
---

## Why it matters

"The answer was wrong" has two root causes with completely different fixes: **retrieval** did not supply the evidence, or **generation** did not use it (or invented more). RAG-specific metrics separate them.

## Concept

```mermaid
flowchart LR
    Q[question] --> R[retrieval]
    R --> C[context chunks]
    C --> G[generation]
    G --> A[answer]
    R -. "context recall: needed facts present?" .-> C
    R -. "context precision: chunks relevant?" .-> C
    C -. "faithfulness: every claim supported?" .-> A
    Q -. "answer relevance: addresses the question?" .-> A
```

| Metric | Stage | Low score means |
|---|---|---|
| Context recall | Retrieval | Evidence missing — fix chunking, embeddings, hybrid search, k |
| Context precision | Retrieval | Noise in the prompt — rerank, raise threshold, lower k |
| Faithfulness | Generation | Hallucination — tighten grounding instructions, model choice |
| Answer relevance | Generation | Off-topic or evasive answers — prompt, query rewriting |

These are the core metrics popularised by RAGAS; they can be computed with labelled data, an LLM verifier, or both.

## Build it from scratch

<!-- code: lessons/p12-evals/src/main/java/dev/aieng/p12/RagMetrics.java#rag -->

Faithfulness splits the answer into claims and asks a verifier whether each is supported. In production the verifier is an LLM ("Is this claim supported by the context? yes/no"); the tests use a lexical verifier so they run offline. They show a faithful answer scoring 1.0 and an answer with an invented "hardware token" requirement scoring 0.5 — even though half of it is correct.

## Now with the library

```java
// Spring AI: relevancy of an answer to the question given the retrieved docs
var relevancy = new RelevancyEvaluator(ChatClient.builder(judgeModel));

// Fact checking (faithfulness-style) of a claim against documents
var factCheck = FactCheckingEvaluator.builder(ChatClient.builder(judgeModel)).build();
```

Get the retrieved documents from the RAG advisor's response context (Phase 9, lesson 4) so the evaluator sees exactly what the model saw.

## Production notes

- **Log retrieval with every answer** (chunk ids, scores) so you can compute these metrics on production samples.
- **Separate dashboards** for retrieval and generation metrics.
- **Unanswerable questions** need their own metric: correct refusal rate.

## Exercises

1. **Easy** — Write three answers for the VPN context: fully faithful, partly faithful, unfaithful. Predict and then compute their faithfulness.
2. **Medium** — Implement an LLM verifier `BiPredicate<String, String>` with a strict yes/no prompt and a fake model in tests.
3. **Hard** — Run the course Tutor over 20 questions with Ollama, compute context recall (using expected lesson ids), faithfulness (LLM verifier) and refusal accuracy. Which stage needs work first?

## Common mistakes

- Only measuring end-answer correctness.
- Evaluating faithfulness against documents other than those actually retrieved.
- Ignoring refusal cases.

## Further reading

- Es et al., "RAGAS: Automated Evaluation of Retrieval Augmented Generation", 2023.
- Spring AI evaluation documentation.
