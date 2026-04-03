package ragas.metrics.collections

import ragas.metrics.Metric

/**
 * Executes tier4CollectionMetrics.
 */
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

/**
 * Executes advancedRubricsTier4Metrics.
 */
fun advancedRubricsTier4Metrics(): List<Metric> =
    listOf(
        DomainSpecificRubricsMetric(),
        RubricsScoreWithoutReferenceMetric(),
        RubricsScoreWithReferenceMetric(),
        InstanceSpecificRubricsMetric(),
    )
