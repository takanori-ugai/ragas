<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LlamaIndex Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LlamaIndexIntegration
import ragas.integrations.LlamaIndexRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(LlamaIndexRecord(query = "Q", response = "A"))
LlamaIndexIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
```
