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
    val predictedCounts = predicted.groupingBy { it }.eachCount()
    val expectedCounts = expected.groupingBy { it }.eachCount()
    val truePositives =
        expectedCounts.entries.sumOf { (toolCall, expectedCount) ->
            minOf(predictedCounts[toolCall] ?: 0, expectedCount)
        }.toDouble()
    val precision = if (predicted.isEmpty()) 0.0 else truePositives / predicted.size.toDouble()
    val recall = if (expected.isEmpty()) 0.0 else truePositives / expected.size.toDouble()
    return if (precision + recall == 0.0) 0.0 else (2.0 * precision * recall) / (precision + recall)
}
```
