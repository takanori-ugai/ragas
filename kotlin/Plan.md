# RAGAS Kotlin Conversion Plan

## Progress Summary

- [x] Phase 1: Bootstrap Kotlin package + build alignment
- [x] Phase 2: Port core domain/data model (foundation)
- [x] Phase 3: Port runtime primitives
- [x] Phase 4: Port metric abstraction layer
- [x] Phase 5: Port LLM + Embedding adapters (minimal provider set first)
- [x] Phase 6: Port evaluation pipeline
- [x] Phase 7: Port initial metric set (MVP)
- [~] Phase 8: Port supporting modules incrementally
- [~] Phase 9: Parity test strategy
- [ ] Phase 10: Migration and compatibility layer

`[x]` done, `[~]` partial, `[ ]` not started

## Detailed Status

### 1) Bootstrap Kotlin package + build alignment `[x]`

- [x] Create package root `ragas` under `src/main/kotlin/ragas`.
- [x] Align Gradle `group`, `application.mainClass`, and module naming to RAGAS Kotlin.
- [x] Add test scaffolding under `src/test/kotlin/ragas`.

### 2) Port core domain/data model first (foundation) `[x]`

- [x] Implement Kotlin equivalents for:
  - `SingleTurnSample`
  - `MultiTurnSample`
  - `EvaluationDataset`
  - `MetricResult`
  - message/tool-call models
- [x] Preserve Python multi-turn message validation constraints.

### 3) Port runtime primitives `[x]`

- [x] Implement `RunConfig` (timeouts/retries/workers/seed).
- [x] Implement coroutine-based `Executor` with batching/cancellation/timeout/error handling.
- [x] Add retry utility with exponential backoff + jitter.

### 4) Port metric abstraction layer `[x]`

- [x] Implement:
  - `Metric`
  - `SingleTurnMetric`
  - `MultiTurnMetric`
  - `MetricWithLLM`
  - `MetricWithEmbeddings`
- [x] Implement required-column validation and output-type model.

### 5) Port LLM + Embedding adapters (minimal provider set first) `[x]`

- [x] Define provider-agnostic LLM interface (`BaseRagasLlm`).
- [x] Implement LangChain4j chat-model adapter (`LangChain4jLlm`).
- [x] Add embeddings abstraction + adapter(s).
- [x] Add cache integration points.

### 6) Port evaluation pipeline `[x]`

- [x] Implement `evaluate/aevaluate` equivalent orchestration:
  - [x] dataset validation
  - [x] metric initialization
  - [x] per-row/per-metric async execution
  - [x] aggregation into evaluation result

### 7) Port initial metric set (MVP) `[x]`

- [x] Port default baseline metrics:
  - [x] answer relevancy
  - [x] context precision
  - [x] faithfulness
  - [x] context recall
- [x] Add generic discrete/numeric/ranking primitives.

### 8) Port supporting modules incrementally `[~]`

- [x] Prompt subsystem (simple prompt + prompt collection persistence)
- [x] Backends (`inmemory`, `csv`, `jsonl` first)
- [ ] Testset generation and graph transforms
- [ ] Integrations, CLI expansion, optimizers

### 9) Parity test strategy `[~]`

- [x] Kotlin unit tests for deterministic behavior (evaluate flow, MVP metrics, cache wrappers, prompt/backends)
- [ ] Golden fixtures for parsing/validation/aggregation
- [ ] E2E evaluate-flow tests with mock LLM/embeddings

### 10) Migration and compatibility layer `[ ]`

- [ ] Feature parity matrix (ported/partial/deferred)
- [ ] Explicit errors for unsupported integrations
- [ ] Stable public package/API surface

## Next Recommended Steps

1. Expand tests for multi-turn metrics and failure/cancellation paths.
2. Implement testset generation and graph transform scaffolding.
3. Add golden fixtures for score aggregation/validation behavior.
