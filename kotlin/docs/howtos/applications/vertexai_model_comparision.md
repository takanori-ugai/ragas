<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Vertex AI Model Comparison

Compare two model outputs on the same evaluation dataset.

```kotlin
// @compile
import ragas.evaluate
import ragas.defaultMetrics
import ragas.llms.BaseRagasLlm
import ragas.model.EvaluationDataset
import ragas.model.Sample

val datasetA: EvaluationDataset<out Sample> = TODO("Provide dataset A")
val datasetB: EvaluationDataset<out Sample> = TODO("Provide dataset B")
val llmA: BaseRagasLlm = TODO("Configure model A")
val llmB: BaseRagasLlm = TODO("Configure model B")
val resultA = evaluate(dataset = datasetA, metrics = defaultMetrics(), llm = llmA)
val resultB = evaluate(dataset = datasetB, metrics = defaultMetrics(), llm = llmB)

println(resultA.scores)
println(resultB.scores)
```
