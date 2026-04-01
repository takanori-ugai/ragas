package ragas.metrics.collections

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.ToolCall
import ragas.model.ToolMessage
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal object AgentFixtureTestSupport {
    private val EXPECTED_TIER3_METRIC_NAMES =
        setOf(
            "answer_accuracy",
            "answer_correctness",
            "factual_correctness",
            "topic_adherence",
            "noise_sensitivity",
            "summary_score",
            "quoted_spans_alignment",
            "chrf_score",
            "bleu_score",
            "rouge_score",
            "semantic_similarity",
        )

    fun readFixture(resourcePath: String): JsonElement =
        Json.parseToJsonElement(
            requireNotNull(AgentFixtureTestSupport::class.java.classLoader.getResourceAsStream(resourcePath)) {
                "Fixture not found on classpath: $resourcePath"
            }.bufferedReader().use { it.readText() },
        )

    fun parseMessage(obj: JsonObject): ConversationMessage =
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

    fun parseToolCall(obj: JsonObject): ToolCall =
        ToolCall(
            name = obj.getValue("name").jsonPrimitive.content,
            args = obj["args"]?.jsonObject?.toMap().orEmpty(),
        )

    fun assertFixtureScore(
        score: Double,
        expected: Double,
        metricName: String,
    ) {
        assertTrue(abs(score - expected) < 1e-9, "metric=$metricName expected=$expected actual=$score")
    }

    /**
     * Asserts that [score] falls within the expected band.
     * Bands: perfect (1.0), high (>= 2/3), partial (>= 1/3), low (< 1/3).
     */
    fun assertScoreBand(
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

    fun assertTier3MetricRegistryMatchesExpected() {
        val names = answerQualityTier3Metrics().map { metric -> metric.name }.toSet()
        assertEquals(EXPECTED_TIER3_METRIC_NAMES, names)
    }

    fun assertTier3MetricRegistryIncludes(vararg requiredMetricNames: String) {
        val names = answerQualityTier3Metrics().map { metric -> metric.name }.toSet()
        requiredMetricNames.forEach { name ->
            assertTrue(name in names, "tier3 registry missing metric: $name")
        }
    }
}
