package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluation.evaluate
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithEmbeddings
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class E2EEvaluateFlowTest {
    @Test
    fun evaluateInjectsLlmAndEmbeddingsAndAggregatesScores() {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(userInput = "u1", response = "r1", reference = "ref1"),
                    SingleTurnSample(userInput = "u2", response = "r2", reference = "ref2"),
                ),
            )

        val llm = FakeE2ELlm("0.7")
        val embeddings = FakeE2EEmbedding(listOf(0.1f, 0.2f, 0.3f))
        val metric = InjectedDependenciesMetric()

        val result =
            evaluate(
                dataset = dataset,
                metrics = listOf(metric),
                llm = llm,
                embeddings = embeddings,
            )

        assertEquals(2, result.scores.size)
        assertTrue((result.scores[0]["injected_metric"] as Double) > 0.0)
        assertTrue((result.scores[1]["injected_metric"] as Double) > 0.0)
        assertEquals(null, metric.llm)
        assertEquals(null, metric.embeddings)
        assertEquals(2, llm.calls)
        assertEquals(2, embeddings.calls)
    }
}

private class InjectedDependenciesMetric : BaseMetric(
    name = "injected_metric",
    requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
    outputType = MetricOutputType.CONTINUOUS,
), SingleTurnMetric, MetricWithLlm, MetricWithEmbeddings {
    override var llm: BaseRagasLlm? = null
    override var embeddings: BaseRagasEmbedding? = null

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val llmInstance = checkNotNull(llm)
        val embeddingInstance = checkNotNull(embeddings)
        val llmScore = llmInstance.generateText(sample.response.orEmpty()).generations.first().text.toDouble()
        val embeddingScore = embeddingInstance.embedText(sample.response.orEmpty()).sum().toDouble()
        return llmScore + embeddingScore
    }
}

private class FakeE2ELlm(
    private val output: String,
) : BaseRagasLlm {
    var calls: Int = 0
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        calls += 1
        return LlmResult(generations = listOf(LlmGeneration(output)))
    }
}

private class FakeE2EEmbedding(
    private val vector: List<Float>,
) : BaseRagasEmbedding {
    var calls: Int = 0

    override suspend fun embedText(text: String): List<Float> {
        calls += 1
        return vector
    }
}
