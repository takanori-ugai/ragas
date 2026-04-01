package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerQualityFixtureTest {
    @Test
    fun answerAccuracyMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH)
            val metric = AnswerAccuracyMetric()

            fixture.jsonObject.getValue("cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        userInput = obj.getValue("user_input").jsonPrimitive.content,
                        response = obj.getValue("response").jsonPrimitive.content,
                        reference = obj.getValue("reference").jsonPrimitive.content,
                    )
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    score,
                    expectedScores.getValue(metric.name).jsonPrimitive.double,
                    metric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    score,
                    expectedBands.getValue(metric.name).jsonPrimitive.content,
                    metric.name,
                )
            }
        }

    @Test
    fun answerCorrectnessMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH)
            val metric = AnswerCorrectnessMetric()

            fixture.jsonObject.getValue("cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        userInput = obj.getValue("user_input").jsonPrimitive.content,
                        response = obj.getValue("response").jsonPrimitive.content,
                        reference = obj.getValue("reference").jsonPrimitive.content,
                    )
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    score,
                    expectedScores.getValue(metric.name).jsonPrimitive.double,
                    metric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    score,
                    expectedBands.getValue(metric.name).jsonPrimitive.content,
                    metric.name,
                )
            }
        }

    @Test
    fun answerCorrectnessValidatesConstructorInputs() {
        val invalidWeightsSize =
            runCatching {
                AnswerCorrectnessMetric(weights = listOf(1.0))
            }.exceptionOrNull()
        assertTrue(invalidWeightsSize is IllegalArgumentException)

        val invalidWeightsZero =
            runCatching {
                AnswerCorrectnessMetric(weights = listOf(0.0, 0.0))
            }.exceptionOrNull()
        assertTrue(invalidWeightsZero is IllegalArgumentException)

        val invalidNegativeWeights =
            runCatching {
                AnswerCorrectnessMetric(weights = listOf(-0.1, 0.2))
            }.exceptionOrNull()
        assertTrue(invalidNegativeWeights is IllegalArgumentException)
    }

    @Test
    fun tier3MetricListIncludesAnswerQualityPorts() {
        val names = answerQualityTier3Metrics().map { metric -> metric.name }.toSet()
        assertEquals(11, names.size)
        assertTrue("answer_accuracy" in names)
        assertTrue("answer_correctness" in names)
        assertTrue("factual_correctness" in names)
        assertTrue("topic_adherence" in names)
        assertTrue("noise_sensitivity" in names)
        assertTrue("summary_score" in names)
        assertTrue("quoted_spans_alignment" in names)
        assertTrue("chrf_score" in names)
        assertTrue("bleu_score" in names)
        assertTrue("rouge_score" in names)
        assertTrue("semantic_similarity" in names)
    }

    private companion object {
        private const val FIXTURE_PATH = "fixtures/metrics/ws3_tier3_answer_quality_fixture.json"
    }
}
