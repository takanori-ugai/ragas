package ragas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.collections.TopicAdherenceMetric
import ragas.model.AiMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TopicAdherenceMetricParityTest {
    @Test
    fun llmPathUsesExtractRefusalAndClassificationPipeline() =
        runBlocking {
            val metric =
                TopicAdherenceMetric(mode = TopicAdherenceMetric.Mode.F1).also { adherence ->
                    adherence.llm =
                        ScriptedTopicAdherenceLlm(
                            outputs =
                                listOf(
                                    """{"topics":["Physics","Cooking"]}""",
                                    """{"refused_to_answer":false}""",
                                    """{"refused_to_answer":true}""",
                                    """{"classifications":[true,false]}""",
                                ),
                        )
                }
            val sample =
                MultiTurnSample(
                    userInput =
                        listOf(
                            HumanMessage("Tell me about physics and pasta."),
                            AiMessage("I can explain physics but not cooking advice."),
                        ),
                    referenceTopics = listOf("Physics", "Science"),
                )

            val score = (metric.multiTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0, score, 1e-9)
        }

    @Test
    fun llmPathPadsClassificationVectorToMatchExtractedTopics() =
        runBlocking {
            val metric =
                TopicAdherenceMetric(mode = TopicAdherenceMetric.Mode.PRECISION).also { adherence ->
                    adherence.llm =
                        ScriptedTopicAdherenceLlm(
                            outputs =
                                listOf(
                                    """{"topics":["Physics","Math","Cooking"]}""",
                                    """{"refused_to_answer":false}""",
                                    """{"refused_to_answer":false}""",
                                    """{"refused_to_answer":false}""",
                                    """{"classifications":[true]}""",
                                ),
                        )
                }
            val sample =
                MultiTurnSample(
                    userInput =
                        listOf(
                            HumanMessage("Talk about physics, math and cooking."),
                            AiMessage("Here are notes on all three."),
                        ),
                    referenceTopics = listOf("Physics"),
                )

            val score = (metric.multiTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0 / 3.0, score, 1e-9)
        }

    @Test
    fun llmPathReturnsNanWhenNoTopicsAreExtracted() =
        runBlocking {
            val metric =
                TopicAdherenceMetric().also { adherence ->
                    adherence.llm = ScriptedTopicAdherenceLlm(outputs = listOf("""{"topics":[]}"""))
                }
            val sample =
                MultiTurnSample(
                    userInput = listOf(HumanMessage("hello"), AiMessage("hi")),
                    referenceTopics = listOf("General"),
                )

            val score = (metric.multiTurnAscore(sample) as Number).toDouble()
            assertTrue(score.isNaN())
        }

    @Test
    fun llmPathValidatesReferenceTopicsPresence() =
        runBlocking {
            val metric = TopicAdherenceMetric().also { it.llm = ScriptedTopicAdherenceLlm(outputs = emptyList()) }
            val sample =
                MultiTurnSample(
                    userInput = listOf(HumanMessage("hello"), AiMessage("hi")),
                    referenceTopics = emptyList(),
                )

            val error = assertFailsWith<IllegalArgumentException> { metric.multiTurnAscore(sample) }
            assertEquals("reference_topics must be a non-empty list of topics", error.message)
        }

    @Test
    fun llmPathRetriesMalformedExtractionAndClassification() =
        runBlocking {
            val llm =
                ScriptedTopicAdherenceLlm(
                    outputs =
                        listOf(
                            "not-json",
                            """{"topics":["Physics"]}""",
                            """{"refused_to_answer":false}""",
                            """{"classifications":"bad"}""",
                            """{"classifications":[true]}""",
                        ),
                )
            val metric =
                TopicAdherenceMetric(maxRetries = 2).also { adherence ->
                    adherence.llm = llm
                }
            val sample =
                MultiTurnSample(
                    userInput =
                        listOf(
                            HumanMessage("Explain physics basics."),
                            AiMessage("Physics studies matter, motion and energy."),
                        ),
                    referenceTopics = listOf("Physics"),
                )

            val score = (metric.multiTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0, score, 1e-9)
            assertEquals(5, llm.prompts.size)
        }

    @Test
    fun llmPathPropagatesCancellationException() {
        runBlocking {
            val metric =
                TopicAdherenceMetric(maxRetries = 2).also { adherence ->
                    adherence.llm =
                        ScriptedTopicAdherenceLlm(
                            outputs = listOf(CancellationException("cancelled")),
                        )
                }
            val sample =
                MultiTurnSample(
                    userInput =
                        listOf(
                            HumanMessage("Explain physics."),
                            AiMessage("Sure."),
                        ),
                    referenceTopics = listOf("Physics"),
                )

            assertFailsWith<CancellationException> { metric.multiTurnAscore(sample) }
        }
    }
}

private class ScriptedTopicAdherenceLlm(
    private val outputs: List<Any>,
) : BaseRagasLlm {
    private var cursor = 0
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
