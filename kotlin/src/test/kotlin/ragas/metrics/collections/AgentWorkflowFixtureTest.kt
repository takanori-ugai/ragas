package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.ToolCall
import ragas.model.ToolMessage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentWorkflowFixtureTest {
    @Test
    fun agentGoalVariantsMatchFixtureScoreBands() =
        runBlocking {
            val fixture = readFixture()
            val withReference = AgentGoalAccuracyWithReferenceMetric()
            val withoutReference = AgentGoalAccuracyWithoutReferenceMetric()

            fixture.jsonObject.getValue("cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample = parseMultiTurnSample(obj)
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val withReferenceScore = (withReference.multiTurnAscore(sample) as Number).toDouble()
                assertFixtureScore(
                    withReferenceScore,
                    expectedScores.getValue(withReference.name).jsonPrimitive.double,
                    withReference.name,
                )
                assertScoreBand(
                    withReferenceScore,
                    expectedBands.getValue(withReference.name).jsonPrimitive.content,
                    withReference.name,
                )

                val withoutReferenceScore = (withoutReference.multiTurnAscore(sample) as Number).toDouble()
                assertFixtureScore(
                    withoutReferenceScore,
                    expectedScores.getValue(withoutReference.name).jsonPrimitive.double,
                    withoutReference.name,
                )
                assertScoreBand(
                    withoutReferenceScore,
                    expectedBands.getValue(withoutReference.name).jsonPrimitive.content,
                    withoutReference.name,
                )
            }
        }

    @Test
    fun workflowCompletionMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = readFixture()
            val metric = AgentWorkflowCompletionMetric()

            fixture.jsonObject.getValue("cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample = parseMultiTurnSample(obj)
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val score = (metric.multiTurnAscore(sample) as Number).toDouble()
                assertFixtureScore(score, expectedScores.getValue(metric.name).jsonPrimitive.double, metric.name)
                assertScoreBand(score, expectedBands.getValue(metric.name).jsonPrimitive.content, metric.name)
            }
        }

    private fun parseMultiTurnSample(obj: JsonObject): MultiTurnSample {
        val messages =
            obj
                .getValue("user_input")
                .jsonArray
                .map { parseMessage(it.jsonObject) }

        val reference = obj["reference"]?.jsonPrimitive?.content
        return MultiTurnSample(userInput = messages, reference = reference)
    }

    private fun parseMessage(obj: JsonObject): ConversationMessage =
        when (obj.getValue("type").jsonPrimitive.content) {
            "human" -> {
                HumanMessage(content = obj.getValue("content").jsonPrimitive.content)
            }

            "tool" -> {
                ToolMessage(content = obj.getValue("content").jsonPrimitive.content)
            }

            "ai" -> {
                AiMessage(
                    content = obj.getValue("content").jsonPrimitive.content,
                    toolCalls =
                        obj["tool_calls"]?.jsonArray?.map { parseToolCall(it.jsonObject) },
                )
            }

            else -> {
                error("Unsupported message type in fixture: ${obj["type"]}")
            }
        }

    private fun parseToolCall(obj: JsonObject): ToolCall =
        ToolCall(
            name = obj.getValue("name").jsonPrimitive.content,
            args = obj["args"]?.jsonObject?.toMap().orEmpty(),
        )

    private fun assertFixtureScore(
        score: Double,
        expected: Double,
        metricName: String,
    ) {
        assertTrue(abs(score - expected) < 1e-9, "metric=$metricName expected=$expected actual=$score")
    }

    private fun assertScoreBand(
        score: Double,
        expectedBand: String,
        metricName: String,
    ) {
        val band =
            when {
                abs(score - 1.0) < 1e-9 -> "perfect"
                score >= 2.0 / 3.0 -> "high"
                score >= 1.0 / 3.0 -> "partial"
                else -> "low"
            }
        assertTrue(
            band == expectedBand,
            "metric=$metricName expectedBand=$expectedBand actualBand=$band score=$score",
        )
    }

    private fun readFixture(): JsonElement =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/metrics/ws3_tier2_agent_workflow_fixture.json")) {
                "Fixture not found on classpath: fixtures/metrics/ws3_tier2_agent_workflow_fixture.json"
            }.bufferedReader().use { it.readText() },
        )
}
