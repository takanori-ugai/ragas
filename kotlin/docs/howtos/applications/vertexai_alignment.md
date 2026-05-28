<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Vertex AI Alignment

Align a custom judge metric using human-labeled examples.

```kotlin
// @compile
fun alignmentScore(human: List<Int>, llmScores: List<Int>): Double {
    require(human.size == llmScores.size)
    val correct = human.zip(llmScores).count { (h, l) -> h == l }
    return correct.toDouble() / human.size.toDouble()
}
```

```kotlin
// @compile
import ragas.evaluate
import ragas.metrics.Metric
import ragas.llms.BaseRagasLlm
import ragas.model.EvaluationDataset
import ragas.model.Sample

val dataset: EvaluationDataset<out Sample> = TODO("Provide evaluation dataset")
val metrics: List<Metric> = TODO("Provide metrics")
val llm: BaseRagasLlm = TODO("Configure LLM")
val result = evaluate(dataset = dataset, metrics = metrics, llm = llm)
println(result.scores)
```
