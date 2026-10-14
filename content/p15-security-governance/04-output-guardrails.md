---
title: Output Guardrails
summary: Validate what the model produces before anyone acts on it — leakage canaries, PII, link allow-lists, citation checks, schema validation and safe rendering.
minutes: 35
prereqs: [p15-security-governance/03-personal-data-protection]
tags: [security, guardrails, output-handling]
quiz:
  - q: What is a canary token in a system prompt for?
    options:
      - Tracking costs
      - Detecting system-prompt leakage — if the unique string appears in output, the prompt was disclosed
      - Improving accuracy
      - Rate limiting
    answer: 1
    why: Cheap, precise leakage detection.
  - q: The model's answer contains a link to an unknown domain. What should happen?
    options:
      - Render it
      - Block or strip it per policy — unapproved links are exfiltration and phishing channels
      - Shorten it
      - Translate it
    answer: 1
    why: Allow-list approved domains for links and images in rendered output.
  - q: Why render model output as escaped Markdown rather than raw HTML?
    options:
      - It looks nicer
      - Model output is untrusted; raw HTML enables cross-site scripting
      - Browsers require it
      - It is faster
    answer: 1
    why: Improper output handling (OWASP LLM05). The course app renders with escapeHtml and sanitizeUrls.
interview:
  - q: What happens when an output guard fails in production?
    a: |
      It depends on the violation and the context, decided in advance. Leakage of system prompts or secrets: block, alert security, log the conversation id. PII in output that the user did not provide: redact or block and alert. Unapproved links: strip them and continue. Invalid citations or schema: regenerate once with feedback, then fall back to a safe response (sources only, or "I can't answer that reliably"). Harmful content per policy: refuse with a standard message. Every violation is a metric with alerting thresholds, and samples go to human review and into the eval/red-team datasets.
    level: senior
---

## Why it matters

Input defences fail sometimes. Output guards are the last deterministic checkpoint before text reaches a browser, a database or another system — and they catch the *results* of attacks you did not anticipate.

## Concept

```mermaid
flowchart LR
    MODEL[model output] --> G{OutputGuard}
    G -->|canary found| BLOCK[block + alert]
    G -->|PII| REDACT[redact / block]
    G -->|unapproved link| STRIP[strip link]
    G -->|bad citation / schema| REGEN[regenerate once → safe fallback]
    G -->|clean| RENDER["render as escaped Markdown"]
```

| Check | Catches |
|---|---|
| Canary token | System prompt leakage |
| PII detector | Disclosure of personal data |
| Link/image allow-list | Exfiltration via rendered URLs, phishing |
| Citation range | Fabricated sources |
| Schema/business validation | Malformed or unsafe structured output (Phase 6) |
| Safe rendering | XSS via HTML in output |
| Content policy classifier | Harmful or off-brand content |

## Build it from scratch

<!-- code: lessons/p15-security-governance/src/main/java/dev/aieng/p15/OutputGuard.java#check -->

The test passes a clean answer with an approved intranet link and valid citation, then a malicious one containing the canary, a Markdown image to `evil.example` with a query string, an email address and an out-of-range citation `[3]` — all four violations are reported. Subdomains of approved domains are allowed.

## Now with the library

- Implement guards as a Spring AI **advisor** that inspects `ChatClientResponse` (for non-streaming) or the aggregated text (for streaming).
- The course app already applies safe rendering: `AiService.toHtml` uses CommonMark with `escapeHtml(true)` and `sanitizeUrls(true)`, and citation links are generated only for valid source numbers.
- Add `Content-Security-Policy` headers (`img-src 'self'`) to the UI so a missed image link still cannot load.

## Production notes

- **Decide actions per violation type** before launch (block, strip, regenerate, escalate).
- **Measure guard trigger rates;** sudden increases indicate attacks or model changes.
- **Streaming:** either guard aggregated output before showing it, or stream with a final guard that can retract and replace the message.

## Exercises

1. **Easy** — Add a check that rejects outputs longer than a configured maximum.
2. **Medium** — Implement a `GuardAdvisor` for `ChatClient` that regenerates once on citation violations, using a fake model in tests.
3. **Hard** — Add a CSP header to the course app via a Spring `OncePerRequestFilter` that allows only the CDNs it uses, and verify in a MockMvc test.

## Common mistakes

- Rendering model HTML directly.
- Guarding input but not output.
- No defined action when a guard fires.

## Further reading

- OWASP LLM05 Improper Output Handling.
- Johann Rehberger, research on data exfiltration via Markdown images in LLM apps (2023–2024).
