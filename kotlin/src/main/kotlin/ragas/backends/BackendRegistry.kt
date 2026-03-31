package ragas.backends

class BackendRegistry {
    private val backends = mutableMapOf<String, () -> BaseBackend>()
    private val aliases = mutableMapOf<String, String>()

    fun register(
        name: String,
        factory: () -> BaseBackend,
        aliasList: List<String> = emptyList(),
    ) {
        require(name.isNotBlank()) { "Backend name must be a non-empty string" }
        backends[name] = factory
        aliasList.forEach { alias ->
            if (alias.isNotBlank()) {
                aliases[alias] = name
            }
        }
    }

    fun create(name: String): BaseBackend {
        val resolved = aliases[name] ?: name
        val factory = backends[resolved]
            ?: throw NoSuchElementException("Backend '$name' not found. Available backends: ${availableNames()}")
        return factory()
    }

    fun contains(name: String): Boolean = name in backends || name in aliases

    fun keys(): Set<String> = backends.keys

    fun availableNames(): List<String> = (backends.keys + aliases.keys).sorted()

    fun clear() {
        backends.clear()
        aliases.clear()
    }
}

val BACKEND_REGISTRY = BackendRegistry().apply {
    register("inmemory", ::InMemoryBackend)
    register("local/csv", { LocalCsvBackend(".") }, aliasList = listOf("csv"))
    register("local/jsonl", { LocalJsonlBackend(".") }, aliasList = listOf("jsonl"))
}

fun registerBackend(
    name: String,
    factory: () -> BaseBackend,
    aliases: List<String> = emptyList(),
) {
    BACKEND_REGISTRY.register(name, factory, aliases)
}
