package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CollectionsApiParityGapsTest {
    @Test
    fun stringMetricsAreAvailableAndScore() =
        runBlocking {
            val sample =
                SingleTurnSample(
                    response = "The capital of France is Paris.",
                    reference = "Paris",
                )

            val exact = ExactMatch()
            val presence = StringPresence()
            val simLev = NonLLMStringSimilarity(distanceMeasure = DistanceMeasure.LEVENSHTEIN)
            val simHam = NonLLMStringSimilarity(distanceMeasure = DistanceMeasure.HAMMING)
            val simJaro = NonLLMStringSimilarity(distanceMeasure = DistanceMeasure.JARO)
            val simJw = NonLLMStringSimilarity(distanceMeasure = DistanceMeasure.JARO_WINKLER)

            assertEquals(0.0, (exact.singleTurnAscore(sample) as Number).toDouble(), 1e-12)
            assertEquals(1.0, (presence.singleTurnAscore(sample) as Number).toDouble(), 1e-12)

            listOf(simLev, simHam, simJaro, simJw).forEach { metric ->
                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                assertTrue(score in 0.0..1.0, "Expected [0,1] score for ${metric.name}, got $score")
            }
        }

    @Test
    fun pythonStyleAliasesAreAvailable() {
        val goalAccuracy: AgentGoalAccuracy = AgentGoalAccuracy()
        assertEquals("agent_goal_accuracy_with_reference", goalAccuracy.name)

        val sqlEquivalence: SQLSemanticEquivalence = SQLSemanticEquivalence()
        assertEquals("sql_semantic_equivalence", sqlEquivalence.name)
    }

    @Test
    fun collectionsMetricsAreStrictByDefaultWithoutLlm() {
        runBlocking {
            val singleTurn =
                SingleTurnSample(
                    userInput = "When was Kotlin announced?",
                    response = "Kotlin was announced in 2011.",
                    reference = "Kotlin was announced in 2011.",
                    retrievedContexts = listOf("Kotlin was announced in 2011."),
                    referenceContexts = listOf("Kotlin was announced in 2011."),
                )
            val multiTurn =
                MultiTurnSample(
                    userInput = listOf(HumanMessage(content = "Book a table")),
                    reference = "done",
                    referenceTopics = listOf("kotlin"),
                )

            assertFailsWith<IllegalStateException> { AnswerAccuracyMetric().singleTurnAscore(singleTurn) }
            assertFailsWith<IllegalStateException> { AnswerCorrectnessMetric().singleTurnAscore(singleTurn) }
            assertFailsWith<IllegalStateException> { FactualCorrectnessMetric().singleTurnAscore(singleTurn) }
            assertFailsWith<IllegalStateException> { NoiseSensitivityMetric().singleTurnAscore(singleTurn) }
            assertFailsWith<IllegalStateException> { SummaryScoreMetric().singleTurnAscore(singleTurn) }
            assertFailsWith<IllegalStateException> { AgentGoalAccuracyWithReferenceMetric().multiTurnAscore(multiTurn) }
            assertFailsWith<IllegalStateException> { AgentGoalAccuracyWithoutReferenceMetric().multiTurnAscore(multiTurn) }
            assertFailsWith<IllegalStateException> { AgentWorkflowCompletionMetric().multiTurnAscore(multiTurn) }
        }
    }
}
