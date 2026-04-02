package ragas

import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.defaults.FaithfulnessMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaithfulnessMetricTest {
    @Test
    fun crossContextSplicedSentenceIsNotMarkedSupported() =
        runBlocking {
            val metric = FaithfulnessMetric(allowHeuristicFallback = true)
            val sample =
                SingleTurnSample(
                    response = "Kotlin runs on JVM and blue whales migrate oceans.",
                    retrievedContexts =
                        listOf(
                            "Kotlin runs on JVM.",
                            "Blue whales migrate oceans.",
                        ),
                )

            assertEquals(0.0, metric.singleTurnAscore(sample))
        }

    @Test
    fun sentenceSupportedBySingleContextScoresAsSupported() =
        runBlocking {
            val metric = FaithfulnessMetric(allowHeuristicFallback = true)
            val sample =
                SingleTurnSample(
                    response = "Kotlin runs on JVM.",
                    retrievedContexts =
                        listOf(
                            "Kotlin runs on JVM with strong tooling.",
                            "Python uses indentation.",
                        ),
                )

            assertEquals(1.0, metric.singleTurnAscore(sample))
        }

    @Test
    fun defaultPathRequiresLlmForParitySemantics() {
        runBlocking {
            val metric = FaithfulnessMetric()
            val sample =
                SingleTurnSample(
                    userInput = "What is Kotlin?",
                    response = "Kotlin runs on JVM.",
                    retrievedContexts = listOf("Kotlin runs on JVM."),
                )
            assertFailsWith<IllegalStateException> {
                metric.singleTurnAscore(sample)
            }
        }
    }

    @Test
    fun llmPathUsesStatementAndVerdictPipeline() =
        runBlocking {
            val metric =
                FaithfulnessMetric().also { faithfulness ->
                    faithfulness.llm =
                        ScriptedLlm(
                            outputs =
                                listOf(
                                    """{"statements":["Kotlin runs on JVM.","Kotlin was created in 2010."]}""",
                                    """{"statements":[{"statement":"Kotlin runs on JVM.","reason":"In context","verdict":1},{"statement":"Kotlin was created in 2010.","reason":"Not in context","verdict":0}]}""",
                                ),
                        )
                }
            val sample =
                SingleTurnSample(
                    userInput = "What is Kotlin?",
                    response = "Kotlin runs on JVM. Kotlin was created in 2010.",
                    retrievedContexts = listOf("Kotlin runs on JVM and Android."),
                )

            assertEquals(0.5, metric.singleTurnAscore(sample))
        }

    @Test
    fun llmVerdictPromptEscapesQuotedStatementsAsJson() =
        runBlocking {
            val llm =
                CapturingScriptedLlm(
                    outputs =
                        listOf(
                            """{"statements":["Kotlin is called \"JetBrains language\"."]}""",
                            """{"statements":[{"statement":"Kotlin is called \"JetBrains language\".","reason":"In context","verdict":1}]}""",
                        ),
                )
            val metric = FaithfulnessMetric().also { it.llm = llm }
            val sample =
                SingleTurnSample(
                    userInput = "What is Kotlin called?",
                    response = """Kotlin is called "JetBrains language".""",
                    retrievedContexts = listOf("""Kotlin is called "JetBrains language" by some developers."""),
                )

            val score = (metric.singleTurnAscore(sample) as Number).toDouble()
            assertEquals(1.0, score, 1e-9)
            assertTrue(llm.prompts[1].contains("""["Kotlin is called \"JetBrains language\"."]"""))
        }
}

private class ScriptedLlm(
    private val outputs: List<String>,
) : BaseRagasLlm {
    private var cursor: Int = 0
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        val value = outputs.getOrElse(cursor) { outputs.lastOrNull().orEmpty() }
        cursor += 1
        return LlmResult(generations = listOf(LlmGeneration(value)))
    }
}

private class CapturingScriptedLlm(
    private val outputs: List<String>,
) : BaseRagasLlm {
    private var cursor: Int = 0
    val prompts = mutableListOf<String>()
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        prompts += prompt
        val value = outputs.getOrElse(cursor) { outputs.lastOrNull().orEmpty() }
        cursor += 1
        return LlmResult(generations = listOf(LlmGeneration(value)))
    }
}
