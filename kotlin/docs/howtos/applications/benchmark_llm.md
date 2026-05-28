<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Benchmark LLMs

Compare model variants on the same task and dataset.

```kotlin
// @compile
data class BenchmarkRow(val input: String, val expected: String)

suspend fun runPrompt(input: String, modelName: String): String = TODO()
```

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment

val runner =
    experiment<BenchmarkRow>(backend = LocalCsvBackend("experiments"), namePrefix = "llm-benchmark") { row ->
        val response = runPrompt(row.input, modelName = "gpt-5.4-mini")
        val normalizedResponse = response.trim().lowercase()
        val normalizedExpected = row.expected.trim().lowercase()
        val score = if (normalizedResponse == normalizedExpected) "correct" else "incorrect"
        mapOf("input" to row.input, "expected" to row.expected, "response" to response, "score" to score)
    }
```
