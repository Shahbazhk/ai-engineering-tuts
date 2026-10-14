# Build Prompt: "AI Engineering From Scratch — The Java & Spring Edition"

## Role

Act as a principal AI engineer, Java/Spring architect, and technical curriculum designer. Build a self-paced learning web application in this directory. Use https://aiengineeringfromscratch.com/ and https://github.com/rohitg00/ai-engineering-from-scratch as the model for structure and teaching style. Do not copy their content. Do not use Python as the main language. Every concept is built and run in **Java 21+ and Spring**.

## Learner Profile

- 14 years of professional experience in Java, Spring, and Liferay DXP. Certified PMP and PSM I.
- Expert in Java, Spring internals, enterprise architecture, and delivery. **Do not teach Java, Spring, OOP, design patterns, or Agile basics.** Use them as the base and refer to them for analogies (for example, "an agent tool registry is like a `BeanFactory`").
- New to AI/ML math, neural networks, LLM internals, and AI system design.
- Goal: become a hands-on **AI engineer and AI solution architect** in the Java ecosystem. Must be able to design, build, evaluate, ship, and lead enterprise AI products, and to integrate AI into existing Spring and Liferay platforms.
- Learns best by building from first principles, then using the production library.

## Teaching Principle

For every major concept:
1. **From scratch in plain Java:** a minimal implementation with no AI libraries. For example: autograd engine, BPE tokenizer, attention, vector index, agent loop.
2. **With the library:** the same capability using Spring AI, LangChain4j, DJL, or ONNX Runtime, with an explanation of what the library does internally.
3. **In production:** how it looks in an enterprise Spring Boot service, including cost, latency, security, observability, and governance.

## Curriculum (Phases)

Each phase has 5–12 lessons with explicit prerequisites and time estimates.

0. **Setup & AI Toolchain for Java Developers** — JDK 21, Maven/Gradle, Docker, Ollama (local models), an API key for a hosted LLM, Jupyter with a Java kernel (JJava/IJava) for experiments, GPU and CPU options, and an overview of the Java AI ecosystem: Spring AI, LangChain4j, DJL, ONNX Runtime, Tribuo, Jlama, and the Panama Vector API.
1. **Math for AI, in Java** — vectors, matrices, dot products, norms, derivatives, chain rule, gradients, probability, softmax, entropy, and cross-entropy. Each topic is implemented in Java and visualized.
2. **Classical ML** — regression, classification, gradient descent, overfitting, regularization, evaluation metrics, and train/test splits. Built from scratch, then with Tribuo or Smile.
3. **Neural Networks From Scratch** — a scalar autograd engine (micrograd-style) in Java, then tensors, an MLP, backpropagation, optimizers (SGD, Adam), and training loops. Performance with the Vector API.
4. **NLP & Embeddings** — tokenization (build a BPE tokenizer), word embeddings, sentence embeddings, similarity metrics, and running embedding models through ONNX Runtime or DJL.
5. **Transformers From Scratch** — self-attention, multi-head attention, positional encoding, layer norm, and a decoder-only GPT in Java. Train a tiny model on a toy dataset. Sampling strategies (temperature, top-k, top-p). KV cache.
6. **LLMs in Practice** — hosted vs. local models, model selection, context windows, tokens and cost, streaming, structured output, function/tool calling, and multimodal input. Built with Spring AI `ChatClient` and LangChain4j `AiServices`, with a side-by-side comparison of the two.
7. **Prompt & Context Engineering** — system prompts, few-shot prompting, chain-of-thought, prompt templates, context window management, prompt caching, and prompt versioning as code.
8. **Vector Databases & Search** — build brute-force, IVF, and HNSW indexes in Java. Then pgvector, Redis, Elasticsearch/OpenSearch, Qdrant, and Milvus with Spring AI `VectorStore`. Hybrid search (BM25 plus vectors) and re-ranking.
9. **RAG (Retrieval-Augmented Generation)** — ingestion pipelines (with Spring Batch where it helps), chunking strategies, metadata filtering, query rewriting, re-ranking, citations, GraphRAG, and advanced RAG patterns. Includes a RAG system over Liferay DXP content (web content, documents, knowledge base) through Liferay headless APIs.
10. **Agents & Tool Use** — build an agent loop from scratch (ReAct), then planning, memory (short-term and long-term), tool registries, multi-agent orchestration, human-in-the-loop steps, workflow agents vs. autonomous agents, and failure modes.
11. **Model Context Protocol (MCP)** — build MCP servers and clients in Java with the MCP Java SDK and Spring AI. Expose enterprise systems as MCP tools, including a Liferay MCP server. Cover security and authorization for tools.
12. **Evaluation & Testing of AI Systems** — eval datasets, LLM-as-judge, RAG metrics (faithfulness, relevance, context precision), regression evals in CI, testing non-deterministic code, and Testcontainers with Ollama.
13. **Fine-tuning & Model Customization** — when to fine-tune vs. RAG vs. prompting, LoRA/QLoRA concepts, dataset preparation, and distillation. Training uses Python only where Java has no realistic option. Serve the resulting model from Java (ONNX, GGUF via Jlama or Ollama).
14. **Production AI Engineering (LLMOps)** — gateway and routing patterns, semantic caching, rate limiting, fallbacks, cost control, streaming over SSE/WebFlux, virtual threads for I/O-bound LLM calls, observability (Micrometer, OpenTelemetry, token metrics, traces), and deployment on Kubernetes.
15. **AI Security, Safety & Governance** — prompt injection, data exfiltration through tools, PII redaction, guardrails, OWASP Top 10 for LLMs, red-teaming, the EU AI Act, ISO/IEC 42001, NIST AI RMF, audit trails, and responsible AI.
16. **AI Architecture & System Design** — reference architectures for enterprise AI, AI in a modular monolith vs. microservices, event-driven AI pipelines (Kafka), multi-tenant AI, and ADRs for AI decisions. Case studies: enterprise knowledge assistant, support copilot, document processing pipeline, AI search for a Liferay portal, code-review agent.
17. **AI Product & Delivery Leadership** — this phase builds on PMP/PSM experience. Cover the AI project lifecycle, discovery and feasibility, uncertainty in estimation, experiment-driven Scrum for AI work, defining "done" with evals, ROI and cost models, stakeholder communication, build vs. buy, vendor risk, and AI team roles.
18. **Capstone Projects** — 3 production-grade Spring Boot systems, each with design docs, evals, dashboards, and a written architecture review. At least one capstone integrates with Liferay DXP.

