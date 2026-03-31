package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ragas.cache.InMemoryCacheBackend
import ragas.embeddings.BaseRagasEmbedding
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class PublicApiTest {
    @Test
    fun topLevelEvaluateAndDefaultMetricsWork() {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(
                        userInput = "What is Kotlin language",
                        response = "Kotlin is language.",
                        retrievedContexts = listOf("Kotlin language on JVM", "Python language"),
                        referenceContexts = listOf("Kotlin language", "JVM runtime"),
                        reference = "Kotlin language",
                    ),
                ),
            )

        val result = evaluate(dataset = dataset)
        assertTrue(result.scores.first().containsKey("answer_relevancy"))
        assertEquals(4, defaultMetrics().size)
        assertEquals("0.0.1", VERSION)
    }

    @Test
    fun topLevelAevaluateWorks() = runBlocking {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(
                        userInput = "What is Kotlin language",
                        response = "Kotlin is language.",
                        retrievedContexts = listOf("Kotlin language on JVM", "Python language"),
                        referenceContexts = listOf("Kotlin language", "JVM runtime"),
                        reference = "Kotlin language",
                    ),
                ),
            )

        val result = aevaluate(dataset = dataset)
        assertTrue(result.scores.isNotEmpty())
    }

    @Test
    fun topLevelWithCacheWrapsProviders() = runBlocking {
        val llm = ApiFakeLlm("0.5")
        val embedding = ApiFakeEmbedding(listOf(0.1f, 0.2f))
        val cache = InMemoryCacheBackend()

        val cachedLlm = withCache(llm, cache)
        val cachedEmbedding = withCache(embedding, cache)

        cachedLlm.generateText("prompt")
        cachedLlm.generateText("prompt")
        cachedEmbedding.embedText("text")
        cachedEmbedding.embedText("text")

        assertEquals(1, llm.calls)
        assertEquals(1, embedding.calls)
    }

    @Test
    fun backendRegistryFacadeIsAvailable() {
        val names = backendRegistry().availableNames()
        assertTrue("inmemory" in names)
    }
}

private class ApiFakeLlm(
    private val output: String,
) : BaseRagasLlm {
    var calls: Int = 0
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        calls += 1
        return LlmResult(generations = listOf(LlmGeneration(output)))
    }
}

private class ApiFakeEmbedding(
    private val vector: List<Float>,
) : BaseRagasEmbedding {
    var calls: Int = 0

    override suspend fun embedText(text: String): List<Float> {
        calls += 1
        return vector
    }
}
