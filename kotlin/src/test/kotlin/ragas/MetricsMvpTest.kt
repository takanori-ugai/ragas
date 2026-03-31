package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ragas.evaluation.evaluate
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.primitives.DiscreteMetric
import ragas.metrics.primitives.NumericMetric
import ragas.metrics.primitives.RankingMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class MetricsMvpTest {
    @Test
    fun evaluateUsesDefaultSingleTurnMetricsWhenNotProvided() {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(
                        userInput = "What is Kotlin?",
                        response = "Kotlin is a JVM language.",
                        retrievedContexts = listOf("Kotlin is a statically typed language for the JVM."),
                        referenceContexts = listOf("Kotlin targets the JVM and is statically typed."),
                    ),
                ),
            )

        val result = evaluate(dataset = dataset, metrics = null)
        val scoreRow = result.scores.first()

        assertTrue(scoreRow.containsKey("answer_relevancy"))
        assertTrue(scoreRow.containsKey("context_precision"))
        assertTrue(scoreRow.containsKey("faithfulness"))
        assertTrue(scoreRow.containsKey("context_recall"))
    }

    @Test
    fun primitiveMetricsParseLlmOutputs() = runBlocking {
        val llm =
            FakeLlm(
                outputs =
                    listOf(
                        "0.83",
                        "pass",
                        "1) alpha\n2) beta\n3) gamma",
                    ),
            )

        val sample =
            SingleTurnSample(
                userInput = "u",
                response = "r",
                reference = "ref",
                retrievedContexts = listOf("ctx"),
            )

        val numeric =
            NumericMetric(
                name = "numeric_test",
                prompt = "Rate: {response}",
                llm = llm,
                allowedRange = 0.0..1.0,
            )
        val discrete =
            DiscreteMetric(
                name = "discrete_test",
                prompt = "Decide: {response}",
                llm = llm,
                allowedValues = listOf("pass", "fail"),
            )
        val ranking =
            RankingMetric(
                name = "ranking_test",
                prompt = "Rank: {response}",
                llm = llm,
                expectedSize = 3,
            )

        assertEquals(0.83, numeric.singleTurnAscore(sample))
        assertEquals("pass", discrete.singleTurnAscore(sample))
        assertEquals(listOf("alpha", "beta", "gamma"), ranking.singleTurnAscore(sample))
    }
}

private class FakeLlm(
    private val outputs: List<String>,
) : BaseRagasLlm {
    private var cursor: Int = 0

    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        val next = outputs[cursor]
        cursor += 1
        return LlmResult(generations = listOf(LlmGeneration(text = next)))
    }
}
