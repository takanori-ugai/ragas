package ragas.testset.transforms

import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import ragas.testset.graph.Relationship

/**
 * Relationship builder that links consecutive chunks from the same parent document.
 *
 * @property name Transformation name.
 * @property filterNodes Node selection predicate for document nodes.
 */
class AdjacentChunkRelationshipBuilder(
    override val name: String = "adjacent_chunk_relationship_builder",
    override val filterNodes: (Node) -> Boolean = { node -> node.type == NodeType.DOCUMENT },
) : RelationshipBuilder(name = name, filterNodes = filterNodes) {
    /**
     * Builds relationships from the current graph based on this builder's strategy.
     *
     * @param kg Full graph to inspect.
     * @param filtered Graph filtered by [filterNodes] used for document candidate selection.
     * @return Generated `next` relationships between adjacent chunks.
     */
    override suspend fun build(
        kg: KnowledgeGraph,
        filtered: KnowledgeGraph,
    ): List<Relationship> {
        val documentIds =
            filtered.nodes
                .map { node -> node.id }
                .toSet()
        if (documentIds.isEmpty()) {
            return emptyList()
        }

        val chunkNodesById =
            kg.nodes
                .filter { node -> node.type == NodeType.CHUNK }
                .associateBy { node -> node.id }

        val byDocument =
            kg.relationships
                .asSequence()
                .filter { rel -> rel.type == "child" && rel.sourceId in documentIds }
                .mapNotNull { rel ->
                    val chunk = chunkNodesById[rel.targetId] ?: return@mapNotNull null
                    val index = chunk.getProperty("chunk_index")?.toIntOrNull() ?: Int.MAX_VALUE
                    Triple(rel.sourceId, index, chunk)
                }.groupBy { it.first }

        return byDocument.values.flatMap { triples ->
            val sorted = triples.sortedBy { it.second }.map { it.third }
            sorted
                .zipWithNext()
                .map { (left, right) ->
                    Relationship(
                        type = "next",
                        sourceId = left.id,
                        targetId = right.id,
                    )
                }
        }
    }
}

/**
 * Relationship builder that connects nodes sharing enough normalized keywords.
 *
 * @property name Transformation name.
 * @property filterNodes Node selection predicate for candidate nodes.
 * @property sourceProperty Source property key.
 * @property minSharedKeywords Minimum shared keywords threshold.
 */
class SharedKeywordRelationshipBuilder(
    override val name: String = "shared_keyword_relationship_builder",
    override val filterNodes: (Node) -> Boolean = { node -> node.type == NodeType.CHUNK },
    private val sourceProperty: String = "page_content",
    private val minSharedKeywords: Int = 2,
) : RelationshipBuilder(name = name, filterNodes = filterNodes) {
    init {
        require(minSharedKeywords > 0) { "minSharedKeywords must be > 0" }
    }

    /**
     * Builds relationships from the current graph based on this builder's strategy.
     *
     * @param kg Full graph to inspect.
     * @param filtered Graph filtered by [filterNodes] used for candidate node selection.
     * @return Generated `semantic_overlap` relationships.
     */
    override suspend fun build(
        kg: KnowledgeGraph,
        filtered: KnowledgeGraph,
    ): List<Relationship> {
        val nodes = filtered.nodes
        if (nodes.size < 2) {
            return emptyList()
        }

        val keywordsByNodeId =
            nodes.associate { node ->
                val keywords =
                    node
                        .getProperty(sourceProperty)
                        .orEmpty()
                        .lowercase()
                        .split(Regex("[^a-z0-9]+"))
                        .filter { token -> token.length >= 4 }
                        .toSet()
                node.id to keywords
            }

        val relationships = mutableListOf<Relationship>()
        for (leftIndex in 0 until nodes.size - 1) {
            val left = nodes[leftIndex]
            val leftKeywords = keywordsByNodeId[left.id].orEmpty()
            if (leftKeywords.isEmpty()) {
                continue
            }
            for (rightIndex in leftIndex + 1 until nodes.size) {
                val right = nodes[rightIndex]
                val shared = leftKeywords.intersect(keywordsByNodeId[right.id].orEmpty())
                if (shared.size >= minSharedKeywords) {
                    relationships +=
                        Relationship(
                            type = "semantic_overlap",
                            sourceId = left.id,
                            targetId = right.id,
                            properties =
                                mapOf(
                                    "shared_keyword_count" to shared.size.toString(),
                                ),
                        )
                }
            }
        }

        return relationships
    }
}

/**
 * Relationship builder that links nodes by cosine similarity over vector-like properties.
 *
 * Vector property values may be comma-delimited (`0.1,0.2`) or JSON arrays (`[0.1, 0.2]`).
 */
