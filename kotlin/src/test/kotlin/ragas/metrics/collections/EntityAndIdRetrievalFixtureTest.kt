package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.SingleTurnSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class EntityAndIdRetrievalFixtureTest {
    @Test
    fun contextEntityRecallMatchesPythonFixtureBands() =
        runBlocking {
            val fixture = readFixture()
            val metric = ContextEntityRecallMetric()

            fixture.jsonObject.getValue("context_entity_recall_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        reference = obj.getValue("reference").jsonPrimitive.content,
                        retrievedContexts = obj.getValue("retrieved_contexts").jsonArray.map { it.jsonPrimitive.content },
                    )

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                assertBand(score, obj.getValue("expected_band").jsonPrimitive.content, metric.name)
            }
        }

    @Test
    fun idBasedContextPrecisionMatchesFixtureValues() =
        runBlocking {
            val fixture = readFixture()
            val metric = IdBasedContextPrecisionMetric()

            fixture.jsonObject.getValue("id_based_context_precision_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        retrievedContextIds = obj.getValue("retrieved_context_ids").jsonArray.map { it.jsonPrimitive.content },
                        referenceContextIds = obj.getValue("reference_context_ids").jsonArray.map { it.jsonPrimitive.content },
                    )

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                val expected = obj.getValue("expected").jsonPrimitive.double
                assertTrue(abs(score - expected) < 1e-9, "expected=$expected actual=$score")
            }
        }

    private fun assertBand(
        score: Double,
        band: String,
        metricName: String,
    ) {
        when (band) {
            "high" -> assertTrue(score >= 0.60, "metric=$metricName expected high but score=$score")
            "partial" -> assertTrue(score in 0.10..0.80, "metric=$metricName expected partial but score=$score")
            "low" -> assertTrue(score <= 0.30, "metric=$metricName expected low but score=$score")
            else -> error("Unsupported band '$band'")
        }
    }

    private fun readFixture() =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/metrics/ws3_tier1_entity_id_fixture.json")) {
                "Fixture not found on classpath: fixtures/metrics/ws3_tier1_entity_id_fixture.json"
            }.bufferedReader().use { it.readText() },
        )
}
