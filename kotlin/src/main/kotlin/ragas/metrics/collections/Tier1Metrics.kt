package ragas.metrics.collections

import ragas.metrics.Metric

/**
 * Executes retrievalGroundednessTier1Metrics.
 */
fun retrievalGroundednessTier1Metrics(): List<Metric> =
    listOf(
        ContextRelevanceMetric(),
        ResponseGroundednessMetric(),
        ContextPrecisionWithReferenceMetric(),
        ContextPrecisionWithoutReferenceMetric(),
        IdBasedContextPrecisionMetric(),
        ContextEntityRecallMetric(),
    )