open class CosineSimilarityBuilder(
    override val name: String = "cosine_similarity_builder",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val propertyName: String = "embedding",
    private val newPropertyName: String = "cosine_similarity",
    private val threshold: Double = 0.9,
) : RelationshipBuilder(name = name, filterNodes = filterNodes) {
    init {
        require(threshold in 0.0..1.0) { "threshold must be between 0 and 1" }
    }

    override suspend fun build(
        kg: KnowledgeGraph,
        filtered: KnowledgeGraph,
    ): List<Relationship> {
        val nodes = filtered.nodes
        if (nodes.size < 2) {
            return emptyList()
        }
        val vectors =
            nodes.map { node ->
                parseVector(node.getProperty(propertyName) ?: error("Node ${node.id} has no $propertyName"))
            }
        validateVectorShapes(vectors)

        val relationships = mutableListOf<Relationship>()
        for (i in 0 until nodes.lastIndex) {
            for (j in i + 1 until nodes.size) {
                val similarity = cosineSimilarity(vectors[i], vectors[j])
                if (similarity >= threshold) {
                    relationships +=
                        Relationship(
                            type = newPropertyName,
                            sourceId = nodes[i].id,
                            targetId = nodes[j].id,
                            bidirectional = true,
                            properties = mapOf(newPropertyName to formatDouble(similarity)),
                        )
                }
            }
        }
        return relationships
    }

    protected fun parseVector(raw: String): List<Double> {
        val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
        return cleaned
            .split(Regex("[,\\s]+"))
            .filter { token -> token.isNotBlank() }
            .map { token -> token.toDouble() }
    }

    protected fun validateVectorShapes(vectors: List<List<Double>>) {
        if (vectors.isEmpty()) {
            return
        }
        val first = vectors.first().size
        vectors.forEachIndexed { index, vector ->
            require(vector.size == first) {
                "Embedding at index $index has length ${vector.size}, expected $first."
            }
        }
    }

    private fun cosineSimilarity(
        left: List<Double>,
        right: List<Double>,
    ): Double {
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { idx ->
            val l = left[idx]
            val r = right[idx]
            dot += l * r
            leftNorm += l * l
            rightNorm += r * r
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0
        }
        return dot / (kotlin.math.sqrt(leftNorm) * kotlin.math.sqrt(rightNorm))
    }
}

/**
 * Document-only cosine similarity builder over summary embeddings.
 */
class SummaryCosineSimilarityBuilder(
    override val name: String = "summary_cosine_similarity_builder",
    override val filterNodes: (Node) -> Boolean = { node -> node.type == NodeType.DOCUMENT },
    propertyName: String = "summary_embedding",
    newPropertyName: String = "summary_similarity",
    threshold: Double = 0.7,
) : CosineSimilarityBuilder(
        name = name,
        filterNodes = filterNodes,
        propertyName = propertyName,
        newPropertyName = newPropertyName,
        threshold = threshold,
    )

/**
 * Relationship builder based on Jaccard similarity for list-like string properties.
 */
class JaccardSimilarityBuilder(
    override val name: String = "jaccard_similarity_builder",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val propertyName: String = PropertyNames.ENTITIES_REGEX,
    private val newPropertyName: String = "jaccard_similarity",
    private val threshold: Double = 0.5,
) : RelationshipBuilder(name = name, filterNodes = filterNodes) {
    init {
        require(threshold in 0.0..1.0) { "threshold must be between 0 and 1" }
    }

    override suspend fun build(
        kg: KnowledgeGraph,
        filtered: KnowledgeGraph,
    ): List<Relationship> {
        val nodes = filtered.nodes
        val relationships = mutableListOf<Relationship>()
        for (i in 0 until nodes.lastIndex) {
            val leftItems = parseItems(nodes[i].getProperty(propertyName))
            if (leftItems.isEmpty()) {
                continue
            }
            for (j in i + 1 until nodes.size) {
                val rightItems = parseItems(nodes[j].getProperty(propertyName))
                if (rightItems.isEmpty()) {
                    continue
                }
                val similarity = jaccard(leftItems, rightItems)
                if (similarity >= threshold) {
                    relationships +=
                        Relationship(
                            type = newPropertyName,
                            sourceId = nodes[i].id,
                            targetId = nodes[j].id,
                            bidirectional = true,
                            properties = mapOf(newPropertyName to formatDouble(similarity)),
                        )
                }
            }
        }
        return relationships
    }

    private fun jaccard(
        left: Set<String>,
        right: Set<String>,
    ): Double {
        val union = left.union(right)
        if (union.isEmpty()) {
            return 0.0
        }
        return left.intersect(right).size.toDouble() / union.size.toDouble()
    }
}

