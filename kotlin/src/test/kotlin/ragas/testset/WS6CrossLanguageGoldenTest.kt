package ragas.testset

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ragas.model.SingleTurnSample
import ragas.testset.graph.NodeType
import ragas.testset.synthesizers.SamplingMode
import ragas.testset.synthesizers.SynthesisControls
import ragas.testset.synthesizers.TestsetGenerator
import ragas.testset.transforms.AdjacentChunkRelationshipBuilder
import ragas.testset.transforms.EmbeddingsTopicExtractor
import ragas.testset.transforms.LlmBasedSummaryExtractor
import ragas.testset.transforms.RegexEntityExtractor
import ragas.testset.transforms.SentenceChunkSplitter
import ragas.testset.transforms.SequenceTransforms
import ragas.testset.transforms.SharedKeywordRelationshipBuilder
import ragas.testset.transforms.SingleTransform
import kotlin.test.Test
import kotlin.test.assertTrue

class WS6CrossLanguageGoldenTest {
    @Test
    fun ws6CrossLanguageFixtureCalibratesShapePromptStyleAndGraphStats() =
        runBlocking {
            val fixture = WS6TestFixtures.readFixture("ws6_cross_language_golden_fixture.json").jsonObject
            val documents = fixture.getValue("documents").jsonArray.map { item -> item.jsonPrimitive.content }
            val config = fixture.getValue("config").jsonObject
            val pythonReference = fixture.getValue("python_reference").jsonObject
            val graphReference = fixture.getValue("graph_reference").jsonObject

            val controlsObject = config.getValue("synthesis_controls").jsonObject
            val samplingMode =
                when (controlsObject.getValue("sampling_mode").jsonPrimitive.content) {
                    "top_k" -> SamplingMode.TOP_K
                    "rank_biased" -> SamplingMode.RANK_BIASED
                    "temperature" -> SamplingMode.TEMPERATURE
                    else -> error("Unsupported sampling_mode in fixture.")
                }

            val generator = TestsetGenerator()
            val testset =
                generator.generateFromDocuments(
                    documents = documents,
                    testsetSize = config.getValue("testset_size").jsonPrimitive.int,
                    transforms =
                        SequenceTransforms(
                            listOf(
                                SingleTransform(LlmBasedSummaryExtractor()),
                                SingleTransform(RegexEntityExtractor()),
                                SingleTransform(EmbeddingsTopicExtractor()),
                                SingleTransform(
                                    SentenceChunkSplitter(
                                        maxSentencesPerChunk = config.getValue("max_sentences_per_chunk").jsonPrimitive.int,
                                    ),
                                ),
                                SingleTransform(AdjacentChunkRelationshipBuilder()),
                                SingleTransform(
                                    SharedKeywordRelationshipBuilder(
                                        minSharedKeywords = config.getValue("min_shared_keywords").jsonPrimitive.int,
                                    ),
                                ),
                            ),
                        ),
                    synthesisControls =
                        SynthesisControls(
                            seed = controlsObject.getValue("seed").jsonPrimitive.int,
                            samplingMode = samplingMode,
                            multiHopCount = controlsObject.getValue("multi_hop_count").jsonPrimitive.int,
                        ),
                )

            val requiredFields =
                pythonReference
                    .getValue("sample_shape_required_fields")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }
                    .toSet()
            val allowedStyles =
                pythonReference
                    .getValue("allowed_query_style_names")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }
                    .toSet()
            val allowedLengths =
                pythonReference
                    .getValue("allowed_query_length_names")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }
                    .toSet()
            val singleHopMarkers =
                pythonReference
                    .getValue("single_hop_prompt_markers")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }
            val multiHopMarkers =
                pythonReference
                    .getValue("multi_hop_prompt_markers")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }

            testset.samples.forEach { sample ->
                val row = sample.evalSample.toMap().keys
                assertTrue(requiredFields.all { field -> field in row }, "Missing required shape fields in sample map.")

                val eval = sample.evalSample as SingleTurnSample
                val question = eval.userInput.orEmpty()
                val style = eval.queryStyle.orEmpty()
                val length = eval.queryLength.orEmpty()
                val persona = eval.personaName.orEmpty()
                assertTrue(style in allowedStyles, "query_style '$style' not in Python-style enum names.")
                assertTrue(length in allowedLengths, "query_length '$length' not in Python-style enum names.")
                assertTrue(persona.isNotBlank(), "persona_name should be non-blank for Python-shape parity.")

                if (sample.synthesizerName.startsWith("single_hop_")) {
                    assertTrue(singleHopMarkers.any { marker -> question.contains(marker) })
                }
                if (sample.synthesizerName.startsWith("multi_hop_")) {
                    assertTrue(multiHopMarkers.any { marker -> question.contains(marker) })
                    assertTrue((eval.referenceContexts?.size ?: 0) >= 2, "multi-hop should include multiple contexts.")
                }
            }

            val graph = generator.knowledgeGraph
            val chunkCount = graph.nodes.count { node -> node.type == NodeType.CHUNK }
            val childEdges = graph.relationships.count { rel -> rel.type == "child" }
            val nextEdges = graph.relationships.count { rel -> rel.type == "next" }
            val overlapEdges = graph.relationships.count { rel -> rel.type == "semantic_overlap" }
            val totalPairs = chunkCount * (chunkCount - 1) / 2
            val overlapDensity = if (totalPairs == 0) 0.0 else overlapEdges.toDouble() / totalPairs.toDouble()

            assertTrue(chunkCount >= graphReference.getValue("chunk_count_min").jsonPrimitive.int)
            assertTrue(chunkCount <= graphReference.getValue("chunk_count_max").jsonPrimitive.int)
            assertTrue(childEdges >= graphReference.getValue("child_edges_min").jsonPrimitive.int)
            assertTrue(nextEdges >= graphReference.getValue("next_edges_min").jsonPrimitive.int)
            assertTrue(overlapDensity >= graphReference.getValue("overlap_density_min").jsonPrimitive.double)
            assertTrue(overlapDensity <= graphReference.getValue("overlap_density_max").jsonPrimitive.double)
        }
}
