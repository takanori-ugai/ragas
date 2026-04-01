package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.Metric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.jaccardSimilarity
import ragas.metrics.tokenSet
import ragas.metrics.tokenize
import ragas.model.SingleTurnSample
import kotlin.math.pow

class AnswerAccuracyMetric(
    name: String = "answer_accuracy",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "reference")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val question = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        val reference = sample.reference.orEmpty().trim()
        if (question.isBlank() || response.isBlank() || reference.isBlank()) {
            return 0.0
        }

        val normalizedResponse = normalizeText(response)
        val normalizedReference = normalizeText(reference)
        if (normalizedResponse == normalizedReference) {
            return 1.0
        }

        val responseTokens = meaningfulTokenSet(response)
        val referenceTokens = meaningfulTokenSet(reference)
        if (responseTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0.0
        }

        val overlap = responseTokens.intersect(referenceTokens).size.toDouble()
        val precision = overlap / responseTokens.size.toDouble()
        val recall = overlap / referenceTokens.size.toDouble()
        val lexicalF1 = harmonicMean(precision, recall)

        val referenceNumbers = numericTokenSet(reference)
        val responseNumbers = numericTokenSet(response)
        val numberAlignment = numberAlignmentScore(responseNumbers, referenceNumbers)

        val questionTokens = meaningfulTokenSet(question)
        val questionCoverage =
            if (questionTokens.isEmpty()) {
                1.0
            } else {
                responseTokens.intersect(questionTokens).size.toDouble() / questionTokens.size.toDouble()
            }

        var score = (0.65 * lexicalF1) + (0.25 * numberAlignment) + (0.10 * questionCoverage)
        if (referenceNumbers.isNotEmpty() && responseNumbers.isNotEmpty() && responseNumbers.intersect(referenceNumbers).isEmpty()) {
            score *= 0.2
        }

        return clamp01(score)
    }
}

