package ragas

import kotlinx.coroutines.runBlocking
import ragas.metrics.defaults.FaithfulnessMetric
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals

class FaithfulnessMetricTest {
    @Test
    fun crossContextSplicedSentenceIsNotMarkedSupported() =
        runBlocking {
            val metric = FaithfulnessMetric()
            val sample =
                SingleTurnSample(
                    response = "Kotlin runs on JVM and blue whales migrate oceans.",
                    retrievedContexts =
                        listOf(
                            "Kotlin runs on JVM.",
                            "Blue whales migrate oceans.",
                        ),
                )

            assertEquals(0.0, metric.singleTurnAscore(sample))
        }

    @Test
    fun sentenceSupportedBySingleContextScoresAsSupported() =
        runBlocking {
            val metric = FaithfulnessMetric()
            val sample =
                SingleTurnSample(
                    response = "Kotlin runs on JVM.",
                    retrievedContexts =
                        listOf(
                            "Kotlin runs on JVM with strong tooling.",
                            "Python uses indentation.",
                        ),
                )

            assertEquals(1.0, metric.singleTurnAscore(sample))
        }
}
