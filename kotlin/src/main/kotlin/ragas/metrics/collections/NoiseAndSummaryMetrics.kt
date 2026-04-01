package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenize
import ragas.model.SingleTurnSample

class NoiseSensitivityMetric(
    name: String = "noise_sensitivity",
    private val mode: Mode = Mode.RELEVANT,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "reference", "retrieved_contexts")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    enum class Mode {
        RELEVANT,
        IRRELEVANT,
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val userInput = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        val reference = sample.reference.orEmpty().trim()
        val contexts = sample.retrievedContexts.orEmpty().filter { it.isNotBlank() }
        if (userInput.isBlank() || response.isBlank() || reference.isBlank() || contexts.isEmpty()) {
            return 0.0
        }

        val gtStatements = decomposeStatements(reference, userInput)
        val ansStatements = decomposeStatements(response, userInput)
        if (gtStatements.isEmpty() || ansStatements.isEmpty()) {
            return 0.0
        }

        val retrievedToGroundTruth = matrixFaithfulness(gtStatements, contexts)
        val retrievedToAnswer = matrixFaithfulness(ansStatements, contexts)
        val groundTruthToAnswer = ansStatements.map { stmt -> statementFaithful(stmt, reference) }

        val relevantContexts = contexts.indices.map { ctxIdx -> retrievedToGroundTruth.any { row -> row[ctxIdx] } }
        val relevantFaithful =
            ansStatements.indices.map { stmtIdx ->
                contexts.indices.any { ctxIdx -> relevantContexts[ctxIdx] && retrievedToAnswer[stmtIdx][ctxIdx] }
            }

        val incorrect = groundTruthToAnswer.map { faithful -> !faithful }

        val signal =
            if (mode == Mode.RELEVANT) {
                relevantFaithful.zip(incorrect).map { (faithful, wrong) -> faithful && wrong }
            } else {
                val irrelevantFaithfulRaw =
                    ansStatements.indices.map { stmtIdx ->
                        contexts.indices.any { ctxIdx -> !relevantContexts[ctxIdx] && retrievedToAnswer[stmtIdx][ctxIdx] }
                    }
                val irrelevantFaithful =
                    irrelevantFaithfulRaw.zip(relevantFaithful).map { (irr, rel) -> irr && !rel }
                irrelevantFaithful.zip(incorrect).map { (faithful, wrong) -> faithful && wrong }
            }

        return clamp01(signal.count { it }.toDouble() / signal.size.toDouble())
    }

    private fun matrixFaithfulness(
        statements: List<String>,
        contexts: List<String>,
    ): List<List<Boolean>> = statements.map { statement -> contexts.map { ctx -> statementFaithful(statement, ctx) } }

    private fun decomposeStatements(
        text: String,
        question: String,
    ): List<String> {
        val questionTerms = tokenize(question).toSet()
        return text
            .split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { sentence ->
                sentence
                    .split(CLAUSE_SPLIT_REGEX)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { clause ->
                        val tokens = tokenize(clause).filter { it !in STOP_WORDS }
                        val prioritized =
                            if (questionTerms.isEmpty()) {
                                tokens
                            } else {
                                tokens.sortedByDescending { token ->
                                    token in
                                        questionTerms
                                }
                            }
                        prioritized.joinToString(" ").ifBlank { clause }
                    }
            }
    }

    private fun statementFaithful(
        statement: String,
        context: String,
    ): Boolean {
        val stmtTokens = tokenize(statement).filter { it.length > 2 && it !in STOP_WORDS }.toSet()
        val ctxTokens = tokenize(context).filter { it.length > 2 && it !in STOP_WORDS }.toSet()
        if (stmtTokens.isEmpty() || ctxTokens.isEmpty()) {
            return false
        }

        val overlap = stmtTokens.intersect(ctxTokens).size.toDouble()
        val coverage = overlap / stmtTokens.size.toDouble()
        var score = coverage

        val stmtNums = NUMBER_REGEX.findAll(statement.lowercase()).map { it.value }.toSet()
        val ctxNums = NUMBER_REGEX.findAll(context.lowercase()).map { it.value }.toSet()
        if (stmtNums.isNotEmpty() && ctxNums.isNotEmpty() && stmtNums.intersect(ctxNums).isEmpty()) {
            score *= 0.2
        }

        return score >= 0.45
    }

    private companion object {
        val SENTENCE_SPLIT_REGEX = Regex("[.!?]+")
        val CLAUSE_SPLIT_REGEX = Regex("\\b(?:and|but|while|whereas|although)\\b|,")
        val NUMBER_REGEX = Regex("\\b\\d+(?:[.,]\\d+)?\\b")
        val STOP_WORDS =
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
            )
    }
}

class SummaryScoreMetric(
    name: String = "summary_score",
    private val lengthPenalty: Boolean = true,
    private val coeff: Double = 0.5,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("reference_contexts", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    init {
        require(coeff in 0.0..1.0) {
            "Coefficient must be between 0.0 and 1.0, got $coeff"
        }
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val referenceContexts = sample.referenceContexts.orEmpty().filter { it.isNotBlank() }
        val response = sample.response.orEmpty().trim()
        if (referenceContexts.isEmpty() || response.isBlank()) {
            return 0.0
        }

        val text = referenceContexts.joinToString("\n")
        val keyphrases = extractKeyphrases(text)
        val qaScore = computeQaScore(response, keyphrases)

        if (!lengthPenalty) {
            return clamp01(qaScore)
        }

        val conciseness = computeConcisenessScore(text, response)
        val finalScore = (qaScore * (1.0 - coeff)) + (conciseness * coeff)
        return clamp01(finalScore)
    }

    private fun extractKeyphrases(text: String): List<String> {
        val phrases = linkedSetOf<String>()
        KEYPHRASE_REGEX.findAll(text).forEach { match ->
            val phrase = match.value.trim()
            if (phrase.length >= 4) {
                phrases += phrase
            }
        }

        val tokenCounts =
            tokenize(text)
                .filter { token -> token.length >= 5 && token !in STOP_WORDS }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { (_, count) -> count }
                .take(12)
                .map { (token, _) -> token }
        phrases += tokenCounts

        return phrases.toList()
    }

    private fun computeQaScore(
        summary: String,
        keyphrases: List<String>,
    ): Double {
        if (keyphrases.isEmpty()) {
            return 0.0
        }
        val summaryTokens = tokenize(summary).toSet()
        if (summaryTokens.isEmpty()) {
            return 0.0
        }

        val answers =
            keyphrases.map { phrase ->
                val phraseTokens = tokenize(phrase).toSet()
                if (phraseTokens.isEmpty()) {
                    false
                } else {
                    val overlap = phraseTokens.intersect(summaryTokens).size.toDouble()
                    val coverage = overlap / phraseTokens.size.toDouble()
                    coverage >= 0.6
                }
            }

        return answers.count { it }.toDouble() / answers.size.toDouble()
    }

    private fun computeConcisenessScore(
        text: String,
        summary: String,
    ): Double = 1.0 - (minOf(summary.length, text.length).toDouble() / (text.length.toDouble() + 1e-10))

    private companion object {
        val KEYPHRASE_REGEX =
            Regex("\\b(?:[A-Z][a-zA-Z0-9-]*(?:\\s+[A-Z][a-zA-Z0-9-]*)*|\\d{4}|\\$\\d+(?:[.,]\\d+)?\\s*(?:trillion|billion|million)?)\\b")
        val STOP_WORDS =
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
            )
    }
}
