package ragas.examples.agent

import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.llms.LangChain4jLlm
import ragas.metrics.primitives.NumericMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import java.io.File

private data class AgentRow(
    val expression: String,
    val expected: Double,
)

/**
 * Runs a small agent-evaluation experiment and persists CSV results.
 */
fun main() =
    runBlocking {
        val apiKey =
            System.getenv("OPENAI_API_KEY")
                ?: error("OPENAI_API_KEY is required to run ragas.examples.agent.EvalsKt")
        val chatModel =
            OpenAiChatModel
                .builder()
                .apiKey(apiKey)
                .modelName("gpt-5.4-mini")
                .temperature(0.0)
                .build()
        val llm =
            LangChain4jLlm(
                model = chatModel,
                runConfig = RunConfig(timeoutSeconds = 90),
            )
        val mathAgent = MathToolsAgent(logDir = "logs")

        val dataset =
            listOf(
                AgentRow("(2 + 3) * (4 - 1)", 15.0),
                AgentRow("5 * (6 + 2)", 40.0),
                AgentRow("10 - (3 + 2)", 5.0),
            )

        LocalCsvBackend("datasets").saveDataset(
            name = "test_dataset",
            data = dataset.map { row -> mapOf("expression" to row.expression, "expected" to row.expected) },
        )

        val correctnessMetric =
            NumericMetric(
                name = "correctness",
                prompt =
                    """
                    Compare predicted result with expected result.
                    Return 1.0 when |prediction - expected| < 1e-5, else return 0.0.
                    Prediction: {response}
                    Expected: {reference}
                    """.trimIndent(),
                llm = llm,
                allowedRange = 0.0..1.0,
            )

        val runner =
            experiment<AgentRow>(backend = LocalCsvBackend("experiments"), namePrefix = "agent-eval") { row ->
                val prediction = mathAgent.solve(row.expression)
                val scoreRaw =
                    correctnessMetric.singleTurnAscore(
                        SingleTurnSample(
                            userInput = row.expression,
                            response = prediction.result.toString(),
                            reference = row.expected.toString(),
                        ),
                    )
                val score = (scoreRaw as? Number)?.toDouble() ?: 0.0
                mapOf(
                    "expression" to row.expression,
                    "expected_result" to row.expected,
                    "prediction" to prediction.result,
                    "log_file" to prediction.logFile,
                    "correctness" to score,
                )
            }

        val result = runner.arun(dataset = dataset, name = "baseline")
        val csvPath = File("experiments/${result.name}.csv")
        println("Experiment completed successfully!")
        println("Experiment name: ${result.name}")
        println("Experiment results saved to: ${csvPath.absolutePath}")
    }
