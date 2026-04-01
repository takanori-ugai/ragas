package ragas.metrics.defaults

import kotlinx.coroutines.runBlocking
import ragas.embeddings.BaseRagasEmbedding
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerRelevancyMetricTest {
    @Test
    fun directAnswerScoresHigherThanOffTopicAnswer() =
        runBlocking {
            val metric = AnswerRelevancyMetric()

            val aligned =
                SingleTurnSample(
                    userInput = "What is the capital of France?",
                    response = "The capital of France is Paris.",
                )
            val offTopic =
                SingleTurnSample(
                    userInput = "What is the capital of France?",
                    response = "Kotlin is a statically typed language for the JVM.",
                )

            val alignedScore = (metric.singleTurnAscore(aligned) as Number).toDouble()
            val offTopicScore = (metric.singleTurnAscore(offTopic) as Number).toDouble()

            assertTrue(alignedScore > offTopicScore, "aligned=$alignedScore offTopic=$offTopicScore")
        }

    @Test
    fun noncommittalAnswerScoresZero() =
        runBlocking {
            val metric = AnswerRelevancyMetric()
            val sample =
                SingleTurnSample(
                    userInput = "What is the capital of France?",
                    response = "I don't know.",
                )

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertEquals(0.0, score)
        }

    @Test
    fun embeddingSimilarityContributesToScoreWhenConfigured() =
        runBlocking {
            val metric =
                AnswerRelevancyMetric(
                    embeddings = ControlledEmbedding(),
                )
            val sample =
                SingleTurnSample(
                    userInput = "What is the square root of 64?",
                    response = "8",
                )

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertTrue(score > 0.6, "expected embedding-backed similarity to raise score, got $score")
        }

    private class ControlledEmbedding : BaseRagasEmbedding {
        override suspend fun embedText(text: String): List<Float> =
            when (text.trim()) {
                "What is the square root of 64?" -> listOf(1.0f, 0.0f, 0.0f)
                "8" -> listOf(1.0f, 0.0f, 0.0f)
                else -> listOf(0.0f, 1.0f, 0.0f)
            }
    }
}

