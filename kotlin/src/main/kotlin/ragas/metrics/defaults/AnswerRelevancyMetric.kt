package ragas.metrics.defaults

import ragas.embeddings.BaseRagasEmbedding
import ragas.metrics.BaseMetric
import ragas.metrics.COMMON_STOP_WORDS
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithEmbeddings
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.jaccardSimilarity
import ragas.metrics.tokenize
import ragas.model.SingleTurnSample
import kotlin.math.sqrt

class AnswerRelevancyMetric(
    override var embeddings: BaseRagasEmbedding? = null,
    private val strictness: Int = 3,
) :
    BaseMetric(
        name = "answer_relevancy",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric,
    MetricWithEmbeddings {
    init {
        require(strictness > 0) { "Strictness must be a positive integer." }
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val question = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        if (question.isBlank() || response.isBlank()) {
            return 0.0
        }

        val lexicalScore = lexicalRelevancyScore(question, response)
        val semanticScore = semanticRelevancyScore(question, response)
        var score =
            if (semanticScore != null) {
                (0.65 * semanticScore) + (0.35 * lexicalScore)
            } else {
                lexicalScore
            }

        // Mirror Python intent: noncommittal responses should collapse relevancy.
        if (isNoncommittalResponse(response)) {
            score *= NONCOMMITTAL_DOWNWEIGHT
        }

        return clamp01(score)
    }

    private suspend fun semanticRelevancyScore(
        question: String,
        response: String,
    ): Double? {
        val embeddingModel = embeddings ?: return null
        val questionVector = embeddingModel.embedText(question)
        val responseVector = embeddingModel.embedText(response)
        return cosineSimilarity(questionVector, responseVector)
    }

    private fun lexicalRelevancyScore(
        question: String,
        response: String,
    ): Double {
        val questionTokens = meaningfulTokens(question).toSet()
        val responseTokens = meaningfulTokens(response).toSet()
        if (questionTokens.isEmpty() || responseTokens.isEmpty()) {
            return 0.0
        }

        val overlap = questionTokens.intersect(responseTokens).size.toDouble()
        val precision = overlap / responseTokens.size.toDouble()
        val recall = overlap / questionTokens.size.toDouble()
        val f1 = harmonicMean(precision, recall)
        val jaccard = jaccardSimilarity(questionTokens, responseTokens)

        var score =
            (LEXICAL_F1_WEIGHT * f1) +
                (LEXICAL_RECALL_WEIGHT * recall) +
                (LEXICAL_JACCARD_WEIGHT * jaccard)

        if (responseTokens.size > questionTokens.size * VERBOSITY_RATIO_THRESHOLD && precision < LOW_PRECISION_THRESHOLD) {
            score *= VERBOSITY_PENALTY
        }

        if (response.endsWith("?")) {
            score *= QUESTION_ENDING_PENALTY
        }

        return clamp01(score)
    }

    private fun meaningfulTokens(text: String): List<String> =
        tokenize(text).filter { token ->
            token.length > 2 && token !in COMMON_STOP_WORDS && token !in ANSWER_RELEVANCY_STOP_WORDS
        }

    private fun harmonicMean(
        precision: Double,
        recall: Double,
    ): Double =
        if (precision + recall == 0.0) {
            0.0
        } else {
            (2.0 * precision * recall) / (precision + recall)
        }

    private fun cosineSimilarity(
        left: List<Float>,
        right: List<Float>,
    ): Double {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) {
            return 0.0
        }

        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { index ->
            val l = left[index].toDouble()
            val r = right[index].toDouble()
            dot += l * r
            leftNorm += l * l
            rightNorm += r * r
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0
        }
        return clamp01(dot / (sqrt(leftNorm) * sqrt(rightNorm)))
    }

    private fun isNoncommittalResponse(response: String): Boolean {
        val normalized = response.lowercase()
        val matchedCount =
            NONCOMMITTAL_PATTERNS.count { pattern ->
                pattern.containsMatchIn(normalized)
            }
        return matchedCount >= NONCOMMITTAL_MATCH_THRESHOLD
    }

    private companion object {
        const val LEXICAL_F1_WEIGHT = 0.5
        const val LEXICAL_RECALL_WEIGHT = 0.35
        const val LEXICAL_JACCARD_WEIGHT = 0.15
        const val VERBOSITY_RATIO_THRESHOLD = 4
        const val LOW_PRECISION_THRESHOLD = 0.2
        const val VERBOSITY_PENALTY = 0.75
        const val QUESTION_ENDING_PENALTY = 0.6
        const val NONCOMMITTAL_DOWNWEIGHT = 0.0
        const val NONCOMMITTAL_MATCH_THRESHOLD = 1

        val NONCOMMITTAL_PATTERNS =
            listOf(
                Regex("\\bi\\s+(don't|do not)\\s+know\\b"),
                Regex("\\bi\\s+(am\\s+)?not\\s+sure\\b"),
                Regex("\\bcan't\\s+say\\b|\\bcannot\\s+say\\b"),
                Regex("\\bno\\s+idea\\b"),
                Regex("\\bunclear\\b|\\bunsure\\b"),
                Regex("\\bit\\s+depends\\b"),
                Regex("\\binsufficient\\s+information\\b"),
                Regex("\\bnot\\s+enough\\s+information\\b"),
                Regex("\\bcannot\\s+help\\b|\\bcan't\\s+help\\b"),
            )

        val ANSWER_RELEVANCY_STOP_WORDS =
            setOf(
                "what",
                "which",
                "when",
                "where",
                "who",
                "whom",
                "whose",
                "why",
                "how",
            )
    }
}
