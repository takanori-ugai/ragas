package ragas.testset

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WS6EdgeCaseFixturesTest {
    @Test
    fun sparseOverlapFixtureConformance() =
        runBlocking {
            val fixture = readFixture("ws6_sparse_overlap_fixture.json")
            val run = executeFixture(fixture)
            val expected = fixture.jsonObject.getValue("expected").jsonObject

            assertTrue(run.chunkCount >= expected.getValue("min_chunk_nodes").jsonPrimitive.int)
            assertTrue(run.overlapRelationshipCount <= expected.getValue("max_overlap_relationships").jsonPrimitive.int)
            assertTrue(run.nextRelationshipCount >= expected.getValue("min_next_relationships").jsonPrimitive.int)

            val requiredPrefixes =
                expected
                    .getValue("required_synthesizer_prefixes")
                    .jsonArray
                    .map { item -> item.jsonPrimitive.content }
            val synthesizerSet =
                run.testset.samples
                    .map { sample -> sample.synthesizerName }
                    .toSet()
            assertTrue(requiredPrefixes.all { prefix -> synthesizerSet.any { name -> name.startsWith(prefix) } })

            val minResponseChars = expected.getValue("min_response_characters").jsonPrimitive.int
            run.testset.samples.forEach { sample ->
                val response = (sample.evalSample as SingleTurnSample).response.orEmpty()
                assertTrue(response.length >= minResponseChars)
            }
        }

    @Test
    fun longDocumentFixtureConformance() =
        runBlocking {
            val fixture = readFixture("ws6_long_document_fixture.json")
            val run = executeFixture(fixture)
            val expected = fixture.jsonObject.getValue("expected").jsonObject

            assertTrue(run.chunkCount >= expected.getValue("min_chunk_nodes").jsonPrimitive.int)
            assertTrue(run.chunkCount <= expected.getValue("max_chunk_nodes").jsonPrimitive.int)
            assertTrue(run.childRelationshipCount >= expected.getValue("min_child_relationships").jsonPrimitive.int)
            assertTrue(run.nextRelationshipCount >= expected.getValue("min_next_relationships").jsonPrimitive.int)

            val multiHopCount = run.testset.samples.count { sample -> sample.synthesizerName.startsWith("multi_hop_") }
            assertTrue(multiHopCount <= expected.getValue("max_multi_hop_samples").jsonPrimitive.int)

            val avgChunkChars =
                run.graph.nodes
                    .filter { node -> node.type == NodeType.CHUNK }
                    .map { node ->
                        node
                            .getProperty("page_content")
                            .orEmpty()
                            .length
                            .toDouble()
                    }.average()
            assertTrue(avgChunkChars >= expected.getValue("min_avg_chunk_characters").jsonPrimitive.double)
        }

    @Test
    fun relationshipDensityFixtureConformance() =
        runBlocking {
            val fixture = readFixture("ws6_relationship_density_fixture.json")
            val run = executeFixture(fixture)
            val expected = fixture.jsonObject.getValue("expected").jsonObject

            assertTrue(run.chunkCount >= expected.getValue("min_chunk_nodes").jsonPrimitive.int)
            assertTrue(run.overlapRelationshipCount >= expected.getValue("min_overlap_relationships").jsonPrimitive.int)

            val totalPairs = run.chunkCount * (run.chunkCount - 1) / 2
            val density =
                if (totalPairs == 0) {
                    0.0
                } else {
                    run.overlapRelationshipCount.toDouble() / totalPairs.toDouble()
                }
            assertTrue(density >= expected.getValue("semantic_overlap_density_min").jsonPrimitive.double)
            assertTrue(density <= expected.getValue("semantic_overlap_density_max").jsonPrimitive.double)

            val multiHopCount = run.testset.samples.count { sample -> sample.synthesizerName.startsWith("multi_hop_") }
            assertTrue(multiHopCount >= expected.getValue("min_multi_hop_samples").jsonPrimitive.int)
        }

    private data class FixtureRun(
        val testset: ragas.testset.synthesizers.Testset,
        val graph: ragas.testset.graph.KnowledgeGraph,
        val chunkCount: Int,
        val childRelationshipCount: Int,
        val nextRelationshipCount: Int,
        val overlapRelationshipCount: Int,
    )

    private suspend fun executeFixture(fixture: kotlinx.serialization.json.JsonElement): FixtureRun {
        val root = fixture.jsonObject
        val documents = root.getValue("documents").jsonArray.map { item -> item.jsonPrimitive.content }
        val config = root.getValue("config").jsonObject

        val testsetSize = config.getValue("testset_size").jsonPrimitive.int
        val maxSentencesPerChunk = config.getValue("max_sentences_per_chunk").jsonPrimitive.int
        val minSharedKeywords = config.getValue("min_shared_keywords").jsonPrimitive.int
        val controls = synthesisControlsFrom(config.jsonObject.getValue("synthesis_controls").jsonObject)

        val generator = TestsetGenerator()
        val testset =
            generator.generateFromDocuments(
                documents = documents,
                testsetSize = testsetSize,
                transforms =
                    SequenceTransforms(
                        listOf(
                            SingleTransform(LlmBasedSummaryExtractor()),
                            SingleTransform(RegexEntityExtractor()),
                            SingleTransform(EmbeddingsTopicExtractor()),
                            SingleTransform(SentenceChunkSplitter(maxSentencesPerChunk = maxSentencesPerChunk)),
                            SingleTransform(AdjacentChunkRelationshipBuilder()),
                            SingleTransform(
                                SharedKeywordRelationshipBuilder(
                                    minSharedKeywords = minSharedKeywords,
                                ),
                            ),
                        ),
                    ),
                synthesisControls = controls,
            )

        val graph = generator.knowledgeGraph
        return FixtureRun(
            testset = testset,
            graph = graph,
            chunkCount = graph.nodes.count { node -> node.type == NodeType.CHUNK },
            childRelationshipCount = graph.relationships.count { rel -> rel.type == "child" },
            nextRelationshipCount = graph.relationships.count { rel -> rel.type == "next" },
            overlapRelationshipCount = graph.relationships.count { rel -> rel.type == "semantic_overlap" },
        )
    }

    private fun synthesisControlsFrom(obj: kotlinx.serialization.json.JsonObject): SynthesisControls {
        val modeString = obj["sampling_mode"]?.jsonPrimitive?.content
        val mode =
            when (modeString) {
                "top_k" -> SamplingMode.TOP_K
                "rank_biased" -> SamplingMode.RANK_BIASED
                "temperature" -> SamplingMode.TEMPERATURE
                null -> null
                else -> error("Unknown sampling_mode in fixture: $modeString")
            }

        return SynthesisControls(
            seed = obj["seed"]?.jsonPrimitive?.int ?: 42,
            samplingMode = mode,
            temperature = obj["temperature"]?.jsonPrimitive?.double ?: 0.8,
            singleHopCount = obj["single_hop_count"]?.jsonPrimitive?.int,
            multiHopCount = obj["multi_hop_count"]?.jsonPrimitive?.int,
            enforceDocumentDiversity = obj["enforce_document_diversity"]?.jsonPrimitive?.content?.toBoolean() ?: true,
            maxSingleHopPerDocument = obj["max_single_hop_per_document"]?.jsonPrimitive?.int ?: 2,
            useRankBiasedSampling = obj["use_rank_biased_sampling"]?.jsonPrimitive?.content?.toBoolean() ?: true,
        )
    }

    private fun readFixture(name: String) =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/testset/$name")) {
                "Fixture not found on classpath: fixtures/testset/$name"
            }.bufferedReader().use { it.readText() },
        )
}
