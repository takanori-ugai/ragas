package ragas.testset.transforms

import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.graph.Relationship

val defaultFilter: (Node) -> Boolean = { true }

interface BaseGraphTransformation {
    val name: String
    val filterNodes: (Node) -> Boolean

    suspend fun transform(kg: KnowledgeGraph): Any?

    fun filter(kg: KnowledgeGraph): KnowledgeGraph {
        val filteredNodes = kg.nodes.filter(filterNodes)
        val nodeIds = filteredNodes.map { node -> node.id }.toSet()
        val filteredRelationships =
            kg.relationships.filter { rel -> rel.sourceId in nodeIds && rel.targetId in nodeIds }
        return KnowledgeGraph(
            nodes = filteredNodes.toMutableList(),
            relationships = filteredRelationships.toMutableList(),
        )
    }

    fun generateExecutionPlan(kg: KnowledgeGraph): List<suspend () -> Unit>
}

abstract class Extractor(
    override val name: String,
    override val filterNodes: (Node) -> Boolean = defaultFilter,
) : BaseGraphTransformation {
    abstract suspend fun extract(node: Node): Pair<String, String>

    override suspend fun transform(kg: KnowledgeGraph): List<Pair<Node, Pair<String, String>>> {
        val filtered = filter(kg)
        return filtered.nodes.map { node ->
            node to extract(node)
        }
    }

    override fun generateExecutionPlan(kg: KnowledgeGraph): List<suspend () -> Unit> {
        val filtered = filter(kg)
        return filtered.nodes.map { node ->
            suspend {
                val (key, value) = extract(node)
                synchronized(node) {
                    if (node.getProperty(key) == null) {
                        node.addProperty(key, value)
                    }
                }
            }
        }
    }
}

abstract class Splitter(
    override val name: String,
    override val filterNodes: (Node) -> Boolean = defaultFilter,
) : BaseGraphTransformation {
    abstract suspend fun split(node: Node): Pair<List<Node>, List<ragas.testset.graph.Relationship>>

    override suspend fun transform(kg: KnowledgeGraph): Pair<List<Node>, List<ragas.testset.graph.Relationship>> {
        val filtered = filter(kg)
        val allNodes = mutableListOf<Node>()
        val allRelationships = mutableListOf<ragas.testset.graph.Relationship>()
        filtered.nodes.forEach { node ->
            val (nodes, relationships) = split(node)
            allNodes += nodes
            allRelationships += relationships
        }
        return allNodes to allRelationships
    }

    override fun generateExecutionPlan(kg: KnowledgeGraph): List<suspend () -> Unit> {
        val filtered = filter(kg)
        return filtered.nodes.map { node ->
            suspend {
                val (nodes, relationships) = split(node)
                synchronized(kg) {
                    kg.nodes += nodes
                    kg.relationships += relationships
                }
            }
        }
    }
}

abstract class RelationshipBuilder(
    override val name: String,
    override val filterNodes: (Node) -> Boolean = defaultFilter,
) : BaseGraphTransformation {
    abstract suspend fun build(kg: KnowledgeGraph): List<Relationship>

    override suspend fun transform(kg: KnowledgeGraph): List<Relationship> = build(kg)

    override fun generateExecutionPlan(kg: KnowledgeGraph): List<suspend () -> Unit> =
        listOf(
            suspend {
                val built = build(kg)
                synchronized(kg) {
                    built.forEach { relationship ->
                        if (!kg.hasRelationship(relationship)) {
                            kg.relationships += relationship
                        }
                    }
                }
            },
        )
}

private fun KnowledgeGraph.hasRelationship(candidate: Relationship): Boolean =
    relationships.any { existing ->
        existing.type == candidate.type &&
            existing.sourceId == candidate.sourceId &&
            existing.targetId == candidate.targetId &&
            existing.properties == candidate.properties
    }
