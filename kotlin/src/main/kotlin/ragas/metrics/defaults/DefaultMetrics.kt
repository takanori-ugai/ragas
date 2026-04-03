package ragas.metrics.defaults

import ragas.metrics.Metric

/**
 * Executes defaultSingleTurnMetrics.
 */
fun defaultSingleTurnMetrics(): List<Metric> =
    listOf(
        AnswerRelevancyMetric(allowHeuristicFallback = true),
        ContextPrecisionMetric(),
        FaithfulnessMetric(allowHeuristicFallback = true),
        ContextRecallMetric(),
    )
