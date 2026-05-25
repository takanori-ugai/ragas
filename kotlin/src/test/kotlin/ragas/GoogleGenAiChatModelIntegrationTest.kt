package ragas

import dev.langchain4j.model.google.genai.GoogleGenAiChatModel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class GoogleGenAiChatModelIntegrationTest {
    @Test
    fun generateTextWithGemma4_31bIt() =
        runBlocking {
            val apiKey =
                System
                    .getenv("GEMINI_API_KEY")
                    ?.takeIf { it.isNotBlank() }
                    ?: System.getenv("GOOGLE_API_KEY").orEmpty()

            assumeTrue(
                "Skipping live Google GenAI test. Set GEMINI_API_KEY (or GOOGLE_API_KEY).",
                apiKey.isNotBlank(),
            )

            val model =
                GoogleGenAiChatModel
                    .builder()
                    .apiKey(apiKey)
                    .modelName("gemma-4-31b-it")
                    .temperature(0.0)
                    .build()
            val llm =
                LangChain4jLlm(
                    model = model,
                    runConfig = RunConfig(timeoutSeconds = 120),
                )

            val result =
                llm.generateText(
                    prompt = "Respond with exactly: OK",
                    n = 1,
                )

            val text =
                result.generations
                    .firstOrNull()
                    ?.text
                    .orEmpty()
                    .trim()
            assertTrue(text.isNotBlank(), "Expected non-empty response from GoogleGenAiChatModel(gemma-4-31b-it).")
        }
}
