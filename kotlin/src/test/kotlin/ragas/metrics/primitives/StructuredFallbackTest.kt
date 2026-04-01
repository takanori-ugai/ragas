package ragas.metrics.primitives

import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.model.SingleTurnSample
import ragas.optimizers.OptimizationDataset
import ragas.optimizers.OptimizationExample
import ragas.optimizers.Optimizer
import ragas.optimizers.OptimizerConfig
import ragas.optimizers.OptimizerOutcome
import ragas.optimizers.OptimizerPrompt
import ragas.optimizers.PromptObjectEvaluator
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredFallbackTest {
    @Test
    fun numericMetricParsesJsonObjectFallback() =
        runBlocking {
            val llm = FakeLlm(listOf("{\"value\": 0.75}"))
            val metric = NumericMetric("test", "prompt", llm)
            val sample = SingleTurnSample(userInput = "u", response = "r")

            assertEquals(0.75, metric.singleTurnAscore(sample))
        }

    @Test
    fun discreteMetricParsesJsonObjectFallback() =
        runBlocking {
            val llm = FakeLlm(listOf("{\"value\": \"pass\"}"))
            val metric = DiscreteMetric("test", "prompt", llm, allowedValues = listOf("pass", "fail"))
            val sample = SingleTurnSample(userInput = "u", response = "r")

            assertEquals("pass", metric.singleTurnAscore(sample))
        }

    @Test
    fun rankingMetricParsesJsonObjectFallback() =
        runBlocking {
            val llm = FakeLlm(listOf("{\"items\": [\"a\", \"b\"]}"))
            val metric = RankingMetric("test", "prompt", llm, expectedSize = 2)
            val sample = SingleTurnSample(userInput = "u", response = "r")

            assertEquals(listOf("a", "b"), metric.singleTurnAscore(sample))
        }

    @Test
    fun numericMetricAcceptsOptimizedPromptObject() =
        runBlocking {
            val llm = FakeLlm(listOf("{\"value\": 0.91}"))
            val metric = NumericMetric("test", "prompt", llm)
            val dataset =
                OptimizationDataset(
                    metricName = "test",
                    examples = listOf(OptimizationExample(promptInput = mapOf("q" to "x"), expectedOutput = "y")),
                )
            val optimizer =
                object : Optimizer {
                    override fun optimizePrompts(
                        dataset: OptimizationDataset,
                        initialPrompts: List<OptimizerPrompt>,
                        evaluator: PromptObjectEvaluator,
                        config: OptimizerConfig,
                    ): OptimizerOutcome =
                        OptimizerOutcome(
                            optimizedPrompt = OptimizerPrompt.Text("Optimized prompt using concise JSON output."),
                            metadata = mapOf("optimizer" to "fake"),
                        )
                }

            metric.optimizePrompt(
                optimizer = optimizer,
                dataset = dataset,
                evaluator = PromptObjectEvaluator { _, _ -> 1.0 },
            )

            val score = metric.singleTurnAscore(SingleTurnSample(userInput = "u", response = "r"))
            assertEquals(0.91, score)
            assertTrue(llm.seenPrompts.any { "Optimized prompt using concise JSON output." in it })
        }

    private class FakeLlm(
        private val outputs: List<String>,
    ) : BaseRagasLlm {
        private var cursor = 0
        val seenPrompts: MutableList<String> = mutableListOf()
        override var runConfig: RunConfig = RunConfig()

        override suspend fun generateText(
            prompt: String,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult {
            seenPrompts += prompt
            return LlmResult(listOf(LlmGeneration(outputs[cursor++])))
        }
    }
}
