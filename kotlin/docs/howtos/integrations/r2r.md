<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# R2R Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.R2RIntegration
import ragas.integrations.R2RRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(R2RRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = R2RIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
