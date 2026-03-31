package ragas.metrics.defaults

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.jaccardSimilarity
import ragas.metrics.tokenSet
import ragas.model.SingleTurnSample

class AnswerRelevancyMetric : BaseMetric(
    name = "answer_relevancy",
    requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response")),
    outputType = MetricOutputType.CONTINUOUS,
), SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val questionTokens = tokenSet(sample.userInput.orEmpty())
        val answerTokens = tokenSet(sample.response.orEmpty())
        return clamp01(jaccardSimilarity(questionTokens, answerTokens))
    }
}
