<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Workflow Evaluation Quickstart

Evaluate a multi-step support workflow (classify, extract, respond).

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.workflow.EvalsKt
```

## Workflow Usage

```kotlin
interface WorkflowClientLike {
    suspend fun processEmail(email: String): WorkflowResult
}

data class WorkflowResult(val category: String, val responseTemplate: String)

val workflow: WorkflowClientLike = TODO("Provide workflow implementation")
val result = workflow.processEmail("I found a bug in version 2.1.4 with error XYZ-123")
println(result.category)
println(result.responseTemplate)
```

## Dataset

```kotlin
data class WorkflowRow(val email: String, val passCriteria: String)

val dataset =
    listOf(
        WorkflowRow(
            email = "I need to dispute invoice INV-2024-001 for 299.99",
            passCriteria = "category Billing; invoice_number INV-2024-001; amount 299.99",
        ),
    )
```

## Metric

```kotlin
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val responseQuality =
    DiscreteMetric(
        name = "response_quality",
        prompt = "Evaluate response against pass criteria and return pass/fail.",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )
```
