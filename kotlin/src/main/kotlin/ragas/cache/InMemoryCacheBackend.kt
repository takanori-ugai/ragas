package ragas.cache

import java.util.concurrent.ConcurrentHashMap

class InMemoryCacheBackend : CacheBackend {
    private val store = ConcurrentHashMap<String, Any?>()

    override fun get(key: String): Any? = store[key]

    override fun put(
        key: String,
        value: Any?,
    ) {
        store[key] = value
    }
}
