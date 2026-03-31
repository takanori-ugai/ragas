package ragas

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import ragas.testset.graph.Relationship
import ragas.testset.graph.getChildNodes
import ragas.testset.graph.getParentNodes
import ragas.testset.synthesizers.TestsetGenerator
import ragas.testset.transforms.Extractor
import ragas.testset.transforms.SequenceTransforms
import ragas.testset.transforms.SingleTransform
import ragas.testset.transforms.applyTransforms

class TestsetScaffoldTest {
    @Test
    fun knowledgeGraphSaveLoadAndQueriesWork() {
        val parent = Node(type = NodeType.DOCUMENT, properties = mutableMapOf("page_content" to "p"))
        val child = Node(type = NodeType.CHUNK, properties = mutableMapOf("page_content" to "c"))
        val graph = KnowledgeGraph(nodes = mutableListOf(parent, child))
        graph.add(Relationship(type = "child", sourceId = parent.id, targetId = child.id))

        val dir = createTempDirectory("ragas-kg").toFile()
        val path = dir.resolve("kg.json").absolutePath
        graph.save(path)

        val loaded = KnowledgeGraph.load(path)
        assertEquals(2, loaded.nodes.size)
        assertEquals(1, loaded.relationships.size)

        val loadedParent = loaded.nodes.first { it.id == parent.id }
        val loadedChild = loaded.nodes.first { it.id == child.id }
        assertEquals(listOf(loadedChild.id), getChildNodes(loadedParent, loaded).map { it.id })
        assertEquals(listOf(loadedParent.id), getParentNodes(loadedChild, loaded).map { it.id })
    }

    @Test
    fun applyTransformsExecutesExtractorPlan() = runBlocking {
        val n1 = Node(type = NodeType.DOCUMENT, properties = mutableMapOf("page_content" to "alpha"))
        val n2 = Node(type = NodeType.DOCUMENT, properties = mutableMapOf("page_content" to "beta"))
        val graph = KnowledgeGraph(nodes = mutableListOf(n1, n2))

        val extractor =
            object : Extractor(name = "length_extractor") {
                override suspend fun extract(node: Node): Pair<String, String> {
                    val text = node.getProperty("page_content").orEmpty()
                    return "length" to text.length.toString()
                }
            }

        applyTransforms(
            graph,
            SequenceTransforms(listOf(SingleTransform(extractor))),
        )

        assertEquals("5", n1.getProperty("length"))
        assertEquals("4", n2.getProperty("length"))
    }

    @Test
    fun testsetGeneratorCreatesSingleTurnSamples() = runBlocking {
        val generator = TestsetGenerator()
        val testset =
            generator.generateFromDocuments(
                documents = listOf("Doc one about Kotlin.", "Doc two about RAGAS."),
                testsetSize = 2,
            )

        assertEquals(2, testset.samples.size)
        val eval = testset.toEvaluationDataset()
        assertEquals(2, eval.samples.size)
        assertTrue(generator.knowledgeGraph.nodes.isNotEmpty())
    }
}
