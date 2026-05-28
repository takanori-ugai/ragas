<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Align LLM as Judge

Align a judge metric with human labels and track alignment gains.

```kotlin
// @compile
data class JudgeRow(
    val response: String,
    val gradingNotes: String,
    val humanLabel: String,
)
```

```kotlin
// @compile
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val judgeMetric =
    DiscreteMetric(
        name = "accuracy",
        prompt = "Evaluate response vs grading notes and return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )

fun alignment(judgeLabel: String, humanLabel: String): String =
    if (judgeLabel == humanLabel) "aligned" else "misaligned"
```

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.model.SingleTurnSample

val runner =
    experiment<JudgeRow>(backend = LocalCsvBackend("experiments"), namePrefix = "judge-alignment") { row ->
        val judgeLabel =
            judgeMetric.singleTurnAscore(
                SingleTurnSample(response = row.response, reference = row.gradingNotes),
            ).toString()
        mapOf(
            "response" to row.response,
            "human_label" to row.humanLabel,
            "judge_label" to judgeLabel,
            "alignment" to alignment(judgeLabel, row.humanLabel),
        )
    }
```
