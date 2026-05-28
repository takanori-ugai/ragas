package ragas.examples.workflow

import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

data class WorkflowResponse(
    val category: String,
    val responseTemplate: String,
)

private const val DEFAULT_MODEL_NAME = "gpt-5.4-mini"

/**
 * Minimal workflow app for tutorial usage.
 */
fun main() =
    runBlocking {
        val email = "Hi, I'm getting error code XYZ-123 when using version 2.1.4 of your software. Please help!"
        val workflowClient = WorkflowClient(createOpenAiLlm())
        val result = workflowClient.processEmail(email)
        println("Input email: $email")
        println("Category: ${result.category}")
        println("Response: ${result.responseTemplate}")
    }

internal fun createOpenAiLlm(modelName: String = DEFAULT_MODEL_NAME): BaseRagasLlm {
    val apiKey =
        System.getenv("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY is required for ragas.examples.workflow")
    val chatModel =
        OpenAiChatModel
            .builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.0)
            .build()
    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}

internal class WorkflowClient(
    private val llm: BaseRagasLlm,
) {
    suspend fun processEmail(email: String): WorkflowResponse {
        val category = inferCategory(email)
        val extractedFacts = extractFacts(email)
        val responseTemplate = generateResponseTemplate(email, category, extractedFacts)
        return WorkflowResponse(category = category, responseTemplate = responseTemplate)
    }

    private fun inferCategory(email: String): String {
        val normalized = email.lowercase()
        return when {
            listOf("invoice", "billing", "charge", "dispute").any { token -> token in normalized } -> "Billing"
            listOf("error", "version", "bug", "exception").any { token -> token in normalized } -> "Bug Report"
            else -> "General"
        }
    }

    private fun extractFacts(email: String): String {
        val version = Regex("\\b\\d+\\.\\d+(?:\\.\\d+)?\\b").find(email)?.value
        val errorCode = Regex("\\b[A-Z]{2,}-\\d{2,}\\b").find(email)?.value
        val invoice = Regex("\\bINV-\\d{4}-\\d{3,}\\b").find(email)?.value
        val amount =
            Regex("\\b\\d+(?:\\.\\d+)?\\b")
                .findAll(email)
                .map { it.value }
                .toList()
                .firstOrNull { "." in it }
        return buildString {
            if (version != null) append("product_version=$version; ")
            if (errorCode != null) append("error_code=$errorCode; ")
            if (invoice != null) append("invoice_number=$invoice; ")
            if (amount != null) append("amount=$amount; ")
        }.trim().ifBlank { "no_structured_facts" }
    }

    private suspend fun generateResponseTemplate(
        email: String,
        category: String,
        extractedFacts: String,
    ): String {
        val prompt =
            """
            You are a support workflow responder.
            Category: $category
            Extracted facts: $extractedFacts
            User email: $email
            
            Write a concise support response template that acknowledges the issue and next steps.
            Keep it under 60 words.
            """.trimIndent()
        return llm
            .generateText(prompt)
            .generations
            .firstOrNull()
            ?.text
            .orEmpty()
            .trim()
    }
}
