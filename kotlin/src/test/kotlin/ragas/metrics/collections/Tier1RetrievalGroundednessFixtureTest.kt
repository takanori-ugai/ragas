package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.SingleTurnSample
import kotlin.test.Test
import kotlin.test.assertTrue

class Tier1RetrievalGroundednessFixtureTest {
    @Test
    fun contextRelevanceMatchesPythonFixtureBands() =
        runBlocking {
            val fixture = readFixture()
            val metric = ContextRelevanceMetric()

            fixture.jsonObject.getValue("context_relevance_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        userInput = obj.getValue("user_input").jsonPrimitive.content,
                        retrievedContexts = obj.getValue("retrieved_contexts").jsonArray.map { it.jsonPrimitive.content },
                    )

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                assertBand(score, obj.getValue("expected_band").jsonPrimitive.content, metric.name)
            }
        }

    @Test
    fun responseGroundednessMatchesPythonFixtureBands() =
        runBlocking {
            val fixture = readFixture()
            val metric = ResponseGroundednessMetric()

            fixture.jsonObject.getValue("response_groundedness_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        response = obj.getValue("response").jsonPrimitive.content,
                        retrievedContexts = obj.getValue("retrieved_contexts").jsonArray.map { it.jsonPrimitive.content },
                    )

                val score = (metric.singleTurnAscore(sample) as Number).toDouble()
                assertBand(score, obj.getValue("expected_band").jsonPrimitive.content, metric.name)
            }
        }

    @Test
    fun tier1MetricListIncludesPorts() {
        val names = retrievalGroundednessTier1Metrics().map { metric -> metric.name }
        assertTrue("context_relevance" in names)
        assertTrue("response_groundedness" in names)
        assertTrue("context_precision_with_reference" in names)
        assertTrue("context_precision_without_reference" in names)
        assertTrue("id_based_context_precision" in names)
        assertTrue("context_entity_recall" in names)
    }

    private fun assertBand(
        score: Double,
        band: String,
        metricName: String,
    ) {
        when (band) {
            "high" -> assertTrue(score >= 0.50, "metric=$metricName expected high but score=$score")
            "partial" -> assertTrue(score in 0.20..0.80, "metric=$metricName expected partial but score=$score")
            "low" -> assertTrue(score <= 0.45, "metric=$metricName expected low but score=$score")
            else -> error("Unsupported band '$band'")
        }
    }

    private fun readFixture() =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/metrics/ws3_tier1_retrieval_groundedness_fixture.json")) {
                "Fixture not found on classpath: fixtures/metrics/ws3_tier1_retrieval_groundedness_fixture.json"
            }.bufferedReader().use { it.readText() },
        )
}
