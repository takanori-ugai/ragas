# Migration Notes: Python RAGAS -> ragas-kotlin

## API Shape

- Kotlin exposes `evaluate(...)`/`aevaluate(...)` over `EvaluationDataset`.
- If metrics are omitted, default single-turn metrics are applied.
- Multi-turn datasets require multi-turn metrics.

## Main Differences (Current)

- Python advanced prompt stack is reduced to `SimplePrompt` in Kotlin.
- Kotlin provides concrete LangChain/LlamaIndex record adapters for evaluation, but advanced tracing hooks are not yet implemented.
- Kotlin optimizers expose usable `GeneticOptimizer` and DSPy-style `DspyOptimizer` paths, with prompt-object (`OptimizerPrompt`) support and cache-backed DSPy scoring.

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
3. Use adapter entrypoints in `ragas.integrations` for LangChain/LlamaIndex record evaluation; keep Python path for advanced integration/optimizer features until parity is complete.
