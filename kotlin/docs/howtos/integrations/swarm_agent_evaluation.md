<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Swarm Integration

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.SwarmIntegration
import ragas.integrations.SwarmRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(SwarmRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = SwarmIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
