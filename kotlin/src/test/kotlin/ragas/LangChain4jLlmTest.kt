package ragas

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.runBlocking
import ragas.llms.LangChain4jLlm
import kotlin.test.Test
import kotlin.test.assertEquals

class LangChain4jLlmTest {
    @Test
    fun generateTextForwardsTemperatureAndStopToChatRequest() =
        runBlocking {
            val model = CapturingChatModel()
            val llm = LangChain4jLlm(model)

            llm.generateText(
                prompt = "hello",
                n = 1,
                temperature = 0.7,
                stop = listOf("STOP"),
            )

            val request = model.lastRequest
            require(request != null) { "Expected ChatRequest to be captured." }
            assertEquals(0.7, request.temperature())
            assertEquals(listOf("STOP"), request.stopSequences())
        }

    @Test
    fun generateNumericValueParsesDoubleResponse() =
        runBlocking {
            val model = ConstantResponseChatModel("{\"value\": 0.85}")
            val llm = LangChain4jLlm(model)

            val result = llm.generateNumericValue("score this")
            assertEquals(0.85, result)
        }

    @Test
    fun generateRankingItemsParsesListResponse() =
        runBlocking {
            val model = ConstantResponseChatModel("{\"items\": [\"item1\", \"item2\"]}")
            val llm = LangChain4jLlm(model)

            val result = llm.generateRankingItems("rank these")
            assertEquals(listOf("item1", "item2"), result)
        }
}

private class CapturingChatModel : ChatModel {
    var lastRequest: ChatRequest? = null

    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        lastRequest = chatRequest
        return ChatResponse
            .builder()
            .aiMessage(AiMessage.from("ok"))
            .build()
    }
}

private class ConstantResponseChatModel(
    private val response: String,
) : ChatModel {
    override fun doChat(chatRequest: ChatRequest): ChatResponse =
        ChatResponse
            .builder()
            .aiMessage(AiMessage.from(response))
            .build()
}
