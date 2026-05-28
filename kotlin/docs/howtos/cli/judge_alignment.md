<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Judge Alignment Quickstart

Measure agreement between human labels and an LLM judge.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.prompteval.EvalsKt
```

## Judge Metrics

```kotlin
// @compile
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val baselineJudge =
    DiscreteMetric(
        name = "accuracy_baseline",
        prompt = "Check if response covers grading notes. Return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )

val improvedJudge =
    DiscreteMetric(
        name = "accuracy_improved",
        prompt = "Check coverage including abbreviations and business shorthand. Return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )
```

## Alignment Label

```kotlin
// @compile
fun alignmentLabel(llmJudgment: String, humanJudgment: String): String =
    if (llmJudgment == humanJudgment) "aligned" else "misaligned"
```
