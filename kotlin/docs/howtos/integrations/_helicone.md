<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Helicone Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.HeliconeIntegration
import ragas.integrations.HeliconeRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(HeliconeRecord(input = "Q", output = "A"))
HeliconeIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
```
