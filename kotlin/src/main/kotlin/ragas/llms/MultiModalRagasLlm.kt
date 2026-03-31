package ragas.llms

import ragas.prompt.PromptContentPart

interface MultiModalRagasLlm {
    suspend fun generateContent(
        content: List<PromptContentPart>,
        n: Int = 1,
        temperature: Double? = 0.01,
        stop: List<String>? = null,
    ): LlmResult
}
