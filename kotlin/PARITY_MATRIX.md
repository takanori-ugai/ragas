# RAGAS Python -> Kotlin Parity Matrix

Last updated: 2026-03-31

## Core Runtime

| Area | Status | Notes |
| --- | --- | --- |
| Dataset/sample schemas | Done | Single-turn + multi-turn models with validation |
| Evaluation (`evaluate`/`aevaluate`) | Done | Async orchestration + sync wrapper |
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
| Backends (inmemory/csv/jsonl) | Done | Registry and file backends |
| Prompt subsystem | Partial | Simple prompt + collection persistence |
| Testset/graph/transforms | Partial | Scaffold + core models + basic engine |
| Integrations | Partial | Concrete LangChain/LlamaIndex record adapters; advanced hooks pending |
| CLI | Partial | Status/backends commands |
| Optimizers | Partial | Genetic scaffold + DSPy unsupported stub |

## Testing Parity

| Area | Status | Notes |
| --- | --- | --- |
| Unit tests | Done | Core, metrics, cache, backends, prompt, testset, multi-turn |
| Golden fixtures | Done | Default metrics + aggregation fixtures |
| E2E evaluation flow | Done | Mock LLM + embeddings injected via evaluate |

## Not Yet Ported

- Advanced prompt types (`PydanticPrompt`/few-shot dynamic behavior)
- Full production-grade testset synthesizers and transformations
- Integration tracing/observability hooks and richer framework bindings
- DSPy optimizer implementation
- Optimizer wiring into metric prompt lifecycle
