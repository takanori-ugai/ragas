# RAGAS Kotlin Full-Parity Completion Plan

Last updated: 2026-03-31

## Goal

Complete Kotlin parity with Python `../src/ragas` so Kotlin can be used as a first-class equivalent for evaluation, prompts, metrics, integrations, backends, optimizers, and CLI workflows.

## Definition of Done

- Public Kotlin APIs cover Python-equivalent core workflows with compatible behavior.
- All Python default and collection metrics have Kotlin equivalents (or documented intentional exclusions).
- Prompt/optimizer/testset flows support Python-style typed prompt pipelines.
- Integration and backend surfaces are functionally mirrored at parity level.
- `PARITY_MATRIX.md` shows only intentional deferrals (no unowned “partial” gaps).

## Current Baseline (Already Completed)

- [x] Core Kotlin package, runtime, dataset/sample models, evaluator orchestration.
- [x] MVP default metrics (answer relevancy, context precision, faithfulness, context recall).
- [x] LangChain4j LLM/embedding adapters + cache wrappers.
- [x] Backends: in-memory, CSV, JSONL.
- [x] Prompt basics: `SimplePrompt`, dynamic few-shot selection, persistence, translation adapt().
- [x] Integration adapters: LangChain/LlamaIndex record-based evaluation.
- [x] Tracing scaffold: run lifecycle + in-memory/Langfuse-style/MLflow-style observers.
- [x] Testset scaffold: graph + transforms + generator baseline.
- [x] Test suite green (`./gradlew test`).

## Remaining Workstreams

### WS1: Evaluation API/Behavior Parity `[ ]`

- [ ] Extend `evaluate/aevaluate` parity options:
  - callbacks/tracing hook compatibility at API level
  - column remap path parity
  - token usage / cost callback parity hooks
  - executor-return / cancellable path parity where applicable
- [ ] Add compatibility shims for Python-style argument patterns where safe.
- Exit criteria:
  - Kotlin evaluator supports Python-equivalent control surface for non-framework-specific options.

### WS2: Prompt System Parity `[ ]`

- [x] Implement typed prompt abstractions equivalent to Python `BasePrompt` + `PydanticPrompt`.
- [x] Add structured output parsing/validation loop (retry-on-parse-failure semantics).
- [x] Port few-shot typed prompt variants (including few-shot Pydantic flows).
- [x] Evaluate and scope multimodal prompt parity (`multi_modal_prompt.py`) for Kotlin compatibility.
- Progress note (2026-03-31):
  - Added WS2 foundation in `src/main/kotlin/ragas/prompt/TypedPrompt.kt`:
    `BasePrompt`, typed prompt model (`TypedPromptModel` / `TypedPromptExample`), `TypedPrompt`,
    `StructuredOutputParser`, and parse-retry generation flow.
  - Added coverage in `src/test/kotlin/ragas/prompt/TypedPromptTest.kt`.
  - Added few-shot typed variants in `src/main/kotlin/ragas/prompt/TypedPrompt.kt`:
    `FewShotTypedPrompt`, `DynamicFewShotTypedPrompt`, `FewShotPydanticPrompt`,
    and `DynamicFewShotPydanticPrompt`.
  - Extended `src/test/kotlin/ragas/prompt/TypedPromptTest.kt` with dynamic few-shot
    selection and few-shot Pydantic flow tests.
  - Multimodal parity scope (from Python `../src/ragas/prompt/multi_modal_prompt.py`):
    - Gap identified: Kotlin `BaseRagasLlm` is text-only (`generateText`) and currently has
      no multimodal message abstraction comparable to Python `PromptValue.to_messages()`.
    - Gap identified: Kotlin prompt stack has no typed image/text content block model and no
      secure image-source normalization pipeline (data URI / URL / local file policy).
    - Kotlin MVP scope (implement first):
      1. Add multimodal content model in prompt layer:
         `PromptContentPart.Text`, `PromptContentPart.ImageDataUri`, `PromptContentPart.ImageUrl`.
      2. Add multimodal-capable LLM interface extension:
         `MultiModalRagasLlm.generateContent(parts, ...)` while keeping `generateText` backward compatible.
      3. Add `ImageTextTypedPrompt` + few-shot variant on top of `BasePrompt` typed stack.
      4. Support data-URI images and already-hosted HTTPS image URLs; keep parsing-retry behavior from WS2.
      5. Add conformance tests for mixed text+image formatting and parser retry behavior.
    - Deferred (explicit, non-blocking for MVP):
      1. URL download/proxy/validation pipeline (SSRF checks, max-size streaming, content sniffing).
      2. Optional local file loading policy and directory allow-list behavior.
      3. Full Python callback/tracing parity for multimodal prompt generation path.
    - Exit target for multimodal scope:
      Kotlin can format and execute typed text+image prompts against multimodal-capable backends,
      with deterministic JSON output parsing/retry behavior equivalent to text prompts.
  - Implemented WS2 multimodal MVP:
    - Added prompt content-part model in `src/main/kotlin/ragas/prompt/PromptContentPart.kt`
      (`Text`, `ImageDataUri`, `ImageUrl` with HTTPS/data-URI validation).
    - Added multimodal LLM extension in
      `src/main/kotlin/ragas/llms/MultiModalRagasLlm.kt`.
    - Extended `src/main/kotlin/ragas/llms/LangChain4jLlm.kt` to implement multimodal generation.
    - Added `ImageTextTypedPrompt` in
      `src/main/kotlin/ragas/prompt/ImageTextTypedPrompt.kt` with retry-on-parse-failure semantics
      and text-only fallback for non-multimodal LLMs.
    - Added tests in
      `src/test/kotlin/ragas/prompt/ImageTextTypedPromptTest.kt` for mixed content assembly,
      multimodal retry flow, and fallback behavior.
