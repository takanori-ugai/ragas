<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# LlamaIndex Agent Evaluation Quickstart

Evaluate tool-call correctness for agent workflows.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.agent.EvalsKt
```

## Tool Call Schema

```kotlin
data class ToolCall(
    val name: String,
    val arguments: Map<String, String>,
)
```

## F1 Tool-Call Metric

```kotlin
fun toolCallF1(predicted: List<ToolCall>, expected: List<ToolCall>): Double {
    if (predicted.isEmpty() && expected.isEmpty()) return 1.0
    val p = predicted.toSet()
    val e = expected.toSet()
    val tp = p.intersect(e).size.toDouble()
    val precision = if (p.isEmpty()) 0.0 else tp / p.size.toDouble()
    val recall = if (e.isEmpty()) 0.0 else tp / e.size.toDouble()
    return if (precision + recall == 0.0) 0.0 else (2.0 * precision * recall) / (precision + recall)
}
```
