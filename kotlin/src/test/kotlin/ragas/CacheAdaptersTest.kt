package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import ragas.cache.InMemoryCacheBackend
import ragas.embeddings.BaseRagasEmbedding
import ragas.embeddings.CachedRagasEmbedding
import ragas.llms.BaseRagasLlm
import ragas.llms.CachedRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.runtime.RunConfig

class CacheAdaptersTest {
    @Test
    fun cachedLlmReturnsCachedResultOnRepeatedRequest() = runBlocking {
        val base = CountingLlm("hello")
        val cached = CachedRagasLlm(base, InMemoryCacheBackend())

        cached.generateText("prompt")
        cached.generateText("prompt")

        assertEquals(1, base.calls)
    }

    @Test
    fun cachedEmbeddingReturnsCachedVectorOnRepeatedRequest() = runBlocking {
        val base = CountingEmbedding(listOf(0.1f, 0.2f))
        val cached = CachedRagasEmbedding(base, InMemoryCacheBackend())

        cached.embedText("text")
        cached.embedText("text")

        assertEquals(1, base.calls)
    }
}

private class CountingLlm(
    private val text: String,
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
        return LlmResult(generations = listOf(LlmGeneration(text = text)))
    }
}

private class CountingEmbedding(
    private val vector: List<Float>,
) : BaseRagasEmbedding {
    var calls: Int = 0

    override suspend fun embedText(text: String): List<Float> {
        calls += 1
        return vector
    }
}