- Exit criteria:
  - Metrics/optimizers can operate on typed prompt objects rather than string-only heuristics.

### WS3: Metrics Catalog Parity `[ ]`

- [ ] Port additional metric primitives/validators to support all collection metrics.
- [ ] Port Python metric collection modules in priority tiers:
  1. Retrieval/groundedness family
  2. Agent/tool-call metrics
  3. Text-quality/translation metrics
  4. Domain/rubric-specific metrics
- [ ] Align score semantics and edge-case handling with Python golden fixtures.
- Progress note (2026-04-01):
  - Started WS3 Tier-1 retrieval/groundedness ports in Kotlin:
    - `src/main/kotlin/ragas/metrics/collections/ContextRelevanceMetric.kt`
    - `src/main/kotlin/ragas/metrics/collections/ResponseGroundednessMetric.kt`
    - `src/main/kotlin/ragas/metrics/collections/ContextPrecisionCollectionMetrics.kt`
    - `src/main/kotlin/ragas/metrics/collections/EntityAndIdRetrievalMetrics.kt`
    - `src/main/kotlin/ragas/metrics/collections/Tier1Metrics.kt`
  - Added fixture baseline derived from Python migration tests:
    - `tests/e2e/metrics_migration/test_context_relevance_migration.py`
    - `tests/e2e/metrics_migration/test_response_groundedness_migration.py`
    - `tests/e2e/metrics_migration/test_context_precision_migration.py`
    - `tests/e2e/metrics_migration/test_context_entity_recall_migration.py`
    - `src/ragas/metrics/_context_precision.py::IDBasedContextPrecision`
    - Kotlin fixture: `src/test/resources/fixtures/metrics/ws3_tier1_retrieval_groundedness_fixture.json`
    - Kotlin fixture: `src/test/resources/fixtures/metrics/ws3_tier1_context_precision_fixture.json`
    - Kotlin fixture: `src/test/resources/fixtures/metrics/ws3_tier1_entity_id_fixture.json`
  - Added fixture-band conformance coverage:
    - `src/test/kotlin/ragas/metrics/collections/Tier1RetrievalGroundednessFixtureTest.kt`
    - `src/test/kotlin/ragas/metrics/collections/ContextPrecisionCollectionFixtureTest.kt`
    - `src/test/kotlin/ragas/metrics/collections/EntityAndIdRetrievalFixtureTest.kt`
