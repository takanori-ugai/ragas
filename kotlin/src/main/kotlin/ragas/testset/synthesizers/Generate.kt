package ragas.testset.synthesizers

import ragas.model.SingleTurnSample
import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import ragas.testset.transforms.SequenceTransforms
import ragas.testset.transforms.Transforms
import ragas.testset.transforms.applyTransforms

class TestsetGenerator(
    var knowledgeGraph: KnowledgeGraph = KnowledgeGraph(),
) {
    suspend fun generateFromDocuments(
        documents: List<String>,
        testsetSize: Int,
        transforms: Transforms? = null,
    ): Testset {
        val nodes =
            documents.map { text ->
                Node(
                    type = NodeType.DOCUMENT,
                    properties = mutableMapOf("page_content" to text),
                )
            }

        val kg = KnowledgeGraph(nodes = nodes.toMutableList())
        if (transforms != null) {
            applyTransforms(kg, transforms)
        }
        knowledgeGraph = kg

        val evalSamples =
            kg.nodes
                .take(testsetSize)
                .map { node ->
                    val content = node.getProperty("page_content").orEmpty()
                    val question = "What is the key point of: ${content.take(40)}?"
                    val answer = content.take(120)
                    TestsetSample(
                        evalSample =
                            SingleTurnSample(
                                userInput = question,
                                response = answer,
                                reference = answer,
                                retrievedContexts = listOf(content),
                                referenceContexts = listOf(content),
                            ),
                        synthesizerName = "single_hop_stub",
                    )
                }

        return Testset(samples = evalSamples)
    }
}

fun emptyTransforms(): Transforms = SequenceTransforms(emptyList())
