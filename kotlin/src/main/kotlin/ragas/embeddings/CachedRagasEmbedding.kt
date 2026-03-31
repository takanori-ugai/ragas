package ragas.embeddings

import ragas.cache.CacheBackend
import ragas.cache.stableCacheKey

class CachedRagasEmbedding(
    private val delegate: BaseRagasEmbedding,
    private val cache: CacheBackend,
) : BaseRagasEmbedding {
    override suspend fun embedText(text: String): List<Float> {
        val key = stableCacheKey("embedding|$text")
        val cached = cache.get(key)
        if (cached is List<*> && cached.all { it is Float }) {
            @Suppress("UNCHECKED_CAST")
            return cached as List<Float>
        }

        val result = delegate.embedText(text)
        cache.put(key, result)
        return result
    }

    override suspend fun embedTexts(texts: List<String>): List<List<Float>> =
        texts.map { text -> embedText(text) }
}
