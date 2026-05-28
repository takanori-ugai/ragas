<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Improve RAG Quickstart

Compare different RAG strategies using the same evaluation dataset.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.rageval.EvalsKt
```

## RAG Interface

```kotlin
enum class RagMode { NAIVE, AGENTIC }

interface RagSystem {
    suspend fun query(question: String, topK: Int = 3): String
}
```

## Retrieval Stub

```kotlin
class CustomRetriever(private val documents: List<String>) {
    fun retrieve(query: String, topK: Int = 3): List<String> = documents.take(topK)
}
```

## Correctness Metric

```kotlin
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val correctnessMetric =
    DiscreteMetric(
        name = "correctness",
        prompt = "Compare response with expected answer and return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )
```

## Pass-Rate Utility

```kotlin
import java.io.File

fun passRate(csvPath: String): Double {
    val rows = File(csvPath).readLines().drop(1)
    if (rows.isEmpty()) return 0.0
    val pass = rows.count { it.contains(",pass") }
    return pass.toDouble() / rows.size.toDouble()
}
```
