package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.MultiTurnSample
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactualAndTopicFixtureTest {
    @Test
    fun factualCorrectnessMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH).jsonObject
            val metric = FactualCorrectnessMetric()

            fixture.getValue("factual_correctness_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        response = obj.getValue("response").jsonPrimitive.content,
                        reference = obj.getValue("reference").jsonPrimitive.content,
                    )
                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    score,
                    obj.getValue("expected_score").jsonPrimitive.double,
                    metric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    score,
                    obj.getValue("expected_band").jsonPrimitive.content,
                    metric.name,
                )
            }
        }

    @Test
    fun topicAdherenceMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH).jsonObject
            val metric = TopicAdherenceMetric()

            fixture.getValue("topic_adherence_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    MultiTurnSample(
                        userInput = obj.getValue("user_input").jsonArray.map { AgentFixtureTestSupport.parseMessage(it.jsonObject) },
                        referenceTopics = obj.getValue("reference_topics").jsonArray.map { it.jsonPrimitive.content },
                    )
                val score = (metric.multiTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    score,
                    obj.getValue("expected_score").jsonPrimitive.double,
                    metric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    score,
                    obj.getValue("expected_band").jsonPrimitive.content,
                    metric.name,
                )
            }
        }

    @Test
    fun factualCorrectnessValidatesBeta() {
        val err =
            runCatching {
                FactualCorrectnessMetric(beta = 0.0)
            }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
    }

    @Test
    fun tier3MetricListIncludesNewPorts() {
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
        private const val FIXTURE_PATH = "fixtures/metrics/ws3_tier3_factual_topic_fixture.json"
    }
}
