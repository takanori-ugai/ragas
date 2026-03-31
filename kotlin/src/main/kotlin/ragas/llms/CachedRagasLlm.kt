package ragas.llms

import ragas.cache.CacheBackend
import ragas.cache.stableCacheKey
import ragas.runtime.RunConfig

class CachedRagasLlm(
    private val delegate: BaseRagasLlm,
    private val cache: CacheBackend,
) : BaseRagasLlm {
    override var runConfig: RunConfig
        get() = delegate.runConfig
        set(value) {
            delegate.runConfig = value
        }

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        val key =
            stableCacheKey(
                buildString {
                    append("llm|")
                    append(prompt)
                    append("|n=")
                    append(n)
                    append("|temp=")
                    append(temperature)
                    append("|stop=")
                    append(stop?.joinToString(",") ?: "")
                },
            )

        val cached = cache.get(key)
        if (cached is LlmResult) {
            return cached
        }

        val result = delegate.generateText(prompt, n, temperature, stop)
        cache.put(key, result)
        return result
    }
}
