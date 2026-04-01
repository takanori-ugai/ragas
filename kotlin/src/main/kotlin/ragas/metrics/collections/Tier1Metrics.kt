package ragas.metrics.collections

import ragas.metrics.Metric

fun retrievalGroundednessTier1Metrics(): List<Metric> =
    listOf(
        ContextRelevanceMetric(),
        ResponseGroundednessMetric(),
        ContextPrecisionWithReferenceMetric(),
        ContextPrecisionWithoutReferenceMetric(),
        IdBasedContextPrecisionMetric(),
        ContextEntityRecallMetric(),
    )
