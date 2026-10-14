---
title: "Case Study: AI Search and Answers for a Liferay DXP Portal"
summary: Add semantic search, generated answers with citations and AI-assisted authoring to an existing multi-site Liferay DXP portal without compromising permissions or editorial control.
minutes: 55
prereqs: [p16-architecture/06-case-document-processing]
tags: [case-study, liferay, search, system-design]
quiz:
  - q: Why build the AI capabilities as a separate Spring Boot service rather than inside Liferay's OSGi container?
    options:
      - Liferay cannot call HTTP services
      - Independent scaling, deployment cadence and dependencies (Spring AI, model clients), while Liferay integrates via client extensions and headless APIs
      - OSGi forbids Java 21
      - It is always cheaper
    answer: 1
    why: Keeps the portal stable and upgradeable; the AI service evolves independently.
  - q: How should AI-generated content enter the portal?
    options:
      - Published directly by the AI
      - As drafts that go through Liferay's workflow and editorial review
      - Through database inserts
      - Via email
    answer: 1
    why: Editorial control and audit stay with the portal's existing workflow.
  - q: Which Liferay capability provides the BM25 half of hybrid search?
    options: [The document library, Its Elasticsearch/OpenSearch-based search infrastructure, The message boards, Workflow]
    answer: 1
    why: Reuse the existing index and permission fields where possible.
interview:
  - q: A customer asks to add "ChatGPT-like answers" to their Liferay DXP portal. Describe your solution.
    a: |
      Clarify sites, audiences (anonymous vs authenticated), languages and content types. Architecture: an AI service (Spring Boot + Spring AI) behind the portal; ingestion from Liferay Headless Delivery on publish events (object actions/model listeners → Kafka or webhooks) into a hybrid index with site, group, role-visibility and locale metadata; for anonymous users only guest-viewable content is indexed or retrievable. The portal integrates via a React client extension for the answer widget on the search page, passing the user's OAuth2 token; the service applies site/permission filters and re-checks sensitive items as the user. Answers cite portal URLs, show "AI-generated" disclosure, and fall back to classic results. Authoring assistance (summaries, SEO metadata, translations, alt text) produces drafts that go through Liferay workflow. Evals per site, analytics on deflection and clicks, and governance for content owners complete it.
    level: architect
---

## Problem

A manufacturer runs **Liferay DXP** with 25 sites: a public website (anonymous), a partner portal (authenticated partners, per-partner content) and an employee intranet. Search is keyword-based; users struggle to find answers across product documentation, FAQs and knowledge-base articles in 8 languages. Editors want help with summaries, SEO metadata and translations.

## Goals

- Semantic and hybrid search with **generated answers and citations** on each site.
- **No leakage** across sites, partner accounts or visibility levels — including anonymous vs authenticated.
- **AI-assisted authoring** inside existing editorial workflows.
- Measurable search success (fewer zero-result and reformulated searches, higher click-through).

## Architecture

```mermaid
flowchart LR
    subgraph Liferay["Liferay DXP"]
        CONTENT[web content, documents, KB, objects]
        WF[workflow: review & publish]
        CX["React client extension: answer widget + authoring panel"]
        OA["object actions / model listeners"]
        SEARCH_L[(Liferay search index: OpenSearch)]
    end
    OA -->|content.published / deleted| K[(Kafka or webhook)]
    K --> ING["ai-ingestion: Headless Delivery fetch, chunk, embed, metadata (site, groups, roles, locale, guest-visible)"]
    ING --> IDX[("hybrid index: BM25 + kNN")]
    CX -->|"user OAuth2 token"| AISVC["ai-service (Spring Boot + Spring AI)"]
    AISVC --> IDX
    AISVC -->|"re-check sensitive items as user"| CONTENT
    AISVC --> GW[model gateway]
    CX -->|"authoring: summarise / SEO / translate"| AISVC
    AISVC -->|drafts via Headless Admin API| WF
```

## Key design points

| Topic | Decision | Course link |
|---|---|---|
| Integration | Client extensions (no core customisation); AI service outside the portal | Phase 9 L6 |
| Identity | Liferay OAuth2 / SSO token passed to the AI service; anonymous requests limited to guest-viewable content | Phase 11 L4 |
| Permissions | Site + group + visibility metadata pre-filter; as-user re-check for partner-restricted content | Phases 8 L5, 9 L6 |
| Hybrid search | OpenSearch BM25 + kNN with RRF; product codes favour BM25 | Phase 8 L4 |
| Multilingual | Multilingual embeddings; answer in page locale; locale filter with fallback to default language | Phase 9 L5 |
| Authoring | AI suggestions saved as drafts; Liferay workflow approval; "AI-assisted" flag | Phase 15 L5 |
| Agents/MCP | Optional Liferay MCP server for internal tools (IDE, chat apps) | Phase 11 |

## Evaluation and analytics

- Offline: per-site eval sets (answerable, unanswerable, permission traps), recall@k, faithfulness, refusal accuracy.
- Online: search success rate, zero-result rate, answer click-through to cited pages, thumbs, deflected support tickets (partner portal).
- Editors: acceptance rate of AI drafts, edit distance, time to publish.

## Risks

| Risk | Mitigation |
|---|---|
| Partner A sees partner B's pricing | Group-level filters + as-user checks; cross-partner red-team tests |
| Answers from outdated documentation | Version/expiry metadata; prefer latest product version; freshness monitoring |
| Brand and legal risk on public site | Conservative prompts, citations required, output guard, disclosure, refusal for off-topic |
| Upgrade friction | No core changes; client extensions and headless APIs only |

## Exercises

1. **Easy** — Define the metadata schema for each indexed chunk in this design.
2. **Medium** — Sketch the React client extension's API contract with the AI service (request, response with citations, error and fallback states).
3. **Hard** — Design the per-site eval set and red-team plan for the partner portal, including how test partner accounts are provisioned.

## Further reading

- Liferay client extensions, Headless Delivery and Headless Admin API documentation (learn.liferay.com).
- Phases 8, 9, 11 and 15 of this course.