## Lesson Template (every lesson follows it)

1. **Why it matters** — the enterprise problem it solves.
2. **Concept** — an intuitive explanation, the math where needed, and a diagram (Mermaid/SVG).
3. **Build it from scratch** — plain Java, no AI libraries.
4. **Now with the library** — Spring AI / LangChain4j / DJL, plus what happens internally.
5. **Runnable code** — a standalone Maven module under `lessons/<phase>/<lesson>/` with passing tests. Default to Ollama with a local model so lessons run free and offline. Allow a switch to a hosted provider through configuration.
6. **Production notes** — cost, latency, scaling, security, and observability.
7. **Exercises** — easy, medium, and hard, with reference solutions.
8. **Common mistakes & debugging.**
9. **Quiz** — 5–10 questions with explanations.
10. **Interview & architecture prep** — AI engineer and AI architect interview questions with model answers.
11. **Further reading** — papers, official docs, and source code.

## Web Application Requirements

- **Stack:** Spring Boot 3.x (current release) with Spring AI (current release). Use Thymeleaf + HTMX for the UI. Lessons are Markdown files rendered on the server. H2 or PostgreSQL with pgvector. Keep the stack lean.
- **Content as files:** lessons live in the repo as Markdown + code. The app indexes them at startup. Adding a lesson must never require code changes.
- **Features:**
  - Roadmap with phases, prerequisites, time estimates, and a progress view.
  - Lesson pages with syntax highlighting, diagrams, math rendering (KaTeX), and copy buttons.
  - Progress tracking and quiz scores stored in the database.
  - Interactive quizzes.
  - **AI tutor built into the app** (dogfooding): a RAG assistant over the course content, built with Spring AI. It answers questions with citations to lessons.
  - **Playground pages**: try prompts, compare models, view embeddings and similarity, and inspect each RAG pipeline step.
  - Full-text and semantic search.
  - Glossary, an interview question bank, and an architecture case-study library.
  - Dark and light mode, responsive layout.
- **Quality:** clean modular package structure, tests, eval suite for the AI tutor, a README, and `docker compose up` to start the app, PostgreSQL/pgvector, and Ollama.

## Delivery Plan

Work incrementally and verify each step:

1. Propose the repo structure, module layout, and tech choices with short ADRs. Wait for my approval.
2. Build the app skeleton: content loader, roadmap, lesson page, progress.
3. Write Phases 0–3 fully as reference-quality lessons to set the quality bar.
4. Add quizzes, search, and the AI tutor (RAG over the lessons).
5. Write the remaining phases one at a time. Before moving to the next phase, confirm all code modules build and tests pass.
6. Add the capstones, system design case studies, and the leadership phase last.

## Constraints

- Java 21 LTS, current Spring Boot 3.x, current Spring AI and LangChain4j releases. Pin versions and check that the APIs exist in those versions before using them.
- All "runnable" code must compile and pass tests. No pseudo-code.
- Use Python only where Java has no realistic alternative (mainly model training in Phase 13). Mark each such use and explain why.
- Accuracy first: cite papers and official docs. State any trade-off and its limits.
- Depth over breadth. The audience is a senior engineer: skip beginner explanations of Java and Spring, and focus on internals, trade-offs, and architecture.
- Original content only.