/**
 * Relationship builder that measures approximate item overlap with simple string similarity.
 */
class OverlapScoreBuilder(
    override val name: String = "overlap_score_builder",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val propertyName: String = PropertyNames.ENTITIES_REGEX,
    private val newPropertyName: String = "overlap_score",
    private val distanceThreshold: Double = 0.9,
    private val threshold: Double = 0.01,
) : RelationshipBuilder(name = name, filterNodes = filterNodes) {
    init {
        require(distanceThreshold in 0.0..1.0) { "distanceThreshold must be between 0 and 1" }
        require(threshold in 0.0..1.0) { "threshold must be between 0 and 1" }
    }

    override suspend fun build(
        kg: KnowledgeGraph,
        filtered: KnowledgeGraph,
    ): List<Relationship> {
        val nodes = filtered.nodes
        val noisyItems = noisyItems(nodes, propertyName, 0.05)
        val relationships = mutableListOf<Relationship>()
        for (i in 0 until nodes.lastIndex) {
            val leftItems = parseItems(nodes[i].getProperty(propertyName)).filterNot { it in noisyItems }
            for (j in i + 1 until nodes.size) {
                val rightItems = parseItems(nodes[j].getProperty(propertyName)).filterNot { it in noisyItems }
                if (leftItems.isEmpty() || rightItems.isEmpty()) {
                    continue
                }
                val overlaps = mutableListOf<Boolean>()
                val overlappedPairs = mutableListOf<String>()
                leftItems.forEach { left ->
                    rightItems.forEach { right ->
                        val similarity = normalizedSimilarity(left, right)
                        val verdict = similarity >= distanceThreshold
                        overlaps += verdict
                        if (verdict) {
                            overlappedPairs += "$left|$right"
                        }
                    }
                }
                val score = if (overlaps.isEmpty()) 0.0 else overlaps.count { it }.toDouble() / overlaps.size.toDouble()
                if (score >= threshold) {
                    relationships +=
                        Relationship(
                            type = "${propertyName}_overlap",
                            sourceId = nodes[i].id,
                            targetId = nodes[j].id,
                            bidirectional = true,
                            properties =
                                mapOf(
                                    "${propertyName}_$newPropertyName" to formatDouble(score),
                                    "overlapped_items" to overlappedPairs.joinToString(";"),
                                ),
                        )
                }
            }
        }
        return relationships
    }

    private fun noisyItems(
        nodes: List<Node>,
        propertyName: String,
        percentCutOff: Double,
    ): Set<String> {
        val allItems =
            nodes
                .flatMap { node -> parseItems(node.getProperty(propertyName)) }
                .filter { it.isNotBlank() }
        if (allItems.isEmpty()) {
            return emptySet()
        }
        val uniqueCount = allItems.toSet().size
        val noisyCount = kotlin.math.max(1, (uniqueCount * percentCutOff).toInt())
        return allItems
            .groupingBy { item -> item }
            .eachCount()
            .entries
            .sortedByDescending { (_, count) -> count }
            .take(noisyCount)
            .map { (item) -> item }
            .toSet()
    }

    private fun normalizedSimilarity(
        left: String,
        right: String,
    ): Double {
        if (left.equals(right, ignoreCase = true)) {
            return 1.0
        }
        val distance = levenshtein(left.lowercase(), right.lowercase())
        val denom = kotlin.math.max(left.length, right.length).coerceAtLeast(1)
        return 1.0 - (distance.toDouble() / denom.toDouble())
    }

    private fun levenshtein(
        left: String,
        right: String,
    ): Int {
        if (left == right) {
            return 0
        }
        if (left.isEmpty()) {
            return right.length
        }
        if (right.isEmpty()) {
            return left.length
        }
        val prev = IntArray(right.length + 1) { it }
        val curr = IntArray(right.length + 1)
        for (i in 1..left.length) {
            curr[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                curr[j] =
                    minOf(
                        curr[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost,
                    )
            }
            for (j in prev.indices) {
                prev[j] = curr[j]
            }
        }
        return prev[right.length]
    }
}

private fun parseItems(raw: String?): Set<String> {
    if (raw.isNullOrBlank()) {
        return emptySet()
    }
    return raw
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(Regex("[,\\n;]+"))
        .map { token -> token.trim().trim('"') }
        .filter { token -> token.isNotBlank() }
        .toSet()
}

private fun formatDouble(value: Double): String = "%.6f".format(java.util.Locale.US, value)
