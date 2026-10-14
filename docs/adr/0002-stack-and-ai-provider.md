# ADR-0002: Spring Boot 4 + Spring AI 2, Ollama by default, graceful offline mode

Status: Accepted (2026-10-14) — revisit when Spring AI or Boot major versions change.

## Context

The learner is a senior Spring engineer; the app should dogfood the course's own subject, run free and offline,
and still allow hosted models.

## Decision

- Spring Boot 4.1 + Spring AI 2.0 (current releases), Java 21 bytecode, Thymeleaf + HTMX instead of a SPA.
- Provider selected with `spring.ai.model.chat` / `spring.ai.model.embedding`: `ollama` (default), `openai`
  (any OpenAI-compatible endpoint) or `none`.
- All AI features degrade gracefully: search falls back to BM25, the tutor returns retrieved sources, the
  similarity playground uses an offline hashed bag-of-words.
- Progress in H2 (file) by default; PostgreSQL supported through `COURSE_DB_URL`.
- Semantic index is in-memory brute force (≈1k sections; exact and sub-millisecond).

## Consequences

- No API key or GPU needed to learn; quality of generated answers depends on the local model.
- In-memory embeddings are recomputed on startup (seconds to minutes with Ollama).
