package ragas

import kotlinx.coroutines.runBlocking
import ragas.embeddings.BaseRagasEmbedding
import ragas.metrics.collections.SemanticSimilarityMetric
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SemanticSimilarityMetricParityTest {
    @Test
    fun defaultPathRequiresEmbeddingsForParitySemantics() =
        runBlocking {
            val metric = SemanticSimilarityMetric()
            val sample = SingleTurnSample(reference = "A", response = "B")

            val error = assertFailsWith<IllegalStateException> { metric.singleTurnAscore(sample) }
            assertEquals(
                "SemanticSimilarityMetric requires embeddings for parity semantics. " +
                    "Set embeddings or use SemanticSimilarityMetric(allowLexicalFallback = true).",
                error.message,
            )
        }

    @Test
    fun embeddingsPathUsesCosineSimilarityWithoutZeroOneClamping() =
        runBlocking {
            val metric =
                SemanticSimilarityMetric().also {
                    it.embeddings =
                        ScriptedSemanticSimilarityEmbedding(
                            vectors =
                                mapOf(
                                    "A" to listOf(1f, 0f),
                                    "B" to listOf(-1f, 0f),
                                ),
                        )
                }
            val sample = SingleTurnSample(reference = "A", response = "B")

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertEquals(-1.0, score, 1e-9)
        }

    @Test
    fun embeddingsPathSupportsThresholdBinarization() =
        runBlocking {
            val metric =
                SemanticSimilarityMetric(threshold = 0.9).also {
                    it.embeddings =
                        ScriptedSemanticSimilarityEmbedding(
                            vectors =
                                mapOf(
                                    "A" to listOf(1f, 0f),
                                    "A2" to listOf(1f, 0f),
                                ),
                        )
                }
            val sample = SingleTurnSample(reference = "A", response = "A2")

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0, score, 1e-9)
        }
}

private class ScriptedSemanticSimilarityEmbedding(
    private val vectors: Map<String, List<Float>>,
) : BaseRagasEmbedding {
    override suspend fun embedText(text: String): List<Float> = vectors[text] ?: listOf(0f, 0f)
}
