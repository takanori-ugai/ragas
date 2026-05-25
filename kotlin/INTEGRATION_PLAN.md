# WS4 Integrations Parity Implementation Plan

Last updated: 2026-05-25
Owner: Kotlin parity track (WS4)
Scope source: `Plan.md` -> `### WS4: Integrations Parity [ ]`

## Objective

Complete WS4 by expanding integration parity beyond LangChain/LlamaIndex so Kotlin covers the same practical mainstream integration workflows as Python, while preserving explicit unsupported behavior for truly unavailable runtime/dependency paths.

## WS4 Success Criteria

- Integration adapters for high-value frameworks are functionally usable for core record evaluation flows.
- Optional dependency strategy remains explicit and deterministic (no silent runtime failures).
- Unsupported paths are intentional, documented, and covered by tests.
- `Plan.md` WS4 can be marked `[x]` with verifiable evidence (tests + docs).

## Current State (2026-05-25)

| Integration | toDataset | evaluateRecords | toMetricPayload | Status |
| --- | --- | --- | --- | --- |
| LangChain | Implemented | Implemented (`evaluate(...)`) | Implemented | Done baseline |
| LlamaIndex | Implemented | Implemented (`evaluate(...)`) | Implemented | Done baseline |
| Langsmith | Implemented | `unsupportedIntegration("langsmith")` | Implemented | Scaffold only |
| Helicone | Implemented | `unsupportedIntegration("helicone")` | Implemented | Scaffold only |
| Opik | Implemented | Unsupported + `@Deprecated(level = ERROR)` | Implemented | Scaffold only |
| LangGraph | Implemented | Implemented (`evaluate(...)`) | Implemented | Done (single-turn + conversion helpers) |
| Swarm | Implemented | `unsupportedIntegration("swarm")` | Implemented | Scaffold only |
| AG-UI | Implemented | Implemented (`evaluate(...)`) | Implemented | Functional parity (record path) |
| R2R | Implemented | `unsupportedIntegration("r2r")` | Implemented | Scaffold only |
| Bedrock | Implemented (metadata guard) | `unsupportedIntegration("bedrock")` | Implemented | Scaffold only |

## Gaps Blocking WS4 Completion

- Most adapters do not execute end-to-end metric evaluation.
- Conformance coverage exists for LangChain/LlamaIndex plus LangGraph; remaining adapters still need focused coverage.
- Unsupported/dependency-gated behavior is not uniformly documented and validated via focused tests.

## Execution Plan

### Phase 1: Normalize Adapter Contract

- Align all integration adapters to the same behavior pattern used by LangChain/LlamaIndex:
  - `evaluateRecords(...)` forwards to `evaluate(dataset = toDataset(records), ...)`.
  - tracing wrapper remains (`traceEvaluation(...)`) with stable framework names.
  - metric payload remains `result.scores`.
- Keep input-record schemas framework-specific, but normalize conversion semantics:
  - empty reference contexts => `null`.
  - required text fields map to `SingleTurnSample` consistently.

Deliverables:
- Updated adapter implementations in `src/main/kotlin/ragas/integrations/*.kt`.
- No accidental API breakage to existing record data classes.

### Phase 2: Implement High-Value Adapters

Prioritized implementation order:
1. Langsmith
2. Helicone
3. LangGraph
4. R2R
5. Swarm
6. Opik
7. AG-UI
8. Bedrock

Implementation rule:
- If no external runtime dependency is required for record-based evaluation, implement `evaluateRecords` now.
- If a framework has a true missing runtime requirement, keep explicit `unsupportedIntegration(...)`, but make it intentional and documented.

Notes:
- Re-evaluate `@Deprecated(level = ERROR)` on Opik/AG-UI once implemented; remove deprecation if functional.
- Keep Bedrock metadata safety check unless `SingleTurnSample` metadata support is introduced.

### Phase 3: Unsupported/Optional-Dependency Policy

Define and enforce one policy across all adapters:
- `unsupportedIntegration(name)` only for intentionally unsupported runtime paths.
- Error message must include actionable guidance (which dependency/capability is missing).
- No silent fallback to partial behavior.

Deliverables:
- `UnsupportedIntegration.kt` message hardening (if needed).
- KDoc per adapter clarifying implemented vs intentionally unsupported scope.

### Phase 4: Conformance Test Expansion

Create `IntegrationsParityTest` coverage that validates every adapter with the same minimum contract:
- `toDataset` mapping correctness.
- `evaluateRecords` behavior:
  - implemented adapters produce 1+ metric fields (smoke parity).
  - intentionally unsupported adapters throw deterministic `UnsupportedOperationException` with clear message.
- `toMetricPayload` pass-through behavior.

Suggested test layout:
- Keep existing `IntegrationsScaffoldTest` for baseline.
- Add targeted tests per adapter in `src/test/kotlin/ragas/integrations/`.

### Phase 5: Documentation and WS4 Closure

- Update parity docs:
  - `PARITY_MATRIX.md`
  - `PARITY_TEST_MATRIX.md`
