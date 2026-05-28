<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LangSmith Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LangsmithIntegration
import ragas.integrations.LangsmithRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(LangsmithRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = LangsmithIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
