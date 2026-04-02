package ragas.metrics.defaults

import ragas.metrics.Metric

fun defaultSingleTurnMetrics(): List<Metric> =
    listOf(
        AnswerRelevancyMetric(allowHeuristicFallback = true),
        ContextPrecisionMetric(),
        FaithfulnessMetric(allowHeuristicFallback = true),
        ContextRecallMetric(),
    )
