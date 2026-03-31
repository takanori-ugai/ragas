package ragas.metrics.primitives

import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private class FakeLlm(
        private val outputs: List<String>,
    ) : BaseRagasLlm {
        private var cursor = 0
        override var runConfig: RunConfig = RunConfig()

        override suspend fun generateText(
            prompt: String,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult = LlmResult(listOf(LlmGeneration(outputs[cursor++])))
    }
}
