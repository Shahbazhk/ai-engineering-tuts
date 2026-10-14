---
title: Deploying AI Services — Containers, Kubernetes, GPUs and Releases
summary: Package and run Spring AI services and self-hosted models, choose CPU vs GPU, roll out prompt and model changes safely, and manage configuration and secrets.
minutes: 40
prereqs: [p14-llmops/06-streaming-and-scaling]
tags: [llmops, kubernetes, deployment, devops]
quiz:
  - q: Why should model ids, prompt versions and retrieval settings be configuration rather than code constants?
    options:
      - To make the code shorter
      - So they can be changed, canaried and rolled back per environment without rebuilding, with an audit trail
      - Kubernetes requires it
      - For speed
    answer: 1
    why: Most AI behaviour changes are configuration changes; treat them with release discipline.
  - q: What is the main operational difference between hosting the Spring AI app and hosting a 7B model?
    options:
      - None
      - The model needs GPU (or large-memory CPU) nodes, long start-up times for loading weights, and capacity planning around KV-cache memory
      - The app needs GPUs
      - Models cannot run in containers
    answer: 1
    why: Separate them into different deployments with different scaling, node pools and health checks.
  - q: How should you release a new prompt version?
    options:
      - Edit it in production directly
      - Ship it behind configuration, run evals, canary to a share of traffic, compare metrics, then promote or roll back
      - Replace it at midnight
      - Ask users to test it
    answer: 1
    why: Prompts are behaviour; release them like code.
interview:
  - q: Describe a reference deployment for an enterprise RAG assistant on Kubernetes.
    a: |
      Separate deployments: the Spring Boot assistant API (stateless, virtual threads, HPA on in-flight requests), an ingestion worker (Spring Batch or Kafka consumer, scaled on lag), the vector store (managed service or operator-run Elasticsearch/OpenSearch/PostgreSQL with pgvector and backups), and — if self-hosting models — an inference service (vLLM/TGI/Ollama) on a GPU node pool with readiness gated on model load, plus an embedding service. Configuration (model ids, prompt versions, thresholds) in ConfigMaps or a config server with GitOps; secrets (API keys, OAuth clients) in a secret manager via CSI or External Secrets. Network policies restrict egress to approved model endpoints. OpenTelemetry collector for traces/metrics. Releases via GitOps with canaries and eval gates; disaster recovery covers the vector index rebuild path from source systems.
    level: architect
---

## Why it matters

AI services add new deployable parts — model servers, embedding services, vector stores, ingestion workers — and new kinds of change: prompts, models and thresholds. A clean deployment model keeps them independently scalable and safely releasable.

## Concept

```mermaid
flowchart TB
    subgraph K8s["Kubernetes cluster"]
        APP["assistant-api (Spring Boot, CPU pool, HPA on in-flight requests)"]
        ING["ingestion-worker (Spring Batch / Kafka consumer)"]
        EMB["embedding service (ONNX/TEI, CPU or small GPU)"]
        LLM["llm-server (vLLM / Ollama, GPU pool, slow start)"]
        CFG[(ConfigMaps / config server: model ids, prompt versions)]
        SEC[(Secrets via External Secrets / CSI)]
        OTEL[OTel collector]
    end
    VS[(vector store)] --- APP
    VS --- ING
    APP --> LLM
    APP --> EMB
    ING --> EMB
    APP -.-> OTEL
    APP --> EXT[(hosted provider, egress-restricted)]
```

| Change type | Release mechanism |
|---|---|
| Application code | CI/CD, container image, rolling update |
| Prompt version | Config change + eval gate + canary |
| Model id / provider | Config change + full eval + canary, fallback ready |
| Embedding model | Blue/green re-index (Phase 4), switch alias |
| Self-hosted model weights | New model-server deployment, warm-up, traffic shift |

## Build it

This repository's `docker-compose.yml` runs the course stack locally — the app, Ollama (with the chat and embedding models pulled on start) and PostgreSQL with pgvector for the Phase 8 exercises:

```bash
docker compose up -d
```

A container image for any Spring Boot AI service needs no Dockerfile:

```bash
./mvnw -pl app spring-boot:build-image -Dspring-boot.build-image.imageName=ai-course:1.0.0
```

For Kubernetes, the model server needs a readiness probe that waits for the model to load, resource requests for GPU (`nvidia.com/gpu: 1`) and node selectors for the GPU pool; the Spring app needs liveness/readiness from Actuator (`/actuator/health/liveness`, `/actuator/health/readiness`).

## Now with the library

| Need | Option |
|---|---|
| GPU serving | vLLM, Hugging Face TGI, NVIDIA NIM/Triton, Ollama (simpler) |
| Kubernetes model operators | KServe, KubeAI |
| Config | Spring Cloud Config, Kubernetes ConfigMaps + `@ConfigurationProperties`, feature flags |
| Secrets | Vault, cloud secret managers, External Secrets Operator |
| Egress control | Network policies, egress gateways allowing only approved AI endpoints |

## Production notes

- **Warm-up:** load models and run a test inference before marking ready; cold starts of tens of seconds are normal.
- **Data residency:** pin hosted model endpoints to approved regions; restrict egress elsewhere.
- **Disaster recovery:** the vector index must be rebuildable from sources; back up conversation and audit stores.
- **Cost:** GPU nodes are expensive — scale to zero for non-production, share via multi-adapter serving.

## Exercises

1. **Easy** — Build the course app image with `spring-boot:build-image` and run it against the compose Ollama.
2. **Medium** — Write Kubernetes manifests (Deployment, Service, ConfigMap, HPA) for the course app with Actuator probes and model settings from the ConfigMap.
3. **Hard** — Add an Ollama Deployment with a PersistentVolume for models, an init job that pulls them, and a readiness probe that checks `/api/tags` lists the chat model.

## Common mistakes

- Model server and application in the same pod with the same scaling.
- Prompts baked into images with no runtime switch.
- Unrestricted egress to any AI API.

## Further reading

- Spring Boot container images: <https://docs.spring.io/spring-boot/reference/packaging/container-images/index.html>
- vLLM deployment documentation; KServe documentation.