- Exit criteria:
  - Kotlin has parity for Python metrics currently under `../src/ragas/metrics`.

### WS4: Integrations Parity `[ ]`

- [ ] Expand beyond current LangChain/LlamaIndex record adapters.
- [ ] Port high-value integration modules (Langsmith, Helicone, Opik, LangGraph, Swarm, AG-UI, R2R, Bedrock as applicable).
- [ ] Preserve optional-dependency strategy and explicit unsupported errors for missing runtime deps.
- Exit criteria:
  - Kotlin integration surface matches Python’s practical integration coverage for mainstream workflows.

### WS5: Backends Parity `[ ]`

- [ ] Implement registry extension/discovery model equivalent to Python entry-point backend discovery.
- [ ] Port optional Google Drive backend behavior or document platform-specific replacement.
- [ ] Align aliasing/info/inspection features in backend registry.
- Exit criteria:
  - Backend registration and optional backend story are parity-level compatible.

### WS6: Testset Pipeline Parity `[ ]`

- [ ] Port production-grade transforms:
  - extractors (`llm_based`, `embeddings`, `regex`)
  - splitters
  - relationship builders
- [ ] Port synthesizer variants (single-hop/multi-hop strategies and prompts).
- [ ] Ensure generated testsets align structurally and semantically with Python output expectations.
- Exit criteria:
  - Kotlin testset generation can replace Python flow for real-world synthesis scenarios.

### WS7: Optimizer Parity `[ ]`

- [ ] Implement DSPy optimizer path (optional dependency model, adapter layer, caching behavior).
- [ ] Expand genetic optimizer from scaffold to Python-equivalent prompt optimization lifecycle.
- [ ] Integrate optimizer outputs with metric prompt objects in typed prompt stack.
- Exit criteria:
  - Both genetic and DSPy optimization workflows are usable in Kotlin with parity semantics.

### WS8: CLI Parity `[ ]`

- [ ] Expand Kotlin CLI beyond `status/backends`:
  - experiment execution
  - metrics reporting/aggregation
  - baseline comparison and gate outputs
- [ ] Keep CLI UX scriptable while mirroring essential Python CLI workflows.
- Exit criteria:
  - Kotlin CLI can run parity-level evaluation workflows from terminal.

### WS9: Parity Verification + Documentation `[ ]`

- [ ] Build parity test matrix mapping Python module -> Kotlin module/test.
- [ ] Add cross-language golden fixtures where behavior must be numerically/structurally aligned.
- [ ] Update `PARITY_MATRIX.md`, `MIGRATION.md`, and `README.md` per milestone.
- [ ] Add release checklist for parity claims.
- Exit criteria:
  - Parity claims are backed by repeatable tests and explicit documentation.

## Execution Order (Critical Path)

1. WS2 Prompt System Parity
2. WS3 Metrics Catalog Parity
3. WS7 Optimizer Parity
4. WS1 Evaluation API/Behavior Parity
5. WS6 Testset Pipeline Parity
6. WS4 Integrations Parity
7. WS5 Backends Parity
8. WS8 CLI Parity
9. WS9 Verification + Docs

## Milestone Checkpoints

- M1: Typed prompt stack + evaluator API parity baseline.
- M2: Core metric families ported with golden parity tests.
- M3: Optimizer parity (DSPy + upgraded genetic).
- M4: Testset pipeline parity.
- M5: Integrations/backends/CLI parity closure.
- M6: Final parity audit and docs freeze.

## Risks and Mitigations

- Risk: Python metrics depend on Python-only ecosystem packages.
  - Mitigation: isolate optional modules behind adapters and capability flags.
- Risk: Behavioral drift in prompt parsing and structured output.
  - Mitigation: fixture-first parity tests before exposing as stable.
- Risk: Scope explosion from low-value integrations.
  - Mitigation: prioritize by usage and mark explicit deferrals with rationale.

## Immediate Next Actions

1. Start WS3 Tier-2 ports for agent/tool-call metrics with fixture-backed score-band parity tests.
2. Add WS9 parity map document (Python file -> Kotlin target + status).
3. Wire early WS7 optimizer integration points to consume typed/multimodal prompt objects.
