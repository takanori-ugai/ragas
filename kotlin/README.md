# Ragas Kotlin

Kotlin port of the Ragas evaluation toolkit.

This module provides:
- evaluation pipeline (`evaluate` / `aevaluate`)
- default single-turn metrics
- WS3 Tier-1 collection metric accessor (`tier1Metrics()`)
- WS3 Tier-2 agent/tool-call + workflow metric accessor (`tier2Metrics()`)
- prompt subsystem (Python-style prompt framing + persistence)
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
