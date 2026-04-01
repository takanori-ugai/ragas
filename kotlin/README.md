# Ragas Kotlin

Kotlin port of the Ragas evaluation toolkit.

This module provides:
- evaluation pipeline (`evaluate` / `aevaluate`)
- default single-turn metrics
- WS3 metric collection accessors:
  - `tier1Metrics()` retrieval/groundedness family
  - `tier2Metrics()` agent/tool-call + workflow family
  - `tier3Metrics()` answer-quality/translation family
  - `tier4Metrics()` rubrics/advanced + multimodal family
- prompt subsystem:
  - `SimplePrompt` + dynamic few-shot + persistence/adapt
  - typed prompt stack (`TypedPrompt`, few-shot typed variants, structured parse-retry loop)
  - multimodal typed prompts (`ImageTextTypedPrompt`) with `PromptContentPart` text/image parts
- optimizer prompt-object flows:
  - `OptimizerPrompt.Text` / `OptimizerPrompt.MultiModal`
  - metric primitive integration via `OptimizableMetricPrompt`
  - public optimizer facades: `geneticOptimizer()`, `dspyOptimizer(cache?)`
- LangChain4j LLM/embedding adapters
- CLI scaffold for runtime status and backend listing

## Requirements

- JDK 17+
- Gradle wrapper (included)

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew test
```

## Run CLI

The CLI entrypoint is `ragas.cli.MainKt`.

### Via Gradle `run`

```bash
./gradlew run --args="help"
./gradlew run --args="status"
./gradlew run --args="backends"
```

### Via Gradle `execute`

```bash
./gradlew execute --args="status"
```

## CLI Commands

### `help`

Show usage information.

```bash
./gradlew run --args="help"
```

### `status`

Show current Kotlin conversion/runtime status.

```bash
./gradlew run --args="status"
```

Current output sections include:
- core evaluation availability
- default metrics availability
- testset scaffold availability
- integration maturity

### `backends`

List registered backends from `BACKEND_REGISTRY`.

```bash
./gradlew run --args="backends"
```

## API Quick Example

```kotlin
import ragas.evaluate
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample

val dataset = EvaluationDataset(
    listOf(
        SingleTurnSample(
            userInput = "What is Kotlin?",
            response = "Kotlin is a JVM language.",
            retrievedContexts = listOf("Kotlin is a statically typed language for JVM and Android.")
        )
    )
)

val result = evaluate(dataset = dataset)
println(result.scores)
```

## Notes

- This Kotlin module is under active parity work with the Python implementation in `../`.
- See [`Plan.md`](./Plan.md), [`PARITY_MATRIX.md`](./PARITY_MATRIX.md), and [`MIGRATION.md`](./MIGRATION.md) for detailed status.
