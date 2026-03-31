package ragas.embeddings

import ragas.cache.CacheBackend
import ragas.cache.stableCacheKey

class CachedRagasEmbedding(
    private val delegate: BaseRagasEmbedding,
    private val cache: CacheBackend,
) : BaseRagasEmbedding {
    override suspend fun embedText(text: String): List<Float> {
        val key = stableCacheKey("embedding|$text")
        val cached = decodeCachedVector(cache.get(key))
        if (cached != null) {
            return cached
        }

        val result = delegate.embedText(text)
        cache.put(key, result)
        return result
    }

    override suspend fun embedTexts(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) {
            return emptyList()
        }

        val keys = texts.map { text -> stableCacheKey("embedding|$text") }
        val results = arrayOfNulls<List<Float>>(texts.size)
        val misses = mutableListOf<Pair<Int, String>>()

        texts.forEachIndexed { index, text ->
            val cached = decodeCachedVector(cache.get(keys[index]))
            if (cached != null) {
                results[index] = cached
            } else {
                misses += index to text
            }
        }

        if (misses.isNotEmpty()) {
            val missVectors = delegate.embedTexts(misses.map { (_, text) -> text })
            require(missVectors.size == misses.size) {
                "Embedding backend returned ${missVectors.size} vectors for ${misses.size} inputs."
            }
            misses.forEachIndexed { missIndex, (originalIndex, _) ->
                val vector = missVectors[missIndex]
                results[originalIndex] = vector
                cache.put(keys[originalIndex], vector)
            }
        }

        return results.mapIndexed { index, vector ->
            checkNotNull(vector) { "Missing embedding result at index $index." }
        }
    }

    private fun decodeCachedVector(cached: Any?): List<Float>? {
        if (cached is List<*> && cached.all { it is Number }) {
            return cached.map { value -> (value as Number).toFloat() }
        }
        return null
    }
}
