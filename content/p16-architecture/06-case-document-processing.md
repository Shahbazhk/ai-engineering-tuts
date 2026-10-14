---
title: "Case Study: Intelligent Document Processing"
summary: Extract structured data from 50,000 invoices and contracts a day — classification, extraction with schemas, validation, human review queues and straight-through processing.
minutes: 50
prereqs: [p16-architecture/05-case-support-copilot]
tags: [case-study, extraction, batch, system-design]
quiz:
  - q: What determines whether a document goes straight through or to human review?
    options:
      - Random sampling only
      - Validation results and confidence — schema checks, business rules (totals, VAT ids, dates) and agreement with reference data
      - The file size
      - The model name
    answer: 1
    why: Straight-through processing must be earned per field by deterministic checks.
  - q: Why is this mostly a batch workload?
    options:
      - LLMs cannot run online
      - Documents arrive in volumes without a user waiting; batch processing allows rate-limited throughput, retries and cheaper batch APIs
      - Batch is always faster
      - Invoices are small
    answer: 1
    why: Design for throughput, idempotency and backlog, not interactive latency.
  - q: What is the most important metric to report per field?
    options: [Average document length, Field-level accuracy and the straight-through rate at a target accuracy, Token count only, Number of retries]
    answer: 1
    why: Business value comes from documents processed without humans at an accuracy the business accepts.
interview:
  - q: Design a document-processing pipeline that a finance department will trust.
    a: |
      Ingest documents into object storage with ids and checksums; classify document type; extract text and layout (OCR where needed); extract fields with schema-constrained structured output per type at temperature 0; validate deterministically (schema, totals = sum of lines, tax rules, VAT/IBAN checksums, supplier exists in ERP, PO matches); compute per-field confidence from validation and model agreement; route documents with all critical fields validated to straight-through posting and others to a review UI that highlights uncertain fields with source snippets; capture reviewer corrections as training/eval data. Run as Spring Batch or Kafka-driven workers with idempotency, retries, DLQ and per-step metrics. Report field accuracy, straight-through rate, review time and cost per document; audit every posted value back to source and model version.
    level: architect
---

## Problem

A shared-services centre receives **50,000 documents per day** — supplier invoices (70%), delivery notes and contracts — as PDFs and scans in six languages. Manual entry costs minutes per document. Goal: process at least 60% straight through with ≥ 99% accuracy on critical fields, and make review fast for the rest.

## Architecture

```mermaid
flowchart LR
    IN[email / upload / EDI gateway] --> S3[(object storage + checksum)]
    S3 --> Q[(Kafka: document.received)]
    Q --> CLS["classify type (small model / classifier)"]
    CLS --> OCR["text + layout extraction (OCR when scanned)"]
    OCR --> EXT["field extraction: structured output per type, temperature 0"]
    EXT --> VAL["validation: schema, totals, VAT/IBAN checksums, ERP lookups, PO match"]
    VAL -->|all critical fields valid| STP[post to ERP]
    VAL -->|otherwise| REV["review UI: uncertain fields highlighted with source snippets"]
    REV --> STP
    REV -->|corrections| DATA[(eval + fine-tune data)]
```

## Design highlights

| Concern | Approach | Course link |
|---|---|---|
| Extraction | `entity(Invoice.class)` with enums and nested `LineItem` lists | Phase 6 L5 |
| Validation | Deterministic business rules; IBAN mod-97, totals, ERP master data | Phase 15 L3 |
| Throughput | Spring Batch / Kafka consumers sized to provider TPM; batch APIs for non-urgent docs | Phase 14 L3/L6 |
| Cost | Small model for classification; larger model only for complex contracts | Phase 14 L1 |
| Learning | Reviewer corrections → eval set → fine-tuned extractor | Phases 12, 13 |
| Audit | Every posted value linked to document, page, model and prompt version | Phase 15 L6 |

## Capacity

50,000 docs/day ≈ 0.6 docs/second average; with ~3,000 input and 500 output tokens per document, 150M input + 25M output tokens/day. Processing over 16 hours needs ~2,600 input tokens/second — within typical enterprise rate limits but requires a quota increase and back-pressure.

## Risks

| Risk | Mitigation |
|---|---|
| Plausible but wrong numbers | Cross-field validation, ERP matching, no posting on failed checks |
| Poor scans | OCR confidence thresholds → review |
| Injection in documents ("approve this invoice") | Documents are data; extraction has no tools; posting rules are code |
| Vendor format drift | Per-supplier accuracy monitoring and alerts |

## Exercises

1. **Easy** — Write the `Invoice` and `LineItem` records with enums and validation annotations.
2. **Medium** — Implement the totals and IBAN checks as a validator and test with five crafted invoices.
3. **Hard** — Design the review UI data model and the metric that decides when a supplier's documents can move to straight-through processing.

## Further reading

- Spring Batch reference documentation (chunk-oriented processing, skip and retry).
