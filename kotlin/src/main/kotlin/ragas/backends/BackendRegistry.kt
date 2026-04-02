package ragas.backends

import java.util.ServiceLoader
import kotlin.reflect.KClass

data class BackendInfo(
    val name: String,
    val aliases: List<String>,
    val implementationClass: String,
    val module: String,
    val description: String,
    val source: String,
)

interface BackendDiscoveryProvider {
    fun registerBackends(registry: BackendRegistry)
}

class BackendRegistry {
    private data class BackendRegistration(
        val factory: () -> BaseBackend,
        val backendClass: KClass<out BaseBackend>?,
        val description: String?,
        val source: String,
    )

    private val backends = mutableMapOf<String, BackendRegistration>()
    private val aliases = mutableMapOf<String, String>()
    private var discovered = false

    @Synchronized
    fun register(
        name: String,
        factory: () -> BaseBackend,
        aliasList: List<String> = emptyList(),
        backendClass: KClass<out BaseBackend>? = null,
        description: String? = null,
        source: String = "runtime",
    ) {
        require(name.isNotBlank()) { "Backend name must be a non-empty string" }
        backends[name] =
            BackendRegistration(
                factory = factory,
                backendClass = backendClass,
                description = description,
                source = source,
            )
        registerAliases(name, aliasList)
    }

    @Synchronized
    fun registerAliases(
        name: String,
        aliasList: List<String>,
        overwrite: Boolean = false,
    ) {
        require(name in backends) { "Backend '$name' not found." }
        aliasList.forEach { alias ->
            if (alias.isNotBlank()) {
                val existing = aliases[alias]
                require(overwrite || existing == null || existing == name) {
                    "Alias '$alias' is already registered for backend '$existing'."
                }
                aliases[alias] = name
            }
        }
    }

    @Synchronized
    fun discoverBackends(force: Boolean = false): Int {
        if (discovered) {
            if (!force) {
                return 0
            }
            discovered = false
        }
        var loadedProviders = 0
        val loader = ServiceLoader.load(BackendDiscoveryProvider::class.java)
        loader.forEach { provider ->
            try {
                provider.registerBackends(this)
                loadedProviders += 1
            } catch (e: Exception) {
                val providerName = provider::class.qualifiedName ?: provider.javaClass.name
                System.err.println("Backend discovery provider failed: $providerName: ${e.message ?: e::class.simpleName}")
            }
        }
        discovered = true
        return loadedProviders
    }

    @Synchronized
    private fun ensureDiscovered() {
        if (!discovered) {
            discoverBackends()
        }
    }

    fun create(name: String): BaseBackend {
        ensureDiscovered()
        val resolved = aliases[name] ?: name
        val registration =
            backends[resolved]
                ?: throw NoSuchElementException("Backend '$name' not found. Available backends: ${availableNames()}")
        return registration.factory()
    }

    fun contains(name: String): Boolean {
        ensureDiscovered()
        return name in backends || name in aliases
    }

    fun keys(): Set<String> {
        ensureDiscovered()
        return backends.keys
    }

    fun availableNames(): List<String> {
        ensureDiscovered()
        return (backends.keys + aliases.keys).sorted()
    }

    fun listAllNames(): Map<String, List<String>> {
        ensureDiscovered()
        return backends.keys.associateWith { name ->
            listOf(name) + aliasesFor(name)
        }
    }

    fun getBackendInfo(name: String): BackendInfo {
        ensureDiscovered()
        val resolved = aliases[name] ?: name
        val registration =
            backends[resolved]
                ?: throw NoSuchElementException("Backend '$name' not found. Available backends: ${availableNames()}")
        val backendClass = registration.backendClass
        val qualifiedName = backendClass?.qualifiedName ?: "unknown"
        val module = qualifiedName.substringBeforeLast('.', "")
        return BackendInfo(
            name = resolved,
            aliases = aliasesFor(resolved),
            implementationClass = qualifiedName,
            module = module,
            description = registration.description ?: "No documentation available",
            source = registration.source,
        )
    }

    fun listBackendInfo(): List<BackendInfo> {
        ensureDiscovered()
        return backends.keys.sorted().map { name ->
            getBackendInfo(name)
        }
    }

    private fun aliasesFor(name: String): List<String> = aliases.filterValues { it == name }.keys.sorted()

    @JvmOverloads
    fun clear(resetDiscovery: Boolean = true) {
        backends.clear()
        aliases.clear()
        if (resetDiscovery) {
            discovered = false
        }
    }
}

val BACKEND_REGISTRY =
    BackendRegistry().apply {
        register(
            name = "inmemory",
            factory = ::InMemoryBackend,
            backendClass = InMemoryBackend::class,
            description = "In-memory backend for temporary dataset and experiment storage.",
            source = "builtin",
        )
        register(
            name = "local/csv",
            factory = { LocalCsvBackend(".") },
            aliasList = listOf("csv"),
            backendClass = LocalCsvBackend::class,
            description = "Local filesystem backend using CSV storage.",
            source = "builtin",
        )
        register(
            name = "local/jsonl",
            factory = { LocalJsonlBackend(".") },
            aliasList = listOf("jsonl"),
            backendClass = LocalJsonlBackend::class,
            description = "Local filesystem backend using JSONL storage.",
            source = "builtin",
        )
    }

fun registerBackend(
    name: String,
    factory: () -> BaseBackend,
    aliases: List<String> = emptyList(),
    backendClass: KClass<out BaseBackend>? = null,
    description: String? = null,
    source: String = "runtime",
) {
    BACKEND_REGISTRY.register(name, factory, aliases, backendClass, description, source)
}
