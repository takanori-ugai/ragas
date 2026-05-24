package ragas.testset.transforms

import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import ragas.testset.graph.Relationship
import kotlin.math.max

/**
 * Splits document text around extracted headlines and adjusts chunk sizes by token count.
 */
class HeadlineSplitter(
    override val name: String = "headline_splitter",
    override val filterNodes: (Node) -> Boolean = { node -> node.type == NodeType.DOCUMENT },
    private val sourceProperty: String = "page_content",
    private val headlinesProperty: String = "headlines",
    private val minTokens: Int = 300,
    private val maxTokens: Int = 1000,
) : Splitter(name = name, filterNodes = filterNodes) {
    init {
        require(minTokens > 0) { "minTokens must be > 0" }
        require(maxTokens >= minTokens) { "maxTokens must be >= minTokens" }
    }

    override suspend fun split(node: Node): Pair<List<Node>, List<Relationship>> {
        val text = node.getProperty(sourceProperty) ?: error("'$sourceProperty' property not found in this node")
        val headlinesRaw = node.getProperty(headlinesProperty) ?: error("'$headlinesProperty' property not found in this node")
        val headlines = headlinesRaw.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (countTokens(text) < minTokens) {
            return emptyList<Node>() to emptyList()
        }

        val indices = mutableListOf(0)
        var searchFrom = 0
        headlines.forEach { headline ->
            val idx = text.indexOf(headline, startIndex = searchFrom)
            if (idx >= 0) {
                indices += idx
                searchFrom = idx + headline.length
            }
        }
        indices += text.length
        val normalizedIndices = indices.distinct().sorted()

        val baseChunks =
            normalizedIndices
                .zipWithNext()
                .mapNotNull { (start, end) ->
                    if (end <= start) null else text.substring(start, end).trim().takeIf { it.isNotBlank() }
                }
        val chunks = adjustChunks(baseChunks)
        if (chunks.size <= 1) {
            return emptyList<Node>() to emptyList()
        }

        val chunkNodes =
            chunks.mapIndexed { index, chunk ->
                Node(
                    type = NodeType.CHUNK,
                    properties =
                        mutableMapOf(
                            "page_content" to chunk,
                            "chunk_index" to index.toString(),
                            "parent_document_id" to node.id,
                            "source_document_summary" to node.getProperty(PropertyNames.SUMMARY_LLM_BASED).orEmpty(),
                            "source_document_entities" to node.getProperty(PropertyNames.ENTITIES_REGEX).orEmpty(),
                            "source_document_topic" to node.getProperty(PropertyNames.EMBEDDING_TOPIC_TAG).orEmpty(),
                        ),
                )
            }

        val relationships = mutableListOf<Relationship>()
        chunkNodes.forEach { chunk ->
            relationships += Relationship(type = "child", sourceId = node.id, targetId = chunk.id)
        }
        chunkNodes.zipWithNext().forEach { (left, right) ->
            relationships += Relationship(type = "next", sourceId = left.id, targetId = right.id)
        }
        return chunkNodes to relationships
    }

    private fun adjustChunks(chunks: List<String>): List<String> {
        val adjusted = mutableListOf<String>()
        var carry = ""

        chunks.forEach { original ->
            var chunk = original.trim()
            var tokenCount = countTokens(chunk)

            while (tokenCount > maxTokens) {
                val words = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) {
                    break
                }
                val splitRatio = maxTokens.toDouble() / tokenCount.toDouble()
                val splitPoint = max(1, (words.size * splitRatio).toInt())
                val head = words.take(splitPoint).joinToString(" ").trim()
                if (head.isNotBlank()) {
                    adjusted += head
                }
                chunk = words.drop(splitPoint).joinToString(" ").trim()
                tokenCount = countTokens(chunk)
            }

            if (chunk.isBlank()) {
                return@forEach
            }

            if (tokenCount < minTokens) {
                carry =
                    if (carry.isBlank()) {
                        chunk
                    } else {
                        "$carry $chunk".trim()
                    }
                if (countTokens(carry) >= minTokens) {
                    adjusted += carry
                    carry = ""
                }
            } else {
                if (carry.isNotBlank()) {
                    chunk = "$carry $chunk".trim()
                    carry = ""
                }
                adjusted += chunk
            }
        }

        if (carry.isNotBlank()) {
            if (adjusted.isNotEmpty()) {
                adjusted[adjusted.lastIndex] = "${adjusted.last()} $carry".trim()
            } else {
                adjusted += carry
            }
        }

        return adjusted.filter { it.isNotBlank() }
    }

    private fun countTokens(text: String): Int = text.split(Regex("\\s+")).count { token -> token.isNotBlank() }
}
