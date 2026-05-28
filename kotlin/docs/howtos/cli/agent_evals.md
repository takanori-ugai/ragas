<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Agent Evaluation Quickstart

The `agent_evals` template evaluates an agent's final answer and tool behavior.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.agent.EvalsKt
```

## Agent Call

```kotlin
interface MathAgent {
    suspend fun solve(expression: String): Double
}

val mathAgent: MathAgent = TODO("Provide MathAgent implementation")
val output = mathAgent.solve("(2 + 3) * (6 - 2)")
```

## Correctness Metric

```kotlin
// @compile
import kotlin.math.abs

fun exactNumericScore(prediction: Double, expected: Double): Double =
    if (abs(prediction - expected) < 1e-5) 1.0 else 0.0
```

## Experiment Pattern

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment

data class AgentRow(val expression: String, val expected: Double)
val mathAgent: MathAgent = TODO("Provide MathAgent implementation")

val runner =
    experiment<AgentRow>(backend = LocalCsvBackend("experiments"), namePrefix = "agent-eval") { row ->
        val predicted = mathAgent.solve(row.expression)
        mapOf(
            "expression" to row.expression,
            "expected" to row.expected,
            "predicted" to predicted,
            "score" to exactNumericScore(predicted, row.expected),
        )
    }
```
