package ragas

import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.defaults.ContextRecallMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextRecallMetricTest {
    @Test
    fun llmPathComputesAttributionRatio() =
        runBlocking {
            val metric =
                ContextRecallMetric().also { recall ->
                    recall.llm =
                        ScriptedRecallLlm(
                            output =
                                """{"classifications":[{"statement":"Kotlin runs on JVM.","reason":"Supported","attributed":1},{"statement":"Kotlin was released in 2010.","reason":"Unsupported","attributed":0}]}""",
                        )
                }
            val sample =
                SingleTurnSample(
                    userInput = "Tell me about Kotlin",
                    retrievedContexts = listOf("Kotlin runs on JVM."),
                    reference = "Kotlin runs on JVM. Kotlin was released in 2010.",
                )

            assertEquals(0.5, metric.singleTurnAscore(sample))
        }

    @Test
    fun fallbackPathUsesReferenceContextsWhenLlmMissing() =
        runBlocking {
            val metric = ContextRecallMetric()
            val sample =
                SingleTurnSample(
                    retrievedContexts = listOf("Kotlin language on JVM", "Python language"),
                    referenceContexts = listOf("Kotlin language", "JVM runtime"),
                )

            assertEquals(0.75, metric.singleTurnAscore(sample))
        }
}

private class ScriptedRecallLlm(
    private val output: String,
) : BaseRagasLlm {
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult = LlmResult(generations = listOf(LlmGeneration(output)))
}
