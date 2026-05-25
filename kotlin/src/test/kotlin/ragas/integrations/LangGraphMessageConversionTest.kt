package ragas.integrations

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ragas.model.AiMessage
import ragas.model.HumanMessage
import ragas.model.ToolMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LangGraphMessageConversionTest {
    @Test
    fun convertToRagasMessagesConvertsHumanAiAndToolAndSkipsSystem() {
        val converted =
            LangGraphIntegration.convertToRagasMessages(
                listOf(
                    mapOf("role" to "system", "content" to "You are a helpful system."),
                    mapOf("type" to "human", "content" to "Find current gold price"),
                    mapOf(
                        "role" to "assistant",
                        "content" to "Checking now.",
                        "tool_calls" to
                            listOf(
                                mapOf(
                                    "function" to
                                        mapOf(
                                            "name" to "get_metal_price",
                                            "arguments" to """{"metal_name":"gold"}""",
                                        ),
                                ),
                            ),
                    ),
                    mapOf("role" to "tool", "content" to "3200.50"),
                ),
            )

        assertEquals(3, converted.size)
        assertIs<HumanMessage>(converted[0])
        assertIs<AiMessage>(converted[1])
        assertIs<ToolMessage>(converted[2])
        assertEquals("Find current gold price", converted[0].content)
        assertEquals("Checking now.", converted[1].content)
        assertEquals("3200.50", converted[2].content)

        val ai = converted[1] as AiMessage
        assertNotNull(ai.toolCalls)
        assertEquals(1, ai.toolCalls.size)
        assertEquals("get_metal_price", ai.toolCalls[0].name)
        val metalName = ai.toolCalls[0].args["metal_name"] as? JsonPrimitive
        assertEquals("gold", metalName?.content)
    }

    @Test
    fun convertToRagasMessagesIncludesMetadataOnlyWhenRequested() {
        val messages = listOf(mapOf("role" to "human", "content" to "hello", "id" to "m-1", "channel" to "chat"))

        val withoutMetadata = LangGraphIntegration.convertToRagasMessages(messages, includeMetadata = false)
        val withMetadata = LangGraphIntegration.convertToRagasMessages(messages, includeMetadata = true)

        assertNull((withoutMetadata.single() as HumanMessage).metadata)
        val metadata = (withMetadata.single() as HumanMessage).metadata
        assertNotNull(metadata)
        assertEquals("m-1", (metadata["id"] as JsonPrimitive).content)
        assertEquals("chat", (metadata["channel"] as JsonPrimitive).content)
    }

    @Test
    fun convertToRagasMessagesFailsOnInvalidToolCallJson() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                LangGraphIntegration.convertToRagasMessages(
                    listOf(
                        mapOf(
                            "role" to "assistant",
                            "content" to "test",
                            "tool_calls" to
                                listOf(
                                    mapOf(
                                        "function" to mapOf("name" to "search", "arguments" to "invalid json"),
                                    ),
                                ),
                        ),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("invalid JSON arguments"))
    }

    @Test
    fun convertToRagasMessagesFailsOnUnsupportedRole() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                LangGraphIntegration.convertToRagasMessages(
                    listOf(mapOf("role" to "developer", "content" to "x")),
                )
            }

        assertTrue(error.message.orEmpty().contains("Unsupported message role/type"))
    }

    @Test
    fun convertToRagasMessagesFailsOnInvalidContentType() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                LangGraphIntegration.convertToRagasMessages(
                    listOf(mapOf("type" to "human", "content" to listOf("invalid"))),
                )
            }

        assertTrue(error.message.orEmpty().contains("requires string 'content'"))
    }

    @Test
    fun convertToRagasMessagesMetadataExcludesDuplicatedToolCalls() {
        val converted =
            LangGraphIntegration.convertToRagasMessages(
                listOf(
                    mapOf(
                        "role" to "assistant",
                        "content" to "Checking.",
                        "tool_calls" to
                            listOf(
                                mapOf(
                                    "function" to
                                        mapOf(
                                            "name" to "search",
                                            "arguments" to """{"query":"weather"}""",
                                        ),
                                ),
                            ),
                        "additional_kwargs" to
                            mapOf(
                                "tool_calls" to listOf(mapOf("name" to "duplicated")),
                                "trace_id" to "t-1",
                            ),
                        "id" to "m-2",
                    ),
                ),
                includeMetadata = true,
            )

        val ai = converted.single() as AiMessage
        val metadata = ai.metadata
        assertNotNull(metadata)
        assertNull(metadata["tool_calls"])
        assertEquals("m-2", (metadata["id"] as JsonPrimitive).content)

        val additionalKwargs = metadata["additional_kwargs"] as JsonObject
        assertEquals("t-1", (additionalKwargs["trace_id"] as JsonPrimitive).content)
        assertNull(additionalKwargs["tool_calls"])
    }

    @Test
    fun toMultiTurnSampleBuildsSampleForAgentMetrics() {
        val sample =
            LangGraphIntegration.toMultiTurnSample(
                messages =
                    listOf(
                        mapOf("role" to "human", "content" to "Book a table for two"),
                        mapOf("role" to "assistant", "content" to "Done."),
                    ),
                reference = "Reservation complete",
                referenceTopics = listOf("reservation"),
            )

        assertEquals(2, sample.userInput.size)
        assertIs<HumanMessage>(sample.userInput[0])
        assertIs<AiMessage>(sample.userInput[1])
        assertEquals("Reservation complete", sample.reference)
        assertEquals(listOf("reservation"), sample.referenceTopics)
    }
}
