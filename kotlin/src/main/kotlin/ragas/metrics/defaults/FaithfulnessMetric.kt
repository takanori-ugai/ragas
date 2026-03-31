package ragas.metrics.defaults

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenSet
import ragas.model.SingleTurnSample

class FaithfulnessMetric :
    BaseMetric(
        name = "faithfulness",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("retrieved_contexts", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    private val minSentenceCoveragePerContext = 0.5

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val contexts = sample.retrievedContexts.orEmpty()
        val response = sample.response.orEmpty()
        if (contexts.isEmpty() || response.isBlank()) {
            return 0.0
        }

        val contextTokenSets = contexts.map { context -> tokenSet(context) }
        val responseSentences =
            response
                .split(Regex("[.!?]"))
                .map { sentence -> sentence.trim() }
                .filter { sentence -> sentence.isNotBlank() }

        if (responseSentences.isEmpty()) {
            return 0.0
        }

        val supported =
            responseSentences.count { sentence ->
                val sentenceTokens = tokenSet(sentence)
                sentenceTokens.isNotEmpty() &&
                    contextTokenSets.any { contextTokens ->
                        val overlap = sentenceTokens.intersect(contextTokens).size.toDouble()
                        val coverage = overlap / sentenceTokens.size.toDouble()
                        coverage >= minSentenceCoveragePerContext
                    }
            }

        return clamp01(supported.toDouble() / responseSentences.size.toDouble())
    }
}
