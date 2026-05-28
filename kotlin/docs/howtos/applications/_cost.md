<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Cost Tracking

Track token/cost usage during evaluation runs.

```kotlin
// @compile
import ragas.evaluate
import ragas.evaluation.TokenUsageParser
import ragas.model.EvaluationDataset
import ragas.metrics.Metric
import ragas.model.Sample
import ragas.llms.BaseRagasLlm

val dataset: EvaluationDataset<out Sample> = TODO("Provide evaluation dataset")
val metrics: List<Metric> = TODO("Provide metrics")
val llm: BaseRagasLlm = TODO("Configure LLM")
val parser: TokenUsageParser = { _, _ -> null } // plug provider-specific parser
val result = evaluate(dataset = dataset, metrics = metrics, llm = llm, tokenUsageParser = parser)
println(result.scores)
```

```kotlin
// @compile
import ragas.testset.synthesizers.TestsetGenerator
import ragas.testset.transforms.defaultTransformsForDocuments

val docs: List<String> = TODO("Provide source documents")
val generator = TestsetGenerator()
val testset = generator.generateFromDocuments(documents = docs, testsetSize = 20, transforms = defaultTransformsForDocuments(docs))
```
