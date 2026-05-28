<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LangChain Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LangChainIntegration
import ragas.integrations.LangChainRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")

val records = listOf(LangChainRecord(question = "What is Ragas?", answer = "Ragas is an eval framework."))
val result = LangChainIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
