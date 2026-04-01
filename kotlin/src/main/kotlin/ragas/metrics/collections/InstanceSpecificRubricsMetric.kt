package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample

class InstanceSpecificRubricsMetric(
    name: String = "instance_specific_rubrics",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "rubrics")),
        outputType = MetricOutputType.DISCRETE,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val sampleRubrics = sample.rubrics
        require(!sampleRubrics.isNullOrEmpty()) {
            "rubrics must be provided for instance-specific evaluation"
        }
        val normalizedRubrics = normalizeRubrics(sampleRubrics)

        return computeRubricScore(
            rubrics = normalizedRubrics,
            userInput = sample.userInput.orEmpty(),
            response = sample.response.orEmpty(),
            reference = sample.reference,
            retrievedContexts = sample.retrievedContexts,
            referenceContexts = sample.referenceContexts,
            withReference = !sample.reference.isNullOrBlank(),
        )
    }
}
