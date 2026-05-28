<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LlamaIndex Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LlamaIndexIntegration
import ragas.integrations.LlamaIndexRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(LlamaIndexRecord(query = "What is Ragas?", response = "Ragas is an eval framework."))
val result = LlamaIndexIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
