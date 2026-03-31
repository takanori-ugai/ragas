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

- [ ] Implement typed prompt abstractions equivalent to Python `BasePrompt` + `PydanticPrompt`.
- [ ] Add structured output parsing/validation loop (retry-on-parse-failure semantics).
- [ ] Port few-shot typed prompt variants (including few-shot Pydantic flows).
- [ ] Evaluate and scope multimodal prompt parity (`multi_modal_prompt.py`) for Kotlin compatibility.
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

1. Implement WS2 foundation (`BasePrompt`/typed prompt model + structured output parser).
2. Start WS3 Tier-1 metric ports using existing Python fixtures as baseline.
3. Add WS9 parity map document (Python file -> Kotlin target + status).
