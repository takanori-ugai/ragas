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
- [x] Phase 9: Parity test strategy
- [x] Phase 10: Migration and compatibility layer

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

- [x] Prompt subsystem (simple prompt + prompt collection persistence + Python-style prompt framing compatibility)
- [x] Backends (`inmemory`, `csv`, `jsonl` first)
- [x] Testset generation and graph transform scaffolding
- [~] Integrations, CLI expansion, optimizers
  - [x] LangChain/LlamaIndex record adapters with evaluation wiring
  - [x] LangChain/LlamaIndex tracing hooks with Langfuse/MLflow-style observers
  - [x] CLI scaffolding
  - [x] Optimizers (baseline scaffolding + genetic optimizer)

### 9) Parity test strategy `[x]`

- [x] Kotlin unit tests for deterministic behavior (evaluate flow, MVP metrics, cache wrappers, prompt/backends)
- [x] Multi-turn metric and executor failure/cancellation behavior tests
- [x] Golden fixtures for parsing/validation/aggregation
- [x] E2E evaluate-flow tests with mock LLM/embeddings

### 10) Migration and compatibility layer `[x]`

- [x] Feature parity matrix (ported/partial/deferred)
- [x] Explicit errors for unsupported integrations
- [x] Stable public package/API surface

## Next Recommended Steps

1. Expand integration adapters to additional frameworks beyond LangChain/LlamaIndex.
2. Upgrade testset/transform scaffolds toward production-grade behavior.
3. Implement production-grade DSPy optimizer adapter and metric prompt wiring.

## Prompt Parity Findings (Python vs Kotlin)

Scope compared: default evaluation metrics (`answer_relevancy`, `context_precision`, `context_recall`, `faithfulness`) and generic prompt/LLM call path.

1. Python default metrics are LLM-prompt driven; Kotlin defaults are heuristic-only.
   - Python uses metric-specific prompts + structured output models.
   - Kotlin default metrics compute token-overlap style scores and do not send prompts to an LLM.

2. Prompt construction format differs substantially. `[partially closed]`
   - Python (`PydanticPrompt`/`BasePrompt`) builds prompts from instruction + JSON schema + few-shot examples + explicit `Input/Output` framing.
   - Kotlin now mirrors this structure in `PromptTemplate`/`SimplePrompt` (schema contract text + examples + explicit `Input/Output` frame), while remaining string-based rather than model-bound prompt classes.

3. LLM output handling differs. `[open]`
   - Python expects typed structured outputs (Pydantic models) and validates/parses JSON.
   - Kotlin primitive metrics parse raw text with lightweight heuristics (e.g., regex first-number extraction, string matching for discrete/ranking).

4. `answer_relevancy` algorithm/prompting is different.
   - Python prompts the LLM multiple times (strictness), generates reverse questions, detects noncommittal responses, then uses embedding cosine similarity.
   - Kotlin uses Jaccard similarity over token sets of `user_input` and `response`; no LLM prompt, no noncommittal detection, no reverse-question generation.

5. `context_precision` algorithm/prompting is different.
   - Python prompts the LLM per retrieved context for binary usefulness verdicts (with reason), then computes average precision.
   - Kotlin checks token overlap between each retrieved context and response tokens, then returns ratio of overlapping contexts.

6. `context_recall` inputs and prompting are different.
   - Python prompts the LLM to classify each statement in a reference answer as attributable/not attributable to retrieved context (with reasons).
   - Kotlin uses token coverage between `retrieved_contexts` and `reference_contexts`; no statement-level attribution prompt and no reasoned verdicts.

7. `faithfulness` pipeline is different.
   - Python uses two LLM prompts: statement decomposition, then NLI-style faithfulness verdict per statement.
   - Kotlin splits response into sentences and checks token overlap with retrieved-context tokens; no statement generation/NLI prompts.

8. Chat message composition differs in adapters.
   - Python prompt/LLM stack supports richer prompt objects, callbacks, and structured generation pathways.
   - Kotlin `LangChain4jLlm` sends a single `UserMessage` containing the rendered prompt string (no explicit system-message layer in current adapter).