- Add WS4 progress note in `Plan.md` listing completed adapter parity and test coverage.
- Mark `### WS4: Integrations Parity` as `[x]` after acceptance checks pass.

## Langsmith Detailed Plan

This section expands WS4 specifically for `LangsmithIntegration` so implementation can proceed with explicit scope boundaries.

### Parity Targets

- Kotlin file to complete:
  - `src/main/kotlin/ragas/integrations/LangsmithIntegration.kt`
- Kotlin tests to update/add:
  - `src/test/kotlin/ragas/integrations/LangsmithIntegrationTest.kt`
  - `src/test/kotlin/ragas/integrations/LangsmithRecordMappingTest.kt` (optional split, if test file size grows)
- Python parity references:
  - `../src/ragas/integrations/langsmith.py`

### Scope Clarification (WS4)

- In-scope for WS4:
  - record-based evaluation parity in Kotlin (`toDataset`, `evaluateRecords`, `toMetricPayload`) using existing ragas evaluation runtime.
  - tracing lifecycle parity with stable framework key (`framework = "langsmith"`).
- Out-of-scope for WS4:
  - direct LangSmith SDK client operations from Kotlin equivalent to Python `upload_dataset(...)` and `Client.run_on_dataset(...)`.
  - remote LangSmith dataset/project lifecycle management APIs (can be tracked in a post-WS4 follow-up).

### Target Capabilities

1. Single-turn integration parity:
- `evaluateRecords(...)` executes `evaluate(...)` instead of throwing `unsupportedIntegration("langsmith")`.
- Tracing contract remains unchanged (`RunStarted` -> `MetricRowLogged` -> `RunCompleted`; no `RunFailed` on success).

2. Record mapping consistency:
- Keep current `LangsmithRecord` schema unchanged.
- Preserve conversion semantics:
  - `referenceContexts = emptyList()` maps to `null`.
  - required text fields map to `SingleTurnSample` consistently with LangChain/LlamaIndex/LangGraph adapters.

3. Metric payload compatibility:
- `toMetricPayload(...)` remains `result.scores` pass-through and requires no format migration.

### Work Packages

#### WSS-1: Unblock `evaluateRecords` for Langsmith

- Replace unsupported call path in `LangsmithIntegration.evaluateRecords(...)` with:
  - `evaluate(dataset = toDataset(records), metrics = ..., llm = ..., embeddings = ..., runConfig = ..., raiseExceptions = ...)`.
- Keep method signature/defaults and trace wrapper unchanged.

Exit check:
- `LangsmithIntegrationTest` asserts successful evaluation payload instead of unsupported exception.

#### WSS-2: Trace Contract Verification

- Update tests to assert:
  - first event is `RunStarted`,
  - at least one `MetricRowLogged`,
  - last event is `RunCompleted`,
  - run metadata/tags are preserved.

Exit check:
- no `RunFailed` event in successful smoke path.

#### WSS-3: Dataset Mapping Regression Coverage

- Retain and extend `toDataset` assertions for:
  - input/output/retrieved/reference mapping,
  - empty `referenceContexts` -> `null`.
- Add a focused assertion that optional `reference` is forwarded when present.

Exit check:
- mapping behavior matches existing adapter contract and does not regress record schema semantics.

#### WSS-4: Documentation and Matrix Evidence

- Update after implementation:
  - `PARITY_MATRIX.md` (Langsmith status from scaffold to functional adapter),
  - `PARITY_TEST_MATRIX.md` (Langsmith test evidence row),
  - `Plan.md` WS4 progress note.

Exit check:
- WS4 docs show Langsmith as completed adapter-level parity.

### Milestones and Estimation

1. M1 (0.5 day): `evaluateRecords` implementation + smoke test migration.
2. M2 (0.5 day): trace + mapping regression expansion.
3. M3 (0.25 day): docs/parity matrix updates.

### Acceptance Criteria (Langsmith-Specific)

- `LangsmithIntegration.evaluateRecords(...)` no longer throws unsupported by default.
- Langsmith integration tests pass with:
  - single-turn evaluate flow,
  - trace lifecycle assertions,
  - dataset mapping assertions.
- `LangsmithRecord` API remains backward-compatible (no required field additions/removals).
- WS4 documentation artifacts include Langsmith completion evidence.

### Verification Commands (Langsmith-Specific)

- `./gradlew --no-daemon test --tests 'ragas.integrations.LangsmithIntegrationTest'`
- `./gradlew --no-daemon test --tests '*Integration*Test'`
- `./gradlew --no-daemon test`

## AG-UI Detailed Plan

This section expands WS4 specifically for `AgUiIntegration` with both parity-first evaluation support and an explicit SDK alignment path.

### Parity Targets

- Kotlin files to complete:
  - `src/main/kotlin/ragas/integrations/AgUiIntegration.kt`
  - `build.gradle.kts` (AG-UI dependency coordinates)
- Kotlin tests to update/add:
  - `src/test/kotlin/ragas/integrations/AgUiIntegrationTest.kt`
  - `src/test/kotlin/ragas/integrations/AgUiRemoteApiIntegrationTest.kt` (new, if remote API path is enabled)
