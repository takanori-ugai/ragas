package ragas.llms

import ragas.prompt.PromptContentPart

/**
 * Optional extension for LLMs that accept multimodal content parts.
 */
interface MultiModalRagasLlm {
    /**
     * Generates output from multimodal prompt content parts.
     *
     * @param content Parameter `content`.
     * @param n Number of generations requested.
     * @param temperature Sampling temperature.
     * @param stop Stop token list.
     */
    suspend fun generateContent(
        content: List<PromptContentPart>,
        n: Int = 1,
        temperature: Double? = 0.01,
        stop: List<String>? = null,
    ): LlmResult
}
