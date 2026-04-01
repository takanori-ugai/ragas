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

class AgentToolCallFixtureTest {
    @Test
    fun toolCallAccuracyMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = readFixture()
            val strictMetric = ToolCallAccuracyMetric(strictOrder = true, name = "tool_call_accuracy_strict")
            val relaxedMetric = ToolCallAccuracyMetric(strictOrder = false, name = "tool_call_accuracy_relaxed")

            fixture.jsonObject.getValue("cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample = parseMultiTurnSample(obj)
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val strictScore = (strictMetric.multiTurnAscore(sample) as Number).toDouble()
                assertFixtureScore(strictScore, expectedScores.getValue(strictMetric.name).jsonPrimitive.double, strictMetric.name)
                assertScoreBand(strictScore, expectedBands.getValue(strictMetric.name).jsonPrimitive.content, strictMetric.name)

                val relaxedScore = (relaxedMetric.multiTurnAscore(sample) as Number).toDouble()
                assertFixtureScore(relaxedScore, expectedScores.getValue(relaxedMetric.name).jsonPrimitive.double, relaxedMetric.name)
                assertScoreBand(relaxedScore, expectedBands.getValue(relaxedMetric.name).jsonPrimitive.content, relaxedMetric.name)
            }
        }

    @Test
    fun toolCallF1MatchesFixtureScoreBands() =
        runBlocking {
            val fixture = readFixture()
            val metric = ToolCallF1Metric()

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

    @Test
    fun tier2MetricListIncludesPorts() {
        val names = agentToolCallTier2Metrics().map { metric -> metric.name }
        assertTrue("tool_call_accuracy" in names)
        assertTrue("tool_call_f1" in names)
        assertTrue("agent_goal_accuracy_with_reference" in names)
        assertTrue("agent_goal_accuracy_without_reference" in names)
        assertTrue("agent_workflow_completion" in names)
    }

    private fun parseMultiTurnSample(obj: JsonObject): MultiTurnSample {
        val messages =
            obj
                .getValue("user_input")
                .jsonArray
                .map { parseMessage(it.jsonObject) }

        val referenceToolCalls =
            obj
                .getValue("reference_tool_calls")
                .jsonArray
                .map { parseToolCall(it.jsonObject) }

        return MultiTurnSample(userInput = messages, referenceToolCalls = referenceToolCalls)
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
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/metrics/ws3_tier2_agent_tool_call_fixture.json")) {
                "Fixture not found on classpath: fixtures/metrics/ws3_tier2_agent_tool_call_fixture.json"
            }.bufferedReader().use { it.readText() },
        )
}
