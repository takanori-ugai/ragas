package ragas.llms

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ragas.runtime.RunConfig

class LangChain4jLlm(
    private val model: ChatModel,
    override var runConfig: RunConfig = RunConfig(),
) : BaseRagasLlm {
    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult =
        coroutineScope {
            require(n > 0) { "n must be greater than zero." }

            val jobs =
                (0 until n).map {
                    async {
                        withTimeout(runConfig.timeoutSeconds * 1_000) {
                            withContext(Dispatchers.IO) {
                                val requestBuilder =
                                    ChatRequest
                                        .builder()
                                        .messages(UserMessage.from(prompt))
                                if (temperature != null) {
                                    requestBuilder.temperature(temperature)
                                }
                                if (!stop.isNullOrEmpty()) {
                                    requestBuilder.stopSequences(stop)
                                }
                                val response = model.chat(requestBuilder.build())
                                val text = response.aiMessage().text()
                                val finish = response.finishReason()?.name
                                LlmGeneration(
                                    text = applyStop(text, stop),
                                    finishReason = finish,
                                )
                            }
                        }
                    }
                }

            LlmResult(generations = jobs.awaitAll())
        }

    private fun applyStop(
        text: String,
        stop: List<String>?,
    ): String {
        if (stop.isNullOrEmpty()) {
            return text
        }

        val firstMatchIndex =
            stop
                .map { marker -> text.indexOf(marker) }
                .filter { index -> index >= 0 }
                .minOrNull()

        return if (firstMatchIndex == null) text else text.substring(0, firstMatchIndex)
    }
}
