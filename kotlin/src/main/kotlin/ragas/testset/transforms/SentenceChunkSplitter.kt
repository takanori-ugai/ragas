package ragas.testset.transforms

import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import ragas.testset.graph.Relationship

class SentenceChunkSplitter(
    override val name: String = "sentence_chunk_splitter",
    override val filterNodes: (Node) -> Boolean = { node -> node.type == NodeType.DOCUMENT },
    private val sourceProperty: String = "page_content",
    private val maxSentencesPerChunk: Int = 1,
) : Splitter(name = name, filterNodes = filterNodes) {
    init {
        require(maxSentencesPerChunk > 0) { "maxSentencesPerChunk must be > 0" }
    }

    override suspend fun split(node: Node): Pair<List<Node>, List<Relationship>> {
        val text = node.getProperty(sourceProperty).orEmpty().trim()
        if (text.isBlank()) {
            return emptyList<Node>() to emptyList()
        }

        val sentences =
            sentenceRegex
                .findAll(text)
                .map { match -> match.value.trim() }
                .filter { sentence -> sentence.isNotBlank() }
                .toList()

        if (sentences.isEmpty()) {
            return emptyList<Node>() to emptyList()
        }

        val grouped = sentences.chunked(maxSentencesPerChunk)
        val chunks =
            grouped.mapIndexed { index, parts ->
                val chunkText = parts.joinToString(" ").trim()
                Node(
                    type = NodeType.CHUNK,
                    properties =
                        mutableMapOf(
                            "page_content" to chunkText,
                            "chunk_index" to index.toString(),
                            "parent_document_id" to node.id,
                            "source_document_summary" to node.getProperty("summary_llm_based").orEmpty(),
                            "source_document_entities" to node.getProperty("entities_regex").orEmpty(),
                            "source_document_topic" to node.getProperty("embedding_topic_tag").orEmpty(),
                        ),
                )
            }

        val relationships =
            chunks.map { chunk ->
                Relationship(
                    type = "child",
                    sourceId = node.id,
                    targetId = chunk.id,
                )
            }

        return chunks to relationships
    }

    companion object {
        private val sentenceRegex = Regex("[^.!?]+[.!?]?")
    }
}
