---
title: Permission-Aware RAG over Liferay DXP
summary: Ingest Liferay web content through the Headless Delivery API, keep it in sync, and guarantee that answers only use content the asking user is allowed to read.
minutes: 60
prereqs: [p09-rag/05-advanced-rag]
tags: [rag, liferay, security, permissions, case-study]
quiz:
  - q: Why does the permission filter call Liferay with the end user's token rather than the service account's?
    options:
      - It is faster
      - Liferay then applies its own permission model (roles, site membership, workflow status) for that user, so the result reflects exactly what they may see
      - Service accounts cannot call the API
      - Tokens are cheaper
    answer: 1
    why: Re-implementing Liferay's permission logic in the AI service would drift; ask the system of record.
  - q: Which Liferay API family is designed for reading published content from external applications?
    options: [JSON web services only, Headless Delivery (/o/headless-delivery/v1.0/...), The OSGi console, Portlet URLs]
    answer: 1
    why: Headless Delivery exposes structured contents, documents, blogs, knowledge base articles, navigation and more as REST/GraphQL.
  - q: How should the AI service learn about content changes for incremental re-indexing?
    options:
      - Re-crawl everything every hour
      - React to change events (e.g. a Liferay model listener or object action publishing to a queue) or poll with dateModified filters
      - Never update
      - Ask users to report changes
    answer: 1
    why: Event-driven or incremental sync keeps the index fresh and cheap; full re-crawls are a fallback.
interview:
  - q: Design an AI assistant for a Liferay intranet with 40 sites and strict confidentiality rules.
    a: |
      Keep Liferay as the system of record. A separate Spring Boot AI service ingests published content via Headless Delivery using a read-only service account (OAuth2 client credentials), chunking with contextual headers and storing site/group id, content id, URL, locale, dateModified and visibility metadata in a vector store — Elasticsearch/OpenSearch if Liferay already uses it, enabling hybrid search. Changes arrive via Liferay model listeners or object actions publishing to Kafka, with a nightly reconciliation job. At query time the user's Liferay identity (OAuth2 token from SSO) drives a pre-filter on the sites they belong to, and a post-retrieval check calls Liferay as the user for the final candidates. Answers cite Liferay URLs; prompts and answers are logged without content where policy requires; the UI is a Liferay fragment or React client extension calling the AI service. Evaluate per site with real questions and red-team for cross-site leakage.
    level: architect
---

## Why it matters

Liferay DXP portals hold exactly the content enterprise assistants need — policies, procedures, product documentation, knowledge bases — under a detailed permission model. A RAG system that ignores those permissions is a data-leak machine. This lesson builds ingestion and permission-aware retrieval against Liferay's real API shapes.

## Concept

```mermaid
flowchart LR
    subgraph Liferay["Liferay DXP"]
        WC[Web content / structured contents]
        DM[Documents & Media]
        KB[Knowledge Base]
        ML["model listener / object action"]
    end
    subgraph AI["Spring Boot AI service"]
        ING["ingestion (service account, read-only)"]
        IDX[(vector + keyword index: siteId, contentId, url, locale)]
        RET["retrieve with site pre-filter"]
        PERM["post-filter: GET as the user → 200?"]
        GEN["answer with Liferay URLs as citations"]
    end
    WC & DM & KB -->|"Headless Delivery API"| ING
    ML -->|"change events (Kafka)"| ING
    ING --> IDX
    IDX --> RET --> PERM --> GEN
    PERM -->|"user's OAuth2 token"| Liferay
```

**Two-layer permission enforcement:**

1. **Pre-filter (coarse, fast):** restrict retrieval to sites/groups the user belongs to, using metadata stored at ingestion.
2. **Post-filter (exact):** for the final top candidates, ask Liferay — with the user's token — whether the user can read each source item. Liferay evaluates roles, permissions and workflow status; 403/404 means drop it.

## Build it

Ingestion pages through `/o/headless-delivery/v1.0/sites/{siteId}/structured-contents`, strips HTML from content fields, and produces chunked Spring AI `Document`s with Liferay metadata:

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/LiferayContent.java#ingest -->

The permission check is a `DocumentPostProcessor`, so it plugs straight into the `RetrievalAugmentationAdvisor` from lesson 4:

<!-- code: lessons/p09-rag/src/main/java/dev/aieng/p09/LiferayContent.java#permissions -->

The test uses Spring's `MockRestServiceServer` to simulate Liferay: two pages of structured contents (a VPN article and a confidential HR article), then a user who gets `200` for the first and `403` for the second. Only the VPN article survives the filter. No Liferay instance is needed to run it.

## Wiring it into a Spring Boot service

```java
// Spring Security's OAuth2 client support adds the service account's client-credentials token to each call
@Bean
RestClient liferayServiceClient(RestClient.Builder builder, OAuth2AuthorizedClientManager clients,
                                LiferayProperties props) {
    var oauth = new OAuth2ClientHttpRequestInterceptor(clients);
    oauth.setClientRegistrationIdResolver(request -> "liferay-ingestion");   // registration in application.yml
    return builder.baseUrl(props.url()).requestInterceptor(oauth).build();
}

// Per request: a RestClient carrying the end user's bearer token (from SSO / Liferay OAuth2)
DocumentPostProcessor permissions = LiferayContent.permissionFilter(
        restClientBuilder.baseUrl(props.url()).defaultHeader("Authorization", "Bearer " + userToken).build());
```

Front-end options on the Liferay side: a **React client extension** or a **fragment** that calls the AI service, or a Liferay **Object** action that triggers AI enrichment on save.

## Keeping the index fresh

| Approach | How | Notes |
|---|---|---|
| Event-driven | Model listener / object action → Kafka → ingestion consumer | Near-real-time; needs a Liferay module or client extension |
| Incremental poll | `filter=dateModified gt <last run>` on Headless Delivery | Simple; minutes of lag |
| Reconciliation | Nightly full id comparison | Catches missed events and deletions |

Unpublished, expired or deleted content must be **removed** from the index — use the deterministic chunk ids from lesson 2.

## Production notes

- **Least privilege:** the ingestion account reads only the sites in scope; it never writes.
- **Locales:** store `locale` metadata and retrieve in the user's language (Liferay content is often multilingual).
- **Cite Liferay URLs** so users land on the authoritative page, with its own permission check.
- **Red-team cross-site leakage** with users from different sites before go-live.
- **Performance:** batch the post-filter checks (e.g. one search call restricted to candidate ids) and cache per request.

## Exercises

1. **Easy** — Extend `fetchSite` to also store `taxonomyCategoryBriefs` names as metadata for filtering.
2. **Medium** — Add ingestion of Knowledge Base articles (`/o/headless-delivery/v1.0/sites/{siteId}/knowledge-base-articles`) with a mocked test.
3. **Hard** — Replace the per-document permission check with a single batched call: query Liferay's search API as the user, restricted to the candidate content ids, and keep only ids it returns. Compare latency in a test with 20 candidates.

## Common mistakes

- Indexing with an admin account and filtering nothing.
- Leaving unpublished or expired content in the index.
- Caching answers across users with different permissions.

## Further reading

- Liferay Headless Delivery API: <https://learn.liferay.com/w/dxp/headless-delivery>
- Liferay OAuth 2.0: <https://learn.liferay.com/w/dxp/security-and-administration/security/oauth-2>
- Liferay client extensions: <https://learn.liferay.com/w/dxp/liferay-development/customizing-liferays-look-and-feel/client-extensions>
