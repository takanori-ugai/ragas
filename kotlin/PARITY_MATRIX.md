# RAGAS Python -> Kotlin Parity Matrix

Last updated: 2026-03-31

## Core Runtime

| Area | Status | Notes |
| --- | --- | --- |
| Dataset/sample schemas | Done | Single-turn + multi-turn models with validation |
| Evaluation (`evaluate`/`aevaluate`) | Partial | Core async/sync orchestration is implemented; Python-only extras (callbacks/cost parsing/column remap/executor return path) are not fully mirrored |
| Run config/retry/executor | Done | Timeout/retry/batch/cancel behavior |
| Default metrics (MVP 4) | Done | answer_relevancy, context_precision, faithfulness, context_recall |

## Models and Providers

| Area | Status | Notes |
| --- | --- | --- |
| LLM adapter | Done | LangChain4j `ChatModel` wrapper |
| Embedding adapter | Done | LangChain4j `EmbeddingModel` wrapper |
| LLM/embedding caching | Done | In-memory cache wrappers |

## Supporting Modules

| Area | Status | Notes |
| --- | --- | --- |
| Backends | Partial | `inmemory/csv/jsonl` and registry exist; Python optional `GDriveBackend` and entry-point plugin discovery are not ported |
| Prompt subsystem | Partial | `SimplePrompt`, dynamic few-shot selection, adapt(), and prompt persistence are implemented; Python `PydanticPrompt` stack remains unported |
| Testset/graph/transforms | Partial | Scaffold + core models + basic engine |
| Integrations | Partial | LangChain/LlamaIndex record adapters plus trace lifecycle observers (in-memory/Langfuse-style/MLflow-style); broader Python integrations are missing |
| CLI | Partial | Status/backends commands |
| Optimizers | Partial | Genetic scaffold + DSPy unsupported stub |

## Testing Parity

| Area | Status | Notes |
| --- | --- | --- |
| Unit tests | Done | Core, metrics, cache, backends, prompt, testset, multi-turn |
| Golden fixtures | Done | Default metrics + aggregation fixtures |
| E2E evaluation flow | Done | Mock LLM + embeddings injected via evaluate; `./gradlew test` passing |

## Not Yet Ported

- Full Python metrics catalog beyond MVP defaults (Python has many additional metric modules/collections)
- `PydanticPrompt`/typed prompt-generation stack and related prompt abstractions
- Full production-grade testset synthesizers/transform pipelines (extractors, splitters, relationship builders)
- Broader integration surface (e.g., Langsmith/Helicone/Opik/Swarm/AG-UI/R2R and richer framework-specific evaluators)
- Backend plugin discovery parity and optional Google Drive backend parity
- DSPy optimizer implementation and deeper optimizer wiring into metric prompt lifecycle
- Python CLI feature parity (experiment-oriented flows, rich reporting/comparison UX)
