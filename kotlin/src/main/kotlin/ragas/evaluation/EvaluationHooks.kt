package ragas.evaluation

import ragas.llms.BaseRagasLlm
import ragas.llms.LlmResult
import ragas.llms.MultiModalRagasLlm
import ragas.llms.StructuredOutputRagasLlm
import ragas.model.EvaluationResult
import ragas.prompt.PromptContentPart
import ragas.runtime.Executor

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
) {
    val totalTokens: Int = promptTokens + completionTokens
}

data class CostEstimate(
    val amount: Double,
    val currency: String = "USD",
)

fun interface EvaluationCallback {
    fun onEvent(event: EvaluationEvent)
}

sealed interface EvaluationEvent {
    data class RunStarted(
        val sampleCount: Int,
        val metricNames: List<String>,
    ) : EvaluationEvent

    data class ExecutorReady(
        val executor: Executor,
    ) : EvaluationEvent

    data class MetricComputed(
        val rowIndex: Int,
        val metricName: String,
        val value: Any?,
    ) : EvaluationEvent

    data class RowCompleted(
        val rowIndex: Int,
        val scores: Map<String, Any?>,
    ) : EvaluationEvent

    data class TokenUsageComputed(
        val usage: TokenUsage,
    ) : EvaluationEvent

    data class CostComputed(
        val cost: CostEstimate,
    ) : EvaluationEvent

    data class RunCompleted(
        val result: EvaluationResult,
    ) : EvaluationEvent

    data class RunFailed(
        val error: Throwable,
    ) : EvaluationEvent
}

typealias TokenUsageParser = (prompt: String, result: LlmResult) -> TokenUsage?
typealias CostParser = (usage: TokenUsage) -> CostEstimate?

internal class TrackingRagasLlm(
    private val delegate: BaseRagasLlm,
    private val tokenUsageParser: TokenUsageParser,
    private val onUsage: (TokenUsage) -> Unit,
) : BaseRagasLlm,
    StructuredOutputRagasLlm,
    MultiModalRagasLlm {
    override var runConfig = delegate.runConfig
        set(value) {
            field = value
            delegate.runConfig = value
        }

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        val result = delegate.generateText(prompt, n, temperature, stop)
        onUsage(tokenUsageParser(prompt, result) ?: heuristicTokenUsage(prompt, result))
        return result
    }

    override suspend fun generateNumericValue(prompt: String): Double? {
        val structured = delegate as? StructuredOutputRagasLlm ?: return null
        return structured.generateNumericValue(prompt)
    }

    override suspend fun generateDiscreteValue(prompt: String): String? {
        val structured = delegate as? StructuredOutputRagasLlm ?: return null
        return structured.generateDiscreteValue(prompt)
    }

    override suspend fun generateRankingItems(prompt: String): List<String>? {
        val structured = delegate as? StructuredOutputRagasLlm ?: return null
        return structured.generateRankingItems(prompt)
    }

    override suspend fun generateContent(
        content: List<PromptContentPart>,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        val multimodal = delegate as? MultiModalRagasLlm
        val promptText = content.joinToString("\n") { part -> part.toPromptText() }
        val result =
            if (multimodal != null) {
                multimodal.generateContent(content, n, temperature, stop)
            } else {
                delegate.generateText(promptText, n, temperature, stop)
            }
        onUsage(tokenUsageParser(promptText, result) ?: heuristicTokenUsage(promptText, result))
        return result
    }

    private fun heuristicTokenUsage(
        prompt: String,
        result: LlmResult,
    ): TokenUsage {
        fun countTokens(text: String): Int =
            text
                .trim()
                .split(Regex("\\s+"))
                .count { token -> token.isNotBlank() }

        val promptTokens = countTokens(prompt)
        val completionTokens = result.generations.sumOf { generation -> countTokens(generation.text) }
        return TokenUsage(promptTokens = promptTokens, completionTokens = completionTokens)
    }
}
