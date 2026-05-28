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

val dataset: EvaluationDataset<out Sample> = TODO("Provide shared evaluation dataset")
val llmA: BaseRagasLlm = TODO("Configure model A")
val llmB: BaseRagasLlm = TODO("Configure model B")
val resultA = evaluate(dataset = dataset, metrics = defaultMetrics(), llm = llmA)
val resultB = evaluate(dataset = dataset, metrics = defaultMetrics(), llm = llmB)

println(resultA.scores)
println(resultB.scores)
```
