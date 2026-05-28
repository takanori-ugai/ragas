<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Evaluate and Improve RAG

Start with a baseline RAG pipeline, evaluate, analyze failures, and iterate.

```kotlin
interface RagSystem {
    suspend fun query(question: String, topK: Int = 4): String
}
```

```kotlin
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val correctnessMetric =
    DiscreteMetric(
        name = "correctness",
        prompt = "Check whether response is correct and grounded. Return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )
```

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.model.SingleTurnSample

data class RagEvalRow(val question: String, val expectedAnswer: String)
val rag: RagSystem = TODO("Provide RAG implementation")

val runner =
    experiment<RagEvalRow>(backend = LocalCsvBackend("experiments"), namePrefix = "rag-improve") { row ->
        val response = rag.query(row.question)
        val score = correctnessMetric.singleTurnAscore(SingleTurnSample(userInput = row.question, response = response, reference = row.expectedAnswer))
        mapOf("question" to row.question, "expected_answer" to row.expectedAnswer, "response" to response, "score" to score)
    }
```
