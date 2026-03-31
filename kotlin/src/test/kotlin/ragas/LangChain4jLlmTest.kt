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
