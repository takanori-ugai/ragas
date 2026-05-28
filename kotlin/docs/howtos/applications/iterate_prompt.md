<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Iterate Prompt

Run repeated prompt experiments and compare result CSVs.

```kotlin
// @compile
data class SupportRow(val id: Int, val text: String, val expectedLabels: String, val expectedPriority: String)

suspend fun runPrompt(text: String, promptTemplate: String): String = TODO()
```

```kotlin
// @compile
fun labelsExactMatch(prediction: String, expectedLabels: String): String =
    if (prediction.contains(expectedLabels)) "pass" else "fail"

fun priorityAccuracy(prediction: String, expectedPriority: String): String =
    if (prediction.contains(expectedPriority)) "pass" else "fail"
```

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment

val runner =
    experiment<SupportRow>(backend = LocalCsvBackend("experiments"), namePrefix = "prompt-iterate") { row ->
        val response = runPrompt(row.text, promptTemplate = "promptv1")
        mapOf(
            "id" to row.id,
            "response" to response,
            "labels_score" to labelsExactMatch(response, row.expectedLabels),
            "priority_score" to priorityAccuracy(response, row.expectedPriority),
        )
    }
```
