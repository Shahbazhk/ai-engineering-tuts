---
title: Governance — EU AI Act, ISO/IEC 42001 and NIST AI RMF
summary: The frameworks that shape enterprise AI programmes, what they demand from engineering, and a risk-triage tool for design reviews.
minutes: 45
prereqs: [p15-security-governance/04-output-guardrails]
tags: [governance, eu-ai-act, iso-42001, nist, compliance]
quiz:
  - q: Under the EU AI Act, AI used to screen job applications is typically…
    options: [Prohibited, High-risk, Limited risk, Out of scope]
    answer: 1
    why: Employment and worker management are listed high-risk areas (Annex III), with obligations such as risk management, data governance, logging and human oversight.
  - q: What is ISO/IEC 42001?
    options:
      - A programming standard
      - A certifiable management-system standard for AI (an "AI management system"), analogous to ISO 27001 for information security
      - A model benchmark
      - An EU regulation
    answer: 1
    why: It defines organisational processes — policies, risk assessment, controls, monitoring, improvement — for responsible AI.
  - q: What are the four functions of the NIST AI Risk Management Framework?
    options:
      - Plan, Do, Check, Act
      - Govern, Map, Measure, Manage
      - Identify, Protect, Detect, Respond
      - Design, Build, Test, Deploy
    answer: 1
    why: GOVERN, MAP, MEASURE, MANAGE.
interview:
  - q: How would you set up AI governance in a company that already has ISO 27001 and an Agile delivery model?
    a: |
      Extend existing structures rather than build a parallel bureaucracy. Create an AI policy and an inventory of AI systems with owners. Add an AI risk assessment step to the existing design/architecture review (risk tier per the EU AI Act, data protection, security, fairness, impact) with a lightweight path for minimal-risk internal tools and a deeper path for high-risk ones. Map controls to ISO/IEC 42001 Annex A alongside ISO 27001 controls; reuse the ISMS document control, audits and management review. In delivery, encode governance into the Definition of Done: eval thresholds, red-team results, model/prompt versioning, audit logging, human-oversight design and user transparency. Monitor in production and review incidents. Train staff (AI literacy is itself an AI Act obligation).
    level: architect
---

## Why it matters

Regulation and standards now shape what you may build and how you must build it. For an architect, governance is not paperwork: it translates into concrete engineering requirements — logging, documentation, human oversight, evaluation, transparency.

## Concept

### EU AI Act (risk-based)

| Tier | Examples | Engineering consequences |
|---|---|---|
| Prohibited | Social scoring, manipulative techniques, emotion recognition at work or school, untargeted facial-image scraping | Don't build |
| High-risk | Employment, credit scoring, access to essential services, education assessment, critical infrastructure (Annex III), safety components | Risk management, data governance, technical documentation, automatic logging, transparency, human oversight, accuracy/robustness/cybersecurity, conformity assessment |
| Limited risk (transparency) | Chatbots, AI-generated content | Disclose AI interaction; mark synthetic content |
| Minimal risk | Spam filters, internal productivity aids | Voluntary codes; AI literacy still applies |

General-purpose AI model providers have separate obligations; most enterprises are **deployers** (and sometimes providers of the systems they build on top).

### ISO/IEC 42001

An auditable **AI management system**: AI policy, roles, risk and impact assessment, controls (Annex A), lifecycle processes, monitoring and continual improvement — designed to sit next to ISO 27001.

### NIST AI Risk Management Framework

```mermaid
flowchart LR
    GOV["GOVERN — culture, policies, roles, accountability"] --> MAP["MAP — context, use case, impacts, risks"]
    MAP --> MEA["MEASURE — evaluate, test, track risks (evals, red-teaming)"]
    MEA --> MAN["MANAGE — prioritise, mitigate, monitor, respond"]
    MAN --> GOV
```

Plus the Generative AI Profile (NIST AI 600-1) listing GenAI-specific risks.

## Build it from scratch

A design-review triage helper — a structured first pass, **not legal advice**:

<!-- code: lessons/p15-security-governance/src/main/java/dev/aieng/p15/AiActTriage.java#triage -->

The tests classify emotion recognition at work as prohibited, CV screening as high-risk with human-oversight obligations, an intranet chatbot as limited-risk (transparency), and an internal productivity tool as minimal-risk.

## Mapping obligations to what you built

| Obligation | Engineering artefact in this course |
|---|---|
| Record-keeping / logging | Tamper-evident `AuditLog` (next lesson), observability (Phase 14) |
| Accuracy, robustness | Eval suites, CI gates (Phase 12), resilience (Phase 14) |
| Human oversight | Approvals, pending actions, staged autonomy (Phase 10) |
| Data governance | Dataset provenance, PII handling (Phases 9, 13, 15) |
| Transparency | Citations, AI disclosure in the UI, model/prompt versioning (Phases 7, 9) |
| Cybersecurity | OWASP LLM controls (this phase) |

## Production notes

- **Inventory first:** you cannot govern AI systems you don't know exist.
- **Proportionality:** a lightweight path for minimal-risk tools keeps teams on the approved path.
- **Track dates:** the AI Act's obligations phase in over several years — check the current timeline with legal for each system.

## Exercises

1. **Easy** — Triage five AI use cases from your organisation with `AiActTriage` and note which need legal review.
2. **Medium** — Write a one-page AI policy outline: scope, roles, approval workflow by tier, required artefacts.
3. **Hard** — Map ISO/IEC 42001 Annex A control areas onto your existing ISO 27001 controls and delivery process, identifying gaps.

## Common mistakes

- Treating governance as a one-time approval.
- Applying high-risk processes to every chatbot (or none to real high-risk systems).
- Forgetting the deployer's obligations when buying an AI product.

## Further reading

- Regulation (EU) 2024/1689 (AI Act), especially Articles 5, 6, 9–15, 26, 50 and Annex III.
- ISO/IEC 42001:2023.
- NIST AI RMF 1.0 and NIST AI 600-1 (Generative AI Profile).
