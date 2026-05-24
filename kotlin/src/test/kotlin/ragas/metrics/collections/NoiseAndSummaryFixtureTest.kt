package ragas.metrics.collections

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NoiseAndSummaryFixtureTest {
    @Test
    fun noiseSensitivityMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH).jsonObject
            val relevant = NoiseSensitivityMetric(mode = NoiseSensitivityMetric.Mode.RELEVANT, name = "noise_sensitivity_relevant")
            val irrelevant = NoiseSensitivityMetric(mode = NoiseSensitivityMetric.Mode.IRRELEVANT, name = "noise_sensitivity_irrelevant")

            fixture.getValue("noise_sensitivity_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        userInput = obj.getValue("user_input").jsonPrimitive.content,
                        response = obj.getValue("response").jsonPrimitive.content,
                        reference = obj.getValue("reference").jsonPrimitive.content,
                        retrievedContexts = obj.getValue("retrieved_contexts").jsonArray.map { it.jsonPrimitive.content },
                    )
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val relScore = (relevant.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    relScore,
                    expectedScores.getValue(relevant.name).jsonPrimitive.double,
                    relevant.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    relScore,
                    expectedBands.getValue(relevant.name).jsonPrimitive.content,
                    relevant.name,
                )

                val irrScore = (irrelevant.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    irrScore,
                    expectedScores.getValue(irrelevant.name).jsonPrimitive.double,
                    irrelevant.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    irrScore,
                    expectedBands.getValue(irrelevant.name).jsonPrimitive.content,
                    irrelevant.name,
                )
            }
        }

    @Test
    fun summaryScoreMatchesFixtureScoreBands() =
        runBlocking {
            val fixture = AgentFixtureTestSupport.readFixture(FIXTURE_PATH).jsonObject
            val defaultMetric = SummaryScoreMetric(name = "summary_score_default")
            val noPenaltyMetric = SummaryScoreMetric(name = "summary_score_no_penalty", lengthPenalty = false)

            fixture.getValue("summary_score_cases").jsonArray.forEach { row ->
                val obj = row.jsonObject
                val sample =
                    SingleTurnSample(
                        response = obj.getValue("response").jsonPrimitive.content,
                        referenceContexts = obj.getValue("reference_contexts").jsonArray.map { it.jsonPrimitive.content },
                    )
                val expectedScores = obj.getValue("expected_scores").jsonObject
                val expectedBands = obj.getValue("expected_bands").jsonObject

                val defaultScore = (defaultMetric.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    defaultScore,
                    expectedScores.getValue(defaultMetric.name).jsonPrimitive.double,
                    defaultMetric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    defaultScore,
                    expectedBands.getValue(defaultMetric.name).jsonPrimitive.content,
                    defaultMetric.name,
                )

                val noPenaltyScore = (noPenaltyMetric.singleTurnAscore(sample) as Number).toDouble()
                AgentFixtureTestSupport.assertFixtureScore(
                    noPenaltyScore,
                    expectedScores.getValue(noPenaltyMetric.name).jsonPrimitive.double,
                    noPenaltyMetric.name,
                )
                AgentFixtureTestSupport.assertScoreBand(
                    noPenaltyScore,
                    expectedBands.getValue(noPenaltyMetric.name).jsonPrimitive.content,
                    noPenaltyMetric.name,
                )
            }
        }

    @Test
    fun summaryScoreValidatesCoefficientBounds() {
        val tooLow =
            runCatching {
                SummaryScoreMetric(coeff = -0.1)
            }.exceptionOrNull()
        assertTrue(tooLow is IllegalArgumentException)

        val tooHigh =
            runCatching {
                SummaryScoreMetric(coeff = 1.1)
            }.exceptionOrNull()
        assertTrue(tooHigh is IllegalArgumentException)
    }

    @Test
    fun tier3MetricListIncludesNoiseAndSummaryPorts() {
        AgentFixtureTestSupport.assertTier3MetricRegistryIncludes(
            "noise_sensitivity",
            "summary_score",
            "quoted_spans_alignment",
            "chrf_score",
            "bleu_score",
            "rouge_score",
            "semantic_similarity",
        )
    }

    @Test
    fun summaryLlmPathRetriesMalformedPayloadsAcrossPipelineStages() =
        runBlocking {
            val llm =
                ScriptedSummaryLlm(
                    outputs =
                        listOf(
                            "not-json",
                            """{"keyphrases":["Apple Inc.","1976"]}""",
                            """{"questions":"bad"}""",
                            """{"questions":["Is Apple Inc. a technology company?","Was Apple founded in 1976?"]}""",
                            """{"answers":"bad"}""",
                            """{"answers":["1","1"]}""",
                        ),
                )
            val metric =
                SummaryScoreMetric(maxRetries = 2, lengthPenalty = false).also { summary ->
                    summary.llm = llm
                }
            val sample =
                SingleTurnSample(
                    referenceContexts =
                        listOf(
                            "Apple Inc. is a technology company based in Cupertino.",
                            "It was founded by Steve Jobs in 1976.",
                        ),
                    response = "Apple is a technology company founded in 1976.",
                )

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0, score, 1e-12)
            assertEquals(6, llm.prompts.size)
        }

    @Test
    fun summaryLlmPathThrowsWhenNoAnswersGenerated() =
        runBlocking {
            val metric =
                SummaryScoreMetric(maxRetries = 1).also { summary ->
                    summary.llm =
                        ScriptedSummaryLlm(
                            outputs =
                                listOf(
                                    """{"keyphrases":["Apple Inc."]}""",
                                    """{"questions":["Is Apple a technology company?"]}""",
                                    """{"answers":[]}""",
                                ),
                        )
                }
            val sample =
                SingleTurnSample(
                    referenceContexts = listOf("Apple Inc. is a technology company."),
                    response = "Apple is a technology company.",
                )

            val error = assertFailsWith<ArithmeticException> { metric.singleTurnAscore(sample) }
            assertEquals("No answers generated, unable to calculate the score.", error.message)
        }

    @Test
    fun summaryLlmPathPropagatesCancellationException() {
        runBlocking {
            val metric =
                SummaryScoreMetric(maxRetries = 2).also { summary ->
                    summary.llm =
                        ScriptedSummaryLlm(
                            outputs = listOf(CancellationException("cancelled")),
                        )
                }
            val sample =
                SingleTurnSample(
                    referenceContexts = listOf("Apple Inc. is a technology company."),
                    response = "Apple is a technology company.",
                )

            assertFailsWith<CancellationException> { metric.singleTurnAscore(sample) }
        }
    }

    private companion object {
        private const val FIXTURE_PATH = "fixtures/metrics/ws3_tier3_noise_summary_fixture.json"
    }
}

private class ScriptedSummaryLlm(
    private val outputs: List<Any>,
) : BaseRagasLlm {
    private var cursor: Int = 0
    override var runConfig: RunConfig = RunConfig()
    val prompts: MutableList<String> = mutableListOf()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        prompts += prompt
        val value = outputs.getOrElse(cursor) { outputs.lastOrNull() ?: "" }
        cursor += 1
        return when (value) {
            is Throwable -> throw value
            is String -> LlmResult(generations = listOf(LlmGeneration(value)))
            else -> LlmResult(generations = listOf(LlmGeneration(value.toString())))
        }
    }
}
