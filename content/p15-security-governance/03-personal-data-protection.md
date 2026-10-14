---
title: Personal Data — Redaction, Pseudonymisation and GDPR
summary: Keep personal data out of prompts, logs, caches and training sets — reversible pseudonymisation with checksum-validated detection, and the data-protection questions every AI design must answer.
minutes: 40
prereqs: [p15-security-governance/02-prompt-injection]
tags: [security, privacy, gdpr, pii]
quiz:
  - q: Why use checksums (Luhn, IBAN mod-97) in PII detection?
    options:
      - They encrypt the data
      - They cut false positives — order numbers and timestamps look like card numbers but fail the checksum
      - They are required by GDPR
      - They speed up regexes
    answer: 1
    why: The test keeps "Order 1234567890123" intact because it fails Luhn.
  - q: What does reversible pseudonymisation allow?
    options:
      - Sending real data to the model
      - The model works with tokens like [EMAIL_1]; the application restores real values in the answer for the same user
      - Deleting data automatically
      - Training on personal data freely
    answer: 1
    why: The provider never sees the values; the user still gets a useful answer.
  - q: Which question must be answered before sending customer data to a hosted model?
    options:
      - Which font to use
      - The legal basis and purpose, the provider's data processing terms (retention, training use, location), and whether a DPIA is needed
      - The model's parameter count
      - The temperature
    answer: 1
    why: Data protection obligations apply to AI processing like any other processing.
interview:
  - q: A business unit wants to summarise customer emails with a hosted LLM. What do you require?
    a: |
      A data protection review first: purpose and legal basis, data categories (special categories?), data minimisation, a data processing agreement with the provider covering retention, no training on our data, sub-processors and data location (EU region if required), and a DPIA if the processing is high-risk. Technically: pseudonymise identifiers before sending (emails, phone numbers, account and payment data) with reversible tokens kept in our system; send only the needed text; disable or minimise provider-side logging where possible; keep prompts and outputs out of our own logs or apply the same retention and access controls; restrict who can see summaries; and document the processing in the record of processing activities. If requirements can't be met, use a self-hosted model.
    level: architect
---

## Why it matters

Prompts are full of personal data: names, emails, account numbers, health details in support tickets. Every place that text flows — provider, logs, traces, caches, vector stores, fine-tuning sets — becomes a data-protection obligation.

## Concept

```mermaid
flowchart LR
    IN["user text with PII"] --> RED["pseudonymise: email → [EMAIL_1], IBAN → [IBAN_2]"]
    RED --> VAULT[(vault: token → value, per request/user)]
    RED --> LLM[model / provider sees tokens only]
    LLM --> OUT["answer with tokens"]
    OUT --> RESTORE["restore values for the same user"]
    VAULT --> RESTORE
    RESTORE --> USER[user]
```

**Where personal data hides in AI systems:**

| Store | Control |
|---|---|
| Prompts sent to providers | Pseudonymise, minimise, DPA, region |
| Logs and traces | Content capture off, redaction, retention |
| Vector stores | Permission metadata, deletion on request |
| Conversation memory | Retention, user deletion, encryption |
| Caches | Scope per user/tenant, TTL |
| Fine-tuning data | Exclude or pseudonymise (models memorise) |

**Detection:** regexes plus checksums for structured identifiers; named-entity recognition (NER) models for names, addresses and free-form data; dictionaries for internal identifiers (customer numbers).

## Build it from scratch

<!-- code: lessons/p15-security-governance/src/main/java/dev/aieng/p15/PiiRedactor.java#redact -->

<!-- code: lessons/p15-security-governance/src/main/java/dev/aieng/p15/PiiRedactor.java#checksums -->

The test redacts an IBAN, a card number, an email (twice — same token both times) and a phone number, leaves an order number that fails Luhn untouched, and restores the original values in a model answer that refers to `[IBAN_2]` and `[EMAIL_1]`.

Two refinements the tests forced: phone detection originally matched any long digit run — including the order number — so it now requires an international `+` prefix or a national separator. **Precision matters**: over-redaction destroys the information the model needs.

## Now with the library

Wrap redaction as a Spring AI advisor so every call through a `ChatClient` is protected:

```java
class PiiAdvisor implements CallAdvisor {
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        PiiRedactor.Redacted r = PiiRedactor.redact(req.prompt().getUserMessage().getText());
        ChatClientRequest redacted = req.mutate()
                .prompt(req.prompt().augmentUserMessage(r.text())).build();
        ChatClientResponse res = chain.nextCall(redacted);
        // restore r.vault() values in res's text before returning (omitted for brevity)
        return res;
    }
    public String getName() { return "pii"; }
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
```

For names and addresses, add an NER model (e.g. an ONNX token-classification model via DJL) or a dedicated PII service.

## Production notes

- **Data processing agreements** with providers: retention, no training on your data, sub-processors, region.
- **DPIA** for high-risk processing; record the processing activity.
- **Right to erasure** must reach vector stores, memory and caches — design ids so you can find a person's data.
- **Never log the vault.**

## Exercises

1. **Easy** — Add detection for IPv4 addresses with validation of each octet.
2. **Medium** — Make `restore` safe against the model inventing tokens like `[EMAIL_9]` that are not in the vault (leave them visible and flag the answer).
3. **Hard** — Implement the `PiiAdvisor` fully, including restoration in the response, and test it with a fake model that echoes the tokens back.

## Common mistakes

- Redacting prompts but logging them unredacted elsewhere.
- Over-redaction that breaks the task.
- Personal data in fine-tuning sets.

## Further reading

- GDPR Articles 5, 6, 25, 35; EDPB guidance on AI models and data protection.
- Microsoft Presidio (concepts for PII detection pipelines).