class AnswerCorrectnessMetric(
    name: String = "answer_correctness",
    private val weights: List<Double> = listOf(0.75, 0.25),
    private val beta: Double = 1.0,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "reference")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    init {
        require(weights.size == 2) {
            "Expects a list of two weights. First for factuality, second for semantic similarity"
        }
        require(weights.any { it != 0.0 }) { "At least one weight must be non-zero" }
        require(weights.all { it >= 0.0 }) { "Weights must be non-negative" }
        require(beta.isFinite() && beta > 0.0) { "Beta must be a positive finite value." }
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val question = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        val reference = sample.reference.orEmpty().trim()
        if (question.isBlank() || response.isBlank() || reference.isBlank()) {
            return 0.0
        }

        val factuality = factualityScore(response, reference)
        val similarity =
            if (weights[1] == 0.0) {
                0.0
            } else {
                semanticSimilarityScore(response, reference)
            }

        val weightSum = weights[0] + weights[1]
        val combined = ((weights[0] * factuality) + (weights[1] * similarity)) / weightSum
        return clamp01(combined)
    }

    private fun factualityScore(
        response: String,
        reference: String,
    ): Double {
        val responseStatements = toStatements(response)
        val referenceStatements = toStatements(reference)
        if (responseStatements.isEmpty() && referenceStatements.isEmpty()) {
            return 1.0
        }
        if (responseStatements.isEmpty() || referenceStatements.isEmpty()) {
            return 0.0
        }

        val tp =
            responseStatements.count { answerStmt ->
                referenceStatements.any { refStmt ->
                    statementMatch(
                        answerStmt,
                        refStmt,
                    ) >= 0.55
                }
            }
        val fp = responseStatements.size - tp
        val fn =
            referenceStatements.count { refStmt ->
                responseStatements.none { answerStmt -> statementMatch(answerStmt, refStmt) >= 0.55 }
            }

        val precision =
            if (tp + fp == 0) {
                if (fn == 0) 1.0 else 0.0
            } else {
                tp.toDouble() / (tp + fp).toDouble()
            }
        val recall =
            if (tp + fn == 0) {
                if (fp == 0) 1.0 else 0.0
            } else {
                tp.toDouble() / (tp + fn).toDouble()
            }

        val fbeta = fBetaScore(precision, recall)
        val contradictionPenalty = contradictionPenalty(response, reference)
        return clamp01(fbeta * contradictionPenalty)
    }

    private fun semanticSimilarityScore(
        response: String,
        reference: String,
    ): Double {
        val responseTokens = meaningfulTokenSet(response)
        val referenceTokens = meaningfulTokenSet(reference)
        if (responseTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0.0
        }

        val jaccard = jaccardSimilarity(responseTokens, referenceTokens)
        val coverage = responseTokens.intersect(referenceTokens).size.toDouble() / referenceTokens.size.toDouble()
        return clamp01((0.7 * jaccard) + (0.3 * coverage))
    }

    private fun statementMatch(
        answerStatement: String,
        referenceStatement: String,
    ): Double {
        val answerTokens = meaningfulTokenSet(answerStatement)
        val referenceTokens = meaningfulTokenSet(referenceStatement)
        if (answerTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0.0
        }

        var score = jaccardSimilarity(answerTokens, referenceTokens)
        val answerNums = numericTokenSet(answerStatement)
        val referenceNums = numericTokenSet(referenceStatement)
        if (referenceNums.isNotEmpty() && answerNums.isNotEmpty() && answerNums.intersect(referenceNums).isEmpty()) {
            score *= 0.5
        }
        if (referenceNums.isNotEmpty() && answerNums.isEmpty()) {
            score *= 0.7
        }

        return clamp01(score)
    }

    private fun contradictionPenalty(
        response: String,
        reference: String,
    ): Double {
        var penalty = 1.0
        val responseNums = numericTokenSet(response)
        val referenceNums = numericTokenSet(reference)
        if (referenceNums.isNotEmpty() && responseNums.isNotEmpty() && responseNums.intersect(referenceNums).isEmpty()) {
            penalty *= 0.45
        }

        val responseHasNegation = NEGATION_REGEX.containsMatchIn(response.lowercase())
        val referenceHasNegation = NEGATION_REGEX.containsMatchIn(reference.lowercase())
        if (responseHasNegation != referenceHasNegation) {
            penalty *= 0.8
        }

        return penalty
    }

    private fun toStatements(text: String): List<String> =
        text
            .split(STATEMENT_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun fBetaScore(
        precision: Double,
        recall: Double,
    ): Double {
        if (precision + recall == 0.0) {
            return 0.0
        }
        val betaSq = beta.pow(2)
        return ((1.0 + betaSq) * precision * recall) / ((betaSq * precision) + recall)
    }

    private companion object {
        val STATEMENT_SPLIT_REGEX = Regex("[.!?;]+")
        val NEGATION_REGEX = Regex("\\b(no|not|never|none|without|cannot|can't|won't|isn't|aren't|didn't|doesn't|don't)\\b")
    }
}

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

private fun normalizeText(text: String): String =
    text
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

private fun harmonicMean(
    precision: Double,
    recall: Double,
): Double =
    if (precision + recall == 0.0) {
        0.0
    } else {
        (2.0 * precision * recall) / (precision + recall)
    }

private fun meaningfulTokenSet(text: String): Set<String> =
    tokenize(text)
        .filter { token -> token.length > 2 && token !in STOP_WORDS }
        .toSet()

private fun numericTokenSet(text: String): Set<String> =
    NUMERIC_TOKEN_REGEX
        .findAll(text.lowercase())
        .map { match -> match.value }
        .toSet()

private fun numberAlignmentScore(
    responseNumbers: Set<String>,
    referenceNumbers: Set<String>,
): Double {
    if (referenceNumbers.isEmpty() && responseNumbers.isEmpty()) {
        return 1.0
    }
    if (referenceNumbers.isEmpty()) {
        return 0.9
    }
    if (responseNumbers.isEmpty()) {
        return 0.4
    }

    val hits = responseNumbers.intersect(referenceNumbers).size.toDouble()
    val precision = hits / responseNumbers.size.toDouble()
    val recall = hits / referenceNumbers.size.toDouble()
    return 0.5 * precision + 0.5 * recall
}

private val NUMERIC_TOKEN_REGEX = Regex("\\b\\d+(?:[.,]\\d+)?\\b")

private val STOP_WORDS =
    setOf(
        "the",
        "and",
        "for",
        "with",
        "that",
        "this",
        "from",
        "into",
        "about",
        "your",
        "you",
        "are",
        "was",
        "were",
        "been",
        "have",
        "has",
        "had",
        "will",
        "would",
        "could",
        "should",
        "what",
        "when",
        "where",
        "which",
        "who",
        "whom",
        "whose",
        "why",
        "how",
    )
