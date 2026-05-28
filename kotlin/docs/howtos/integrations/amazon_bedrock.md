<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Amazon Bedrock Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.BedrockIntegration
import ragas.integrations.BedrockRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(BedrockRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = BedrockIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
