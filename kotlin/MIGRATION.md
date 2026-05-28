# Migration Notes: Python RAGAS -> ragas-kotlin

## API Shape

- Kotlin exposes `evaluate(...)`/`aevaluate(...)` over `EvaluationDataset`.
- If metrics are omitted, default single-turn metrics are applied.
- Multi-turn datasets require multi-turn metrics.
- Tiered collection accessors are available at top-level:
  - `tier1Metrics()`, `tier2Metrics()`, `tier3Metrics()`, `tier4Metrics()`
- Evaluator parity hooks are available on `evaluate(...)`/`aevaluate(...)`:
  - `callbacks` (`EvaluationCallback` / `EvaluationEvent`)
  - `columnMap`
  - `tokenUsageParser`, `costParser`
  - `executorObserver` (cancellable path via exposed `Executor`)

## Main Differences (Current)

- Kotlin now includes Python-style collections exports for string metrics
  (`DistanceMeasure`, `ExactMatch`, `StringPresence`, `NonLLMStringSimilarity`)
  and alias compatibility for `AgentGoalAccuracy` and `SQLSemanticEquivalence`.
- Kotlin prompt stack includes `SimplePrompt` and typed prompts (`TypedPrompt`, few-shot typed variants, Pydantic-style wrappers) plus multimodal typed prompts (`ImageTextTypedPrompt`).
- Multimodal prompt content in Kotlin uses explicit parts (`PromptContentPart.Text`, `ImageDataUri`, `ImageUrl`) and secure normalization helpers (`MultiModalContentNormalizer` / `MultiModalInputPolicy`) for data URI, URL, and optional local-file flows.
- Kotlin provides LangChain/LlamaIndex record adapters and trace observers (`InMemoryTraceObserver`, Langfuse-style, MLflow-style), but broader Python integration ecosystem is still pending.
- Kotlin optimizers expose usable `GeneticOptimizer` and DSPy-style `DspyOptimizer` with prompt-object (`OptimizerPrompt`) flows, including metric primitive integration via `OptimizableMetricPrompt` and DSPy runtime controls via `DspyRuntimeConfig`.
- Kotlin testset transforms include sentence/headline splitters, extractors (summary/entity/topic/headlines/embedding), and relationship builders (adjacency/shared-keyword/cosine/jaccard/overlap) with default transform pipelines for documents and prechunked inputs.
- Python-style evaluator hooks for callbacks/column-remap/token-cost/executor exposure are available; broader framework-specific callback ecosystems may still differ.
- Kotlin backend registry supports lazy plugin discovery through `ServiceLoader` (`BackendDiscoveryProvider`) with alias/info inspection APIs (`listBackendInfo`, `getBackendInfo`).
- Google Drive backend is not bundled in Kotlin core; parity strategy is an optional plugin backend module discovered via `BackendDiscoveryProvider`.
- Kotlin CLI includes scriptable workflow commands for evaluation/report/compare:
  - `eval --input ... --output ...`
  - `report --input ...`
  - `compare --baseline ... --candidate ... --gate metric=delta`
- Collection metrics that are LLM-backed are strict by default for parity semantics.
  For compatibility/offline workflows, selected metrics expose explicit
  `allowHeuristicFallback = true`.

## Minimal Kotlin Evaluate Example

```kotlin
val dataset = EvaluationDataset(
    listOf(
        SingleTurnSample(
            userInput = "What is Kotlin?",
            response = "Kotlin is a JVM language.",
            retrievedContexts = listOf("Kotlin runs on JVM."),
            referenceContexts = listOf("Kotlin is JVM language."),
            reference = "Kotlin language"
        )
    )
)

val result = evaluate(dataset = dataset, metrics = null)
println(result.scores)
```

## Migration Recommendation

1. Start with `EvaluationDataset` + default metrics.
2. Replace Python backend usage with `LocalCsvBackend`/`LocalJsonlBackend` or `InMemoryBackend`.
3. Switch collection-metric imports to top-level tier accessors when needed (`tier1Metrics()` through `tier4Metrics()`).
4. Use adapter entrypoints in `ragas.integrations` for LangChain/LlamaIndex record evaluation; keep Python path for integrations not yet ported.
5. For prompt tuning flows, migrate to optimizer prompt-object contracts (`OptimizerPrompt`, `PromptObjectEvaluator`) and apply outcomes to primitive metrics through `OptimizableMetricPrompt`.
