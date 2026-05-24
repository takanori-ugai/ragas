package ragas.testset.transforms

import ragas.testset.graph.NodeType

/**
 * Heuristic default transform plan for document inputs.
 *
 * This mirrors Python's default transform branching by document length bands while
 * using Kotlin-native extractors/builders.
 */
fun defaultTransformsForDocuments(documents: List<String>): SequenceTransforms {
    require(documents.isNotEmpty()) { "documents cannot be empty" }

    val lengths = documents.map(::tokenCount)
    val total = lengths.size.toDouble()
    val shortRatio = lengths.count { it in 0..100 }.toDouble() / total
    val mediumRatio = lengths.count { it in 101..500 }.toDouble() / total
    val longRatio = lengths.count { it > 500 }.toDouble() / total

    val filterLongDocs: (ragas.testset.graph.Node) -> Boolean =
        { node -> node.type == NodeType.DOCUMENT && tokenCount(node.getProperty("page_content").orEmpty()) > 500 }
    val filterMediumDocs: (ragas.testset.graph.Node) -> Boolean =
        { node -> node.type == NodeType.DOCUMENT && tokenCount(node.getProperty("page_content").orEmpty()) > 100 }
    val filterChunks: (ragas.testset.graph.Node) -> Boolean = { node -> node.type == NodeType.CHUNK }

    val plan =
        when {
            longRatio >= 0.25 -> {
                listOf(
                    SingleTransform(HeadlinesExtractor(filterNodes = filterLongDocs)),
                    SingleTransform(HeadlineSplitter(filterNodes = filterLongDocs, minTokens = 500)),
                    SingleTransform(LlmBasedSummaryExtractor(filterNodes = filterLongDocs)),
                    Parallel(
                        listOf(
                            EmbeddingExtractor(
                                sourceProperty = PropertyNames.SUMMARY_LLM_BASED,
                                targetProperty = "summary_embedding",
                                filterNodes = filterLongDocs,
                            ),
                            EmbeddingsTopicExtractor(filterNodes = filterChunks),
                            RegexEntityExtractor(filterNodes = filterChunks),
                        ),
                    ),
                    Parallel(
                        listOf(
                            SummaryCosineSimilarityBuilder(
                                filterNodes = filterLongDocs,
                                threshold = 0.7,
                            ),
                            OverlapScoreBuilder(
                                filterNodes = filterChunks,
                                propertyName = PropertyNames.ENTITIES_REGEX,
                                threshold = 0.01,
                            ),
                        ),
                    ),
                )
            }

            mediumRatio >= 0.25 -> {
                listOf(
                    SingleTransform(LlmBasedSummaryExtractor(filterNodes = filterMediumDocs)),
                    Parallel(
                        listOf(
                            EmbeddingExtractor(
                                sourceProperty = PropertyNames.SUMMARY_LLM_BASED,
                                targetProperty = "summary_embedding",
                                filterNodes = filterMediumDocs,
                            ),
                            EmbeddingsTopicExtractor(filterNodes = filterMediumDocs),
                            RegexEntityExtractor(filterNodes = filterMediumDocs),
                        ),
                    ),
                    Parallel(
                        listOf(
                            SummaryCosineSimilarityBuilder(
                                filterNodes = filterMediumDocs,
                                threshold = 0.5,
                            ),
                            OverlapScoreBuilder(
                                filterNodes = filterMediumDocs,
                                propertyName = PropertyNames.ENTITIES_REGEX,
                                threshold = 0.01,
                            ),
                        ),
                    ),
                )
            }

            else -> {
                listOf(
                    SingleTransform(SentenceChunkSplitter(maxSentencesPerChunk = 1)),
                    Parallel(
                        listOf(
                            LlmBasedSummaryExtractor(filterNodes = filterChunks),
                            EmbeddingsTopicExtractor(filterNodes = filterChunks),
                            RegexEntityExtractor(filterNodes = filterChunks),
                        ),
                    ),
                    Parallel(
                        listOf(
                            AdjacentChunkRelationshipBuilder(),
                            SharedKeywordRelationshipBuilder(
                                filterNodes = filterChunks,
                                minSharedKeywords = 1,
                            ),
                        ),
                    ),
                )
            }
        }

    return SequenceTransforms(plan)
}

/**
 * Default transform plan for pre-chunked inputs.
 */
fun defaultTransformsForPrechunked(): SequenceTransforms {
    val filterChunks: (ragas.testset.graph.Node) -> Boolean = { node -> node.type == NodeType.CHUNK }
    return SequenceTransforms(
        listOf(
            SingleTransform(LlmBasedSummaryExtractor(filterNodes = filterChunks)),
            Parallel(
                listOf(
                    EmbeddingExtractor(
                        sourceProperty = PropertyNames.SUMMARY_LLM_BASED,
                        targetProperty = "summary_embedding",
                        filterNodes = filterChunks,
                    ),
                    EmbeddingsTopicExtractor(filterNodes = filterChunks),
                    RegexEntityExtractor(filterNodes = filterChunks),
                ),
            ),
            Parallel(
                listOf(
                    SummaryCosineSimilarityBuilder(
                        filterNodes = filterChunks,
                        threshold = 0.7,
                    ),
                    OverlapScoreBuilder(
                        filterNodes = filterChunks,
                        propertyName = PropertyNames.ENTITIES_REGEX,
                        threshold = 0.01,
                    ),
                ),
            ),
        ),
    )
}

private fun tokenCount(text: String): Int = text.split(Regex("\\s+")).count { token -> token.isNotBlank() }
