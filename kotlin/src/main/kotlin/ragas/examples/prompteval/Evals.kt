package ragas.examples.prompteval

import kotlinx.coroutines.runBlocking
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.model.SingleTurnSample
import java.io.File

private data class PromptRow(
    val text: String,
    val label: String,
)

private suspend fun runWithModel(
    dataset: List<PromptRow>,
    modelName: String,
) {
    val llm = createOpenAiLlm(modelName = modelName)
    val accuracyMetric =
        DiscreteMetric(
            name = "accuracy",
            prompt =
                """
                Check if the predicted sentiment matches the expected sentiment.
                Prediction: {response}
                Expected: {reference}
                Return "pass" or "fail".
                """.trimIndent(),
            llm = llm,
            allowedValues = listOf("pass", "fail"),
            requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
        )

    val runner =
        experiment<PromptRow>(backend = LocalCsvBackend("experiments"), namePrefix = "prompt-eval-$modelName") { row ->
            val response = runPrompt(row.text, modelName = modelName)
            val score =
                accuracyMetric.singleTurnAscore(
                    SingleTurnSample(
                        userInput = row.text,
                        response = response,
                        reference = row.label,
                    ),
                )
            mapOf("text" to row.text, "label" to row.label, "response" to response, "score" to score)
        }
    runner.arun(dataset = dataset, name = "baseline")
}

/**
 * Runs a small prompt-evaluation experiment and persists CSV results.
 */
fun main() =
    runBlocking {
        val llm = createOpenAiLlm()

        val dataset =
            listOf(
                PromptRow("I loved the movie! It was fantastic.", "positive"),
                PromptRow("The movie was terrible and boring.", "negative"),
                PromptRow("It was an average film, nothing special.", "positive"),
                PromptRow("Absolutely amazing! Best movie of the year.", "positive"),
            )

        LocalCsvBackend("datasets").saveDataset(
            name = "test_dataset",
            data = dataset.map { row -> mapOf("text" to row.text, "label" to row.label) },
        )

        val accuracyMetric =
            DiscreteMetric(
                name = "accuracy",
                prompt =
                    """
                    Check if the predicted sentiment matches the expected sentiment.
                    Prediction: {response}
                    Expected: {reference}
                    Return "pass" or "fail".
                    """.trimIndent(),
                llm = llm,
                allowedValues = listOf("pass", "fail"),
                requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
            )

        val runner =
            experiment<PromptRow>(backend = LocalCsvBackend("experiments"), namePrefix = "prompt-eval") { row ->
                val response = runPrompt(row.text)
                val score =
                    accuracyMetric.singleTurnAscore(
                        SingleTurnSample(
                            userInput = row.text,
                            response = response,
                            reference = row.label,
                        ),
                    )
                mapOf(
                    "text" to row.text,
                    "label" to row.label,
                    "response" to response,
                    "score" to score,
                )
            }

        val result = runner.arun(dataset = dataset, name = "baseline")
        val csvPath = File("experiments/${result.name}.csv")
        println("Experiment completed successfully!")
        println("Experiment name: ${result.name}")
        println("Experiment results saved to: ${csvPath.absolutePath}")
    }
