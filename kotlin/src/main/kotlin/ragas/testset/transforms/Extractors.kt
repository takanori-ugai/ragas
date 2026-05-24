package ragas.testset.transforms

import ragas.testset.graph.Node
import kotlin.math.min

/**
 * Extractor that derives entity strings from node text using a regex pattern.
 *
 * @property name Transformation name.
 * @property filterNodes Node selection predicate.
 * @property sourceProperty Source property key.
 * @property targetProperty Target property key.
 * @property maxEntities Maximum extracted entities.
 */
class RegexEntityExtractor(
    override val name: String = "regex_entity_extractor",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val sourceProperty: String = "page_content",
    private val targetProperty: String = PropertyNames.ENTITIES_REGEX,
    private val maxEntities: Int = 8,
) : Extractor(name = name, filterNodes = filterNodes) {
    private val entityPattern = Regex("\\b[A-Z][a-zA-Z]{2,}\\b")

    /**
     * Returns the property key written by this extractor.
     *
     * @param node Node to extract from.
     * @return Target property name.
     */
    override fun propertyName(node: Node): String = targetProperty

    /**
     * Extracts one property value from a node.
     *
     * @param node Node to extract from.
     * @return Extracted entity list.
     */
    override suspend fun extract(node: Node): String {
        val text = node.getProperty(sourceProperty).orEmpty()
        if (text.isBlank()) {
            return ""
        }

        val entities =
            entityPattern
                .findAll(text)
                .map { match -> match.value }
                .distinct()
                .take(maxEntities)
                .toList()

        return entities.joinToString(", ")
    }
}

/**
 * Extractor that derives a deterministic topic token from node text as a lightweight stand-in for embedding-based topics.
 *
 * @property name Transformation name.
 * @property filterNodes Node selection predicate.
 * @property sourceProperty Source property key.
 * @property targetProperty Target property key.
 */
class EmbeddingsTopicExtractor(
    override val name: String = "embeddings_topic_extractor",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val sourceProperty: String = "page_content",
    private val targetProperty: String = PropertyNames.EMBEDDING_TOPIC_TAG,
) : Extractor(name = name, filterNodes = filterNodes) {
    /**
     * Returns the property key written by this extractor.
     *
     * @param node Node to extract from.
     * @return Target property name.
     */
    override fun propertyName(node: Node): String = targetProperty

    /**
     * Extracts one property value from a node.
     *
     * @param node Node to extract from.
     * @return Extracted topic token.
     */
    override suspend fun extract(node: Node): String {
        val text = node.getProperty(sourceProperty).orEmpty()
        val token =
            text
                .lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { word -> word.length >= 4 }
                .groupingBy { word -> word }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
                .firstOrNull()
                ?.key
                .orEmpty()

        return token
    }
}

/**
 * Extractor that derives a short summary from node text using sentence-level clipping heuristics.
 *
 * @property name Transformation name.
 * @property filterNodes Node selection predicate.
 * @property sourceProperty Source property key.
 * @property targetProperty Target property key.
 * @property maxWords Maximum summary words.
 */
class LlmBasedSummaryExtractor(
    override val name: String = "llm_based_summary_extractor",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val sourceProperty: String = "page_content",
    private val targetProperty: String = PropertyNames.SUMMARY_LLM_BASED,
    private val maxWords: Int = 24,
) : Extractor(name = name, filterNodes = filterNodes) {
    /**
     * Returns the property key written by this extractor.
     *
     * @param node Node to extract from.
     * @return Target property name.
     */
    override fun propertyName(node: Node): String = targetProperty

    /**
     * Extracts one property value from a node.
     *
     * @param node Node to extract from.
     * @return Extracted summary text.
     */
    override suspend fun extract(node: Node): String {
        val text = node.getProperty(sourceProperty).orEmpty().trim()
        if (text.isBlank()) {
            return ""
        }

        val sentences =
            text
                .split(Regex("(?<=[.!?])\\s+"))
                .map { sentence -> sentence.trim() }
                .filter { sentence -> sentence.isNotBlank() }

        val summary = sentences.take(2).joinToString(" ")
        val words = summary.split(Regex("\\s+")).filter { word -> word.isNotBlank() }
        val clipped = words.take(min(words.size, maxWords)).joinToString(" ")
        return clipped
    }
}

/**
 * Extractor that derives section headlines from document text.
 *
 * Headlines are returned as newline-delimited values to keep node properties string-based.
 */
class HeadlinesExtractor(
    override val name: String = "headlines_extractor",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val sourceProperty: String = "page_content",
    private val targetProperty: String = "headlines",
    private val maxHeadlines: Int = 16,
) : Extractor(name = name, filterNodes = filterNodes) {
    override fun propertyName(node: Node): String = targetProperty

    override suspend fun extract(node: Node): String {
        val text = node.getProperty(sourceProperty).orEmpty()
        if (text.isBlank()) {
            return ""
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headings =
            lines
                .filter { line ->
                    line.startsWith("#") ||
                        line.endsWith(":") ||
                        (line.length in 4..80 && line == line.uppercase() && line.any { it.isLetter() })
                }.map { line -> line.trimStart('#').trim() }
                .distinct()
                .take(maxHeadlines)

        return headings.joinToString("\n")
    }
}

/**
 * Extractor that produces a deterministic dense vector from text and stores it as CSV.
 *
 * This is a lightweight stand-in for embedding-model-based extraction in parity tests.
 */
class EmbeddingExtractor(
    override val name: String = "embedding_extractor",
    override val filterNodes: (Node) -> Boolean = defaultFilter,
    private val sourceProperty: String = PropertyNames.SUMMARY_LLM_BASED,
    private val targetProperty: String = "summary_embedding",
    private val dimensions: Int = 16,
) : Extractor(name = name, filterNodes = filterNodes) {
    init {
        require(dimensions > 0) { "dimensions must be > 0" }
    }

    override fun propertyName(node: Node): String = targetProperty

    override suspend fun extract(node: Node): String {
        val text = node.getProperty(sourceProperty).orEmpty().lowercase()
        if (text.isBlank()) {
            return List(dimensions) { "0.0" }.joinToString(",")
        }

        val vector = DoubleArray(dimensions)
        text.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.forEach { token ->
            val bucket =
                token
                    .hashCode()
                    .toUInt()
                    .toInt()
                    .mod(dimensions)
            vector[bucket] += 1.0
        }
        val norm = kotlin.math.sqrt(vector.sumOf { value -> value * value })
        val normalized =
            if (norm == 0.0) {
                vector
            } else {
                DoubleArray(dimensions) { index -> vector[index] / norm }
            }

        return normalized.joinToString(",") { value -> "%.6f".format(java.util.Locale.US, value) }
    }
}
