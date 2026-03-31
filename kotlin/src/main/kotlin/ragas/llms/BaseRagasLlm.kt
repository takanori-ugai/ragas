package ragas.llms

import ragas.runtime.RunConfig

data class LlmGeneration(
    val text: String,
    val finishReason: String? = null,
)

data class LlmResult(
    val generations: List<LlmGeneration>,
)

interface BaseRagasLlm {
    var runConfig: RunConfig

    suspend fun generateText(
        prompt: String,
        n: Int = 1,
        temperature: Double? = 0.01,
        stop: List<String>? = null,
    ): LlmResult

    fun isFinished(result: LlmResult): Boolean =
        result.generations.all { generation ->
            generation.finishReason == null ||
                generation.finishReason.equals("STOP", ignoreCase = true) ||
                generation.finishReason.equals("LENGTH", ignoreCase = true)
        }
}
