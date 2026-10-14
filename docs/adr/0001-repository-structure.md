# ADR-0001: Content as files, code as tested Maven modules, one Spring Boot app

Status: Accepted (2026-10-14) — revisit if lessons need interactive code execution in the browser.

## Context

The course needs ~100 lessons whose code must compile and pass tests, a web UI with progress, search and an AI
tutor, and an authoring workflow that does not require code changes to add a lesson.

## Options

1. Lessons stored in a database, edited through an admin UI.
2. Markdown files in the repository, code duplicated inside the Markdown.
3. Markdown files plus one Maven module per phase; lessons *include* code regions from the modules.

## Decision

Option 3. `ContentLoader` reads `content/` at startup and expands `<!-- code: path#region -->` includes from
`lessons/` and `app/`. One module per phase keeps the build small (17 lesson modules) while every lesson's code is tested.

## Consequences

- Lesson text always shows code that compiles and passes tests; broken references fail `ContentLoaderTest`.
- Adding a lesson is adding a file. Git provides history and review.
- The running app needs the repository files (`COURSE_ROOT`), so the container image copies `content/` and `lessons/`.
- No in-app editing (acceptable for a single learner).
