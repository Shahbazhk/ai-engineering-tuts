# AI Engineering From Scratch — Java & Spring Edition

A self-paced course and web application for experienced Java/Spring engineers who want to become hands-on
**AI engineers and AI solution architects**. Every major idea is built three times:

1. **From scratch in plain Java** — autograd, a BPE tokenizer, attention and a GPT forward pass, IVF and HNSW
   indexes, a RAG pipeline, a ReAct agent, an MCP server, LoRA, quantisation, evals.
2. **With the library** — Spring AI 2.0, LangChain4j, DJL/ONNX Runtime concepts.
3. **In production** — cost, latency, resilience, observability, security and governance.

## What's inside

| | |
|---|---|
| **19 phases, 114 lessons** | `content/pNN-*/` — Markdown with YAML front matter (quiz, interview Q&A) |
| **17 code modules + app, 137 tests** | `lessons/pNN-*/` — runnable Java 21 code that lessons include directly, so text and code never drift |
| **Course web app** | `app/` — Spring Boot 4 + Spring AI 2 + Thymeleaf/HTMX |

### Curriculum

| # | Phase | # | Phase |
|---|---|---|---|
| 0 | Setup & the Java AI toolchain | 10 | Agents & tool use |
| 1 | Math for AI, in Java | 11 | Model Context Protocol (MCP) |
| 2 | Classical machine learning | 12 | Evaluation & testing of AI systems |
| 3 | Neural networks from scratch | 13 | Fine-tuning & model customisation |
| 4 | NLP, tokenization & embeddings | 14 | Production AI engineering (LLMOps) |
| 5 | Transformers from scratch | 15 | AI security, safety & governance |
| 6 | LLMs in practice (Spring AI, LangChain4j) | 16 | AI architecture & system design (5 case studies) |
| 7 | Prompt & context engineering | 17 | AI product & delivery leadership |
| 8 | Vector databases & search | 18 | Capstone projects |
| 9 | Retrieval-augmented generation (incl. Liferay DXP) | | |

### App features

- Roadmap with progress, phase pages, lessons with syntax highlighting, KaTeX math and Mermaid diagrams.
- Quizzes with stored scores; "mark complete" progress tracking (H2 file database).
- **Search** in three modes: keyword (BM25), semantic (embeddings) and hybrid (Reciprocal Rank Fusion).
- **AI Tutor**: RAG over the course with citation links — and a sources-only answer when no model is reachable.
- **Playground**: prompt lab (compare two models), embedding similarity matrix, RAG pipeline inspector.
- Interview question bank (filter by phase and level), architecture case studies, glossary.
- Dark/light mode, responsive layout.

## Run it

Requirements: JDK 21+ (25 tested). Maven is not needed — use the wrapper.

```bash
./mvnw test                          # builds everything and runs all lesson + app tests (no model needed)
./mvnw -pl app spring-boot:run       # http://localhost:8080
```

For the AI Tutor, semantic search and playground, run [Ollama](https://ollama.com) locally:

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

Or run the whole stack (app + Ollama with models + PostgreSQL/pgvector) with Docker:

```bash
docker compose up -d                 # first start downloads ~2.5 GB of models
```

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `AI_CHAT_PROVIDER` / `AI_EMBEDDING_PROVIDER` | `ollama` | `ollama`, `openai` (any OpenAI-compatible API) or `none` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `OLLAMA_CHAT_MODEL` / `OLLAMA_EMBEDDING_MODEL` | `llama3.2` / `nomic-embed-text` | Models |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_CHAT_MODEL` | — | Hosted provider settings |
| `COURSE_QUERY_PREFIX` / `COURSE_DOCUMENT_PREFIX` | `search_query: ` / `search_document: ` | Embedding instruction prefixes (set both to empty for OpenAI models) |
| `COURSE_ROOT` | auto-detect | Directory containing `content/` and `lessons/` |
| `COURSE_DB_URL` | H2 file `./data/course` | Progress database (PostgreSQL works too) |

## Repository layout

```text
pom.xml                    parent: Spring Boot 4.1, Spring AI 2.0, LangChain4j BOMs, Java 21
app/                       course web application
  src/main/java/dev/aieng/course/
    content/               ContentLoader (Markdown + YAML + code includes), Markdown rendering
    search/                Bm25Index, SemanticIndex, SearchService (hybrid RRF)
    ai/                    AiService: tutor (RAG + citations), playground
    progress/              ProgressService (JdbcClient)
    web/                   controllers
  src/test/...             content integrity, web, AI pipeline and retrieval-eval tests
content/pNN-*/             lessons (Markdown) + phase.yml; content/glossary.yml
lessons/pNN-*/             one Maven module per phase with code and tests
lessons/p13-fine-tuning/python/   the only Python: LoRA training script + Ollama Modelfile (not run by the build)
docs/adr/                  architecture decision records for this repository
```

### Writing lessons

A lesson is `content/pNN-phase/MM-slug.md` with front matter (`title`, `summary`, `minutes`, `prereqs`, `tags`,
`quiz`, `interview`). Include code that the build compiles and tests with:

```markdown
<!-- code: lessons/p08-vector-search/src/main/java/dev/aieng/p08/Hnsw.java#search -->
```

`#search` selects the lines between `// region search` and `// endregion`. Broken includes, unknown prerequisites,
malformed quizzes or glossary references fail `ContentLoaderTest`, so the build guards the content.

## Honest notes

- Lesson tests run offline with fakes; interactive features need a model. Answers from small local models are
  noticeably weaker than frontier models — switch providers by configuration.
- Library snippets marked "reference snippet" (DJL, ONNX Runtime, Tribuo, Gatling, Testcontainers, Liferay OAuth)
  are not compiled by the build because they need large native dependencies or external services.
- Phase 5's GPT is a complete forward pass with a KV cache, but trained weights would require backward passes for
  every transformer operation — that is a guided exercise.
- Capstones (Phase 18) are briefs with acceptance criteria; you build them.
- Prices and version numbers in lessons are illustrative and dated; check current provider pages.
