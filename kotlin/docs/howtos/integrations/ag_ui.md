<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# AG-UI Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.AgUiIntegration
import ragas.integrations.AgUiRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(AgUiRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = AgUiIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