- Python parity references:
  - `../src/ragas/integrations/ag_ui.py` (or equivalent AG-UI integration entrypoint in Python tree)
- AG-UI Java SDK reference:
  - `https://github.com/ag-ui-protocol/ag-ui/blob/main/docs/sdk/java/overview.mdx`

### Scope Clarification (WS4)

- In-scope for WS4:
  - record-based evaluation parity in Kotlin (`toDataset`, `evaluateRecords`, `toMetricPayload`) using ragas core `evaluate(...)`.
  - removal of intentional hard-failure path (`unsupportedIntegration("ag-ui")`) for the standard record workflow.
  - tracing lifecycle parity (`framework = "ag-ui"`).
- Out-of-scope for WS4:
  - full AG-UI event-stream client runtime (SSE subscription, interactive multi-turn transport orchestration).
  - AG-UI server runtime responsibilities outside record-evaluation adapter boundaries.

### Dependency and Packaging Notes

- Current docs list Maven coordinates as `com.ag-ui:{core,client,http}:0.0.1`.
- If those artifacts are unavailable in Maven Central for CI/runtime, use published fallback coordinates with the same logical modules:
  - `io.github.pascalwilbrink.ag-ui.community:java-core:0.0.1`
  - `io.github.pascalwilbrink.ag-ui.community:java-client:0.0.1`
  - `io.github.pascalwilbrink.ag-ui.community:java-http:0.0.1`
- Keep the resolution strategy explicit in docs to avoid hidden dependency drift.

### Target Capabilities

1. Single-turn integration parity:
- `AgUiIntegration.evaluateRecords(...)` executes `evaluate(...)` instead of throwing unsupported.
- Remove `@Deprecated(level = ERROR)` from implemented `evaluateRecords(...)`.

2. Record mapping consistency:
- Preserve current `AgUiRecord` schema.
- Preserve conversion semantics:
  - `referenceContexts = emptyList()` maps to `null`.
  - optional `reference` pass-through remains intact.

3. Metric payload compatibility:
- `toMetricPayload(...)` remains pass-through (`result.scores`) without format migration.

4. Optional SDK-backed bridge (follow-up inside WS4 if bandwidth allows):
- add helper(s) to map AG-UI SDK message/event payloads to `AgUiRecord`.
- keep this additive and non-breaking to core adapter API.

### Work Packages

#### AGU-1: Unblock `evaluateRecords` for AG-UI

- Replace unsupported path in `AgUiIntegration.evaluateRecords(...)` with:
  - `evaluate(dataset = toDataset(records), metrics = ..., llm = ..., embeddings = ..., runConfig = ..., raiseExceptions = ...)`.
- Keep method signature/defaults and tracing wrapper unchanged.

Exit check:
- `AgUiIntegrationTest` verifies successful evaluation payload.

#### AGU-2: Remove Deprecated Error Gate

- Remove `@Deprecated(level = ERROR)` once `evaluateRecords` is functional.
- Retain KDoc notes for intentionally deferred capabilities (event-stream runtime).

Exit check:
- callers can invoke `AgUiIntegration.evaluateRecords(...)` without suppression annotations.

#### AGU-3: Trace Contract Verification

- Update tests to assert:
  - first event is `RunStarted`,
  - at least one `MetricRowLogged`,
  - last event is `RunCompleted`,
  - no `RunFailed` on successful smoke path.

Exit check:
- trace event lifecycle matches LangChain/LlamaIndex/LangGraph/Langsmith behavior.

#### AGU-4: Mapping Regression Coverage

- Extend mapping tests for:
  - input/output/retrieved/reference mapping,
  - empty `referenceContexts` -> `null`,
  - optional `reference` forwarding.

Exit check:
- mapping behavior remains backward-compatible for existing `AgUiRecord`.

#### AGU-5: Documentation and Matrix Evidence

- Update after implementation:
  - `PARITY_MATRIX.md` (AG-UI status scaffold -> functional adapter),
  - `PARITY_TEST_MATRIX.md` (AG-UI test evidence row),
  - `Plan.md` WS4 progress note.

Exit check:
- WS4 docs reflect AG-UI as functional for record-based evaluation path.

### Milestones and Estimation

1. M1 (0.5 day): `evaluateRecords` implementation + deprecation cleanup.
2. M2 (0.5 day): trace + mapping regression coverage.
3. M3 (0.25 day): dependency/docs/matrix updates.

### Acceptance Criteria (AG-UI-Specific)

- `AgUiIntegration.evaluateRecords(...)` no longer throws unsupported by default.
- AG-UI integration tests pass with:
  - single-turn evaluate flow,
  - trace lifecycle assertions,
  - dataset mapping assertions.
- `AgUiRecord` API remains backward-compatible (no required field changes).
- WS4 docs include AG-UI completion evidence.

### Verification Commands (AG-UI-Specific)

- `./gradlew --no-daemon test --tests 'ragas.integrations.AgUiIntegrationTest'`
- `./gradlew --no-daemon test --tests '*Integration*Test'`
- `./gradlew --no-daemon test`
