package ragas.metrics.collections

import ragas.metrics.Metric

fun tier4CollectionMetrics(): List<Metric> =
    listOf(
        DomainSpecificRubricsMetric(),
        RubricsScoreWithoutReferenceMetric(),
        RubricsScoreWithReferenceMetric(),
        InstanceSpecificRubricsMetric(),
        SqlSemanticEquivalenceMetric(),
        DataCompyScoreMetric(),
        MultiModalRelevanceMetric(),
        MultiModalFaithfulnessMetric(),
    )

fun advancedRubricsTier4Metrics(): List<Metric> =
    listOf(
        DomainSpecificRubricsMetric(),
        RubricsScoreWithoutReferenceMetric(),
        RubricsScoreWithReferenceMetric(),
        InstanceSpecificRubricsMetric(),
    )
