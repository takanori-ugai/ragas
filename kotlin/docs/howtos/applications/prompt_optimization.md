<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Prompt Optimization

Evaluate baseline prompt, identify error classes, and optimize prompt versions.

```kotlin
data class PromptEvalRow(
    val question: String,
    val expected: String,
    val response: String,
)
```

```kotlin
import ragas.evaluate
import ragas.defaultMetrics
import ragas.llms.BaseRagasLlm
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample

val rows: List<PromptEvalRow> = TODO("Provide prompt evaluation rows")
val llm: BaseRagasLlm = TODO("Configure LLM")
val dataset =
    EvaluationDataset(
        samples =
            rows.map {
                SingleTurnSample(
                    userInput = it.question,
                    response = it.response,
                    reference = it.expected,
                )
            },
    )

val result = evaluate(dataset = dataset, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
