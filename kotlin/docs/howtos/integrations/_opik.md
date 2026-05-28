<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Opik Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.OpikIntegration
import ragas.integrations.OpikRecord
import ragas.integrations.unsupportedIntegration
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(OpikRecord(input = "Q", output = "A"))
val dataset = OpikIntegration.toDataset(records)
val metricRows = defaultMetrics()
unsupportedIntegration("opik")
```
