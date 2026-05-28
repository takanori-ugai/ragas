package ragas.examples.prompteval

import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

internal const val DEFAULT_MODEL_NAME = "gpt-5.4-mini"

/**
 * Minimal prompt app for tutorial usage.
 */
fun main() =
    runBlocking {
        val input = "The movie was fantastic and I loved every moment of it!"
        val sentiment = runPrompt(input)
        println("Input: $input")
        println("Predicted sentiment: $sentiment")
    }

internal fun createOpenAiLlm(modelName: String = DEFAULT_MODEL_NAME): BaseRagasLlm {
    val apiKey =
        System.getenv("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY is required for ragas.examples.prompteval")
    val chatModel =
        OpenAiChatModel
            .builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.0)
            .build()
    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}

internal suspend fun runPrompt(
    text: String,
    modelName: String = DEFAULT_MODEL_NAME,
): String {
    val llm = createOpenAiLlm(modelName)
    val prompt =
        """
        You are a sentiment classifier.
        Classify the movie review as either "positive" or "negative".
        Return exactly one word: positive or negative.

        Review: $text
        """.trimIndent()
    val raw =
        llm
            .generateText(prompt)
            .generations
            .firstOrNull()
            ?.text
            .orEmpty()
            .trim()
            .lowercase()
    return when {
        "negative" in raw -> "negative"
        else -> "positive"
    }
}
