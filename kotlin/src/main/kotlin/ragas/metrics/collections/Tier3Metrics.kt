package ragas.metrics.collections

import ragas.metrics.Metric

/**
 * Executes answerQualityTier3Metrics.
 */
fun answerQualityTier3Metrics(): List<Metric> =
    listOf(
        AnswerAccuracyMetric(),
        AnswerCorrectnessMetric(),
        FactualCorrectnessMetric(),
        TopicAdherenceMetric(),
        NoiseSensitivityMetric(),
        SummaryScoreMetric(),
        QuotedSpansAlignmentMetric(),
        ChrfScoreMetric(),
        BleuScoreMetric(),
        RougeScoreMetric(),
        SemanticSimilarityMetric(),
    )
