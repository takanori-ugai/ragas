package ragas.metrics.defaults

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenSet
import ragas.model.SingleTurnSample

class ContextRecallMetric : BaseMetric(
    name = "context_recall",
    requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("retrieved_contexts", "reference_contexts")),
    outputType = MetricOutputType.CONTINUOUS,
), SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val retrieved = sample.retrievedContexts.orEmpty()
        val references = sample.referenceContexts.orEmpty()
        if (retrieved.isEmpty() || references.isEmpty()) {
            return 0.0
        }

        val retrievedTokens = retrieved.flatMap { context -> tokenSet(context) }.toSet()
        val referenceTokens = references.flatMap { context -> tokenSet(context) }.toSet()
        if (referenceTokens.isEmpty()) {
            return 0.0
        }

        val covered = referenceTokens.intersect(retrievedTokens).size.toDouble()
        return clamp01(covered / referenceTokens.size.toDouble())
    }
}
