package ragas.cache

interface CacheBackend {
    fun get(key: String): Any?

    fun put(
        key: String,
        value: Any?,
    )
}
