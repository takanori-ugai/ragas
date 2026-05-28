package ragas.examples.workflow

import kotlinx.coroutines.runBlocking
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.model.SingleTurnSample
import java.io.File

private data class WorkflowRow(
    val email: String,
    val passCriteria: String,
)

/**
 * Runs a small workflow-evaluation experiment and persists CSV results.
 */
fun main() =
    runBlocking {
        val llm = createOpenAiLlm()
        val workflowClient = WorkflowClient(llm)

        val dataset =
            listOf(
                WorkflowRow(
                    email = "Hi, I'm getting error code XYZ-123 when using version 2.1.4 of your software. Please help!",
                    passCriteria =
                        "category Bug Report; product_version 2.1.4; error_code XYZ-123; " +
                            "response references both version and error code",
                ),
                WorkflowRow(
                    email = "I need to dispute invoice #INV-2024-001 for 299.99 dollars. The charge seems incorrect.",
                    passCriteria =
                        "category Billing; invoice_number INV-2024-001; amount 299.99; " +
                            "response references invoice and dispute process",
                ),
            )

        LocalCsvBackend("datasets").saveDataset(
            name = "test_dataset",
            data = dataset.map { row -> mapOf("email" to row.email, "pass_criteria" to row.passCriteria) },
        )

        val responseQualityMetric =
            DiscreteMetric(
                name = "response_quality",
                prompt =
                    """
                    Evaluate the response against pass criteria and return "pass" or "fail".
                    Pass criteria: {reference}
                    Response: {response}
                    """.trimIndent(),
                llm = llm,
                allowedValues = listOf("pass", "fail"),
                requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
            )

        val runner =
            experiment<WorkflowRow>(backend = LocalCsvBackend("experiments"), namePrefix = "workflow-eval") { row ->
                val response = workflowClient.processEmail(row.email)
                val score =
                    responseQualityMetric.singleTurnAscore(
                        SingleTurnSample(
                            userInput = row.email,
                            response = response.responseTemplate,
                            reference = row.passCriteria,
                        ),
                    )
                mapOf(
                    "email" to row.email,
                    "pass_criteria" to row.passCriteria,
                    "response" to response.responseTemplate,
                    "score" to score,
                )
            }

        val result = runner.arun(dataset = dataset, name = "baseline")
        val csvPath = File("experiments/${result.name}.csv")
        println("Experiment completed successfully!")
        println("Experiment name: ${result.name}")
        println("Experiment results saved to: ${csvPath.absolutePath}")
    }
