package ragas.metrics.defaults

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenSet
import ragas.model.SingleTurnSample

class ContextPrecisionMetric :
    BaseMetric(
        name = "context_precision",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("retrieved_contexts", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val contexts = sample.retrievedContexts.orEmpty()
        if (contexts.isEmpty()) {
            return 0.0
        }

        val responseTokens = tokenSet(sample.response.orEmpty())
        if (responseTokens.isEmpty()) {
            return 0.0
        }

        val relevant =
            contexts.count { context ->
                val contextTokens = tokenSet(context)
                contextTokens.intersect(responseTokens).isNotEmpty()
            }

        return clamp01(relevant.toDouble() / contexts.size.toDouble())
    }
}
