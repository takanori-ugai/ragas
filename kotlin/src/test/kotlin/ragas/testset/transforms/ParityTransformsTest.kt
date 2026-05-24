package ragas.testset.transforms

import kotlinx.coroutines.runBlocking
import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParityTransformsTest {
    @Test
    fun headlineSplitterBuildsChildAndNextRelationships() =
        runBlocking {
            val splitter = HeadlineSplitter(minTokens = 2, maxTokens = 20)
            val node =
                Node(
                    id = "doc-1",
                    type = NodeType.DOCUMENT,
                    properties =
                        mutableMapOf(
                            "page_content" to
                                """
                                Introduction:
                                Kotlin is a language for modern development.
                                Usage:
                                Teams use Kotlin for backend and Android.
                                """.trimIndent(),
                            "headlines" to "Introduction:\nUsage:",
                        ),
                )

            val (chunks, relationships) = splitter.split(node)

            assertTrue(chunks.size >= 2)
            assertEquals(chunks.size, relationships.count { it.type == "child" })
            assertEquals(chunks.size - 1, relationships.count { it.type == "next" })
        }

    @Test
    fun cosineSimilarityBuilderCreatesRelationshipWhenAboveThreshold() =
        runBlocking {
            val n1 = Node(id = "a", type = NodeType.CHUNK, properties = mutableMapOf("embedding" to "1,0,0"))
            val n2 = Node(id = "b", type = NodeType.CHUNK, properties = mutableMapOf("embedding" to "0.95,0.01,0"))
            val n3 = Node(id = "c", type = NodeType.CHUNK, properties = mutableMapOf("embedding" to "0,1,0"))
            val kg = KnowledgeGraph(nodes = mutableListOf(n1, n2, n3))

            val builder = CosineSimilarityBuilder(threshold = 0.9)
            val built = builder.build(kg, kg)

            assertTrue(built.any { rel -> rel.sourceId == "a" && rel.targetId == "b" })
            assertTrue(built.none { rel -> rel.sourceId == "a" && rel.targetId == "c" })
        }

    @Test
    fun jaccardAndOverlapBuildersCreateRelationships() =
        runBlocking {
            val n1 =
                Node(id = "a", type = NodeType.CHUNK, properties = mutableMapOf(PropertyNames.ENTITIES_REGEX to "Kotlin, JVM, Android"))
            val n2 =
                Node(
                    id = "b",
                    type = NodeType.CHUNK,
                    properties =
                        mutableMapOf(PropertyNames.ENTITIES_REGEX to "Kotlin, JVM, Coroutines"),
                )
            val kg = KnowledgeGraph(nodes = mutableListOf(n1, n2))

            val jaccard = JaccardSimilarityBuilder(threshold = 0.4)
            val overlap = OverlapScoreBuilder(threshold = 0.01, distanceThreshold = 0.9)

            val jaccardBuilt = jaccard.build(kg, kg)
            val overlapBuilt = overlap.build(kg, kg)

            assertEquals(1, jaccardBuilt.size)
            assertEquals(1, overlapBuilt.size)
        }

    @Test
    fun defaultTransformsForDocumentsRejectsVeryShortInputs() {
        assertFailsWith<IllegalStateException> {
            defaultTransformsForDocuments(listOf("short doc", "another short doc"))
        }
    }
}
