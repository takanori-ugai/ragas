package ragas.cache

/**
 * Cache backend for storing arbitrary values by string key.
 *
 * Implementations are expected to be safe for concurrent access because callers
 * may read/write cache entries from multiple coroutines or threads.
 */
interface CacheBackend {
    fun get(key: String): Any?

    fun put(
        key: String,
        value: Any?,
    )
}
