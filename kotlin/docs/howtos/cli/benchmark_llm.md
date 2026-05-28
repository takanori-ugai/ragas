<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LLM Benchmarking Quickstart

Benchmark multiple model configurations on one prompt task.

## Run Baseline

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.prompteval.EvalsKt
```

## Prompt Function

```kotlin
// @compile
suspend fun runPrompt(profile: String, modelName: String): String {
    // Return JSON text such as {"discount_percentage": 15}
    TODO()
}
```

## Discrete Accuracy Metric

```kotlin
// @compile
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val discountAccuracy =
    DiscreteMetric(
        name = "discount_accuracy",
        prompt = "Compare predicted discount JSON with expected value. Return correct/incorrect.",
        llm = llm,
        allowedValues = listOf("correct", "incorrect"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )
```

## Compare CSV Results

```kotlin
// @compile
import java.io.File

fun accuracy(csvPath: String): Double {
    val lines = File(csvPath).readLines()
    if (lines.size <= 1) return 0.0
    val idx = lines.first().split(",").indexOf("score")
    val rows = lines.drop(1)
    return rows.count { row -> row.split(",").getOrNull(idx) == "correct" }.toDouble() / rows.size.toDouble()
}
```
