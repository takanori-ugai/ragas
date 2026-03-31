package ragas.testset.graph

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

enum class NodeType {
    UNKNOWN,
    DOCUMENT,
    CHUNK,
}

@Serializable
data class Node(
    val id: String = UUID.randomUUID().toString(),
    // Mutable by design; data-class copy() is shallow for this map.
    val properties: MutableMap<String, String> = mutableMapOf(),
    val type: NodeType = NodeType.UNKNOWN,
) {
    init {
        if (properties.isNotEmpty()) {
            val normalized =
                properties.entries.groupBy { (key, _) -> key.lowercase() }
            val duplicates = normalized.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) {
                "Duplicate property keys when normalized to lowercase: ${duplicates.joinToString(", ")}"
            }
            properties.clear()
            properties.putAll(normalized.mapValues { (_, entries) -> entries.first().value })
        }
    }

    fun addProperty(
        key: String,
        value: String,
    ) {
        val normalized = key.lowercase()
        require(normalized !in properties) { "Property $key already exists" }
        properties[normalized] = value
    }

    fun getProperty(key: String): String? = properties[key.lowercase()]
}

@Serializable
data class Relationship(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val sourceId: String,
    val targetId: String,
    val bidirectional: Boolean = false,
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
private data class SerializableKnowledgeGraph(
    val nodes: List<Node>,
    val relationships: List<Relationship>,
)

class KnowledgeGraph(
    val nodes: MutableList<Node> = mutableListOf(),
    val relationships: MutableList<Relationship> = mutableListOf(),
) {
    fun add(node: Node) {
        nodes += node
    }

    fun add(relationship: Relationship) {
        relationships += relationship
    }

    fun getNodeById(nodeId: String): Node? = nodes.firstOrNull { node -> node.id == nodeId }

    fun save(path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        val payload = SerializableKnowledgeGraph(nodes = nodes, relationships = relationships)
        file.writeText(graphJson.encodeToString(payload))
    }

    override fun toString(): String = "KnowledgeGraph(nodes=${nodes.size}, relationships=${relationships.size})"

    companion object {
        fun load(path: String): KnowledgeGraph {
            val file = File(path)
            require(file.exists()) { "Knowledge graph file not found: $path" }
            val payload = graphJson.decodeFromString<SerializableKnowledgeGraph>(file.readText())
            return KnowledgeGraph(
                nodes = payload.nodes.toMutableList(),
                relationships = payload.relationships.toMutableList(),
            )
        }
    }
}

private val graphJson =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
