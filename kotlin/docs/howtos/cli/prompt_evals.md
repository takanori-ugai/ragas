<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Prompt Evaluation Quickstart

Evaluate prompt quality on a labeled sentiment dataset.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.prompteval.EvalsKt
```

## Prompt Function

```kotlin
// @compile
suspend fun runPrompt(text: String): String {
    // Must return "positive" or "negative".
    TODO()
}
```

## Dataset

```kotlin
// @compile
data class PromptRow(val text: String, val label: String)

val dataset =
    listOf(
        PromptRow("I loved the movie! It was fantastic.", "positive"),
        PromptRow("The movie was terrible and boring.", "negative"),
    )
```

## Pass/Fail Metric

```kotlin
// @compile
fun passFail(prediction: String, expected: String): String = if (prediction == expected) "pass" else "fail"
```

## Optional Numeric Metric

```kotlin
// @compile
import ragas.metrics.MetricType
import ragas.metrics.primitives.NumericMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val confidenceMetric =
    NumericMetric(
        name = "confidence",
        prompt = "Rate confidence from 1 to 5 for this classification: {response}",
        llm = llm,
        allowedRange = 1.0..5.0,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
    )
```
