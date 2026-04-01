package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample

class MultiModalRelevanceMetric(
    name: String = "multi_modal_relevance",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "retrieved_contexts")),
        outputType = MetricOutputType.BINARY,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val userInput = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        val contexts = sample.retrievedContexts.orEmpty().filter { it.isNotBlank() }
        require(userInput.isNotBlank()) { "user_input is missing. Please provide a question to evaluate against." }
        require(response.isNotBlank()) { "response is missing. Please provide a response to evaluate." }
        require(contexts.isNotEmpty()) { "retrieved_contexts is missing. Please provide contexts to check against." }

        val questionTokens = multimodalMeaningfulTokens(userInput)
        val responseTokens = multimodalMeaningfulTokens(response)
        if (responseTokens.isEmpty()) {
            return 0.0
        }

        val questionRelevance = tokenOverlapF1(responseTokens, questionTokens)
        val textContexts = contexts.filterNot { it.looksLikeImageContext() }
        val imageContexts = contexts.filter { it.looksLikeImageContext() }
        val textSupport =
            textContexts.maxOfOrNull { context ->
                tokenOverlapF1(responseTokens, multimodalMeaningfulTokens(context))
            } ?: 0.0

        if (textContexts.isEmpty() && imageContexts.isNotEmpty()) {
            val imageQuestion =
                userInput.lowercase().contains("image") || userInput.lowercase().contains("photo") ||
                    userInput.lowercase().contains("picture")
            val imageResponse =
                response.lowercase().contains("image") || response.lowercase().contains("photo") || response.lowercase().contains("picture")
            return if (imageQuestion && imageResponse && questionRelevance >= 0.2) 1.0 else 0.0
        }

        val imageAnchor =
            if (imageContexts.isEmpty()) {
                0.0
            } else {
                if (response.lowercase().contains("image") || response.lowercase().contains("photo") ||
                    response.lowercase().contains("picture")
                ) {
                    0.2
                } else {
                    0.1
                }
            }

        val score = (0.55 * questionRelevance) + (0.35 * textSupport) + (0.10 * imageAnchor)
        return if (score >= 0.28) 1.0 else 0.0
    }
}
