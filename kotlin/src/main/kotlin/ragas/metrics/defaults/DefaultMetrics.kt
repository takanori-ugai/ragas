package ragas.metrics.defaults

import ragas.metrics.Metric

fun defaultSingleTurnMetrics(): List<Metric> =
    listOf(
        AnswerRelevancyMetric(),
        ContextPrecisionMetric(),
        FaithfulnessMetric(),
        ContextRecallMetric(),
    )
