package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.COMMON_STOP_WORDS
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MultiTurnMetric
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenize
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.SingleTurnSample
import kotlin.math.pow
import kotlin.math.round

class FactualCorrectnessMetric(
    name: String = "factual_correctness",
    private val mode: Mode = Mode.F1,
    private val beta: Double = 1.0,
    private val atomicity: DecompositionLevel = DecompositionLevel.LOW,
    private val coverage: DecompositionLevel = DecompositionLevel.LOW,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    enum class Mode {
        PRECISION,
        RECALL,
        F1,
    }

    enum class DecompositionLevel {
        LOW,
        HIGH,
    }

    init {
        require(beta.isFinite() && beta > 0.0) {
            "Beta must be a positive finite value."
        }
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val response = sample.response.orEmpty().trim()
        val reference = sample.reference.orEmpty().trim()
        if (response.isBlank() || reference.isBlank()) {
            return 0.0
        }

        val responseClaims = decomposeClaims(response)
        val referenceClaims = decomposeClaims(reference)
        if (responseClaims.isEmpty() && referenceClaims.isEmpty()) {
            return 1.0
        }
        if (responseClaims.isEmpty()) {
            return 0.0
        }

        val responseSupported = responseClaims.map { claim -> isClaimSupported(claim, referenceClaims) }.toBooleanArray()
        val referenceSupported =
            if (mode == Mode.PRECISION) {
                BooleanArray(0)
            } else {
                referenceClaims.map { claim -> isClaimSupported(claim, responseClaims) }.toBooleanArray()
            }

        val precision = safeDivide(responseSupported.count { it }.toDouble(), responseSupported.size.toDouble())
        val recall =
            if (mode == Mode.PRECISION) {
                0.0
            } else {
                safeDivide(referenceSupported.count { it }.toDouble(), referenceSupported.size.toDouble())
            }

        val score =
            when (mode) {
                Mode.PRECISION -> precision
                Mode.RECALL -> recall
                Mode.F1 -> fBeta(precision = precision, recall = recall, beta = beta)
            }

        return round(clamp01(score) * 100.0) / 100.0
    }

    private fun decomposeClaims(text: String): List<String> {
        val sentenceParts =
            text
                .split(SENTENCE_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotBlank() }
        if (sentenceParts.isEmpty()) {
            return emptyList()
        }

        val atomicClaims =
            if (atomicity == DecompositionLevel.HIGH) {
                sentenceParts.flatMap { sentence ->
                    sentence
                        .split(CLAIM_SPLIT_REGEX)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
            } else {
                sentenceParts
            }

        if (coverage == DecompositionLevel.HIGH) {
            return atomicClaims
        }

        return atomicClaims.map { claim ->
            val tokens = meaningfulTokens(claim)
            tokens.take(12).joinToString(" ").ifBlank { claim }
        }
    }

    private fun isClaimSupported(
        claim: String,
        referenceClaims: List<String>,
    ): Boolean {
        if (referenceClaims.isEmpty()) {
            return false
        }
        val claimTokens = meaningfulTokens(claim).toSet()
        if (claimTokens.isEmpty()) {
            return false
        }

        val claimNumbers = numberTokens(claim)
        val bestScore =
            referenceClaims.maxOf { ref ->
                val refTokens = meaningfulTokens(ref).toSet()
                val overlap = claimTokens.intersect(refTokens).size.toDouble()
                val precision = safeDivide(overlap, claimTokens.size.toDouble())
                val recall = safeDivide(overlap, refTokens.size.toDouble())
                var score = (0.6 * precision) + (0.4 * recall)

                val unmatchedCritical =
                    claimTokens
                        .minus(refTokens)
                        .count { token -> token.length >= 5 && token !in GENERIC_FACT_WORDS }
                if (unmatchedCritical > 0) {
                    score *= (1.0 - (0.6 * unmatchedCritical.coerceAtMost(2)))
                }

                val refNumbers = numberTokens(ref)
                if (claimNumbers.isNotEmpty() && refNumbers.isNotEmpty() && claimNumbers.intersect(refNumbers).isEmpty()) {
                    score *= 0.25
                }
                if (claimNumbers.isNotEmpty() && refNumbers.isEmpty()) {
                    score *= 0.6
                }
                score
            }

        return bestScore >= 0.5
    }

    private fun fBeta(
        precision: Double,
        recall: Double,
        beta: Double,
    ): Double {
        if (precision + recall == 0.0) {
            return 0.0
        }
        val betaSq = beta.pow(2.0)
        return ((1.0 + betaSq) * precision * recall) / ((betaSq * precision) + recall)
    }

    private fun safeDivide(
        numerator: Double,
        denominator: Double,
    ): Double =
        if (denominator <= 0.0) {
            0.0
        } else {
            numerator / denominator
        }

    private fun meaningfulTokens(text: String): List<String> =
        tokenize(text).filter { token ->
            token.length > 2 &&
                token !in COMMON_STOP_WORDS
        }

    private fun numberTokens(text: String): Set<String> = NUMBER_TOKEN_REGEX.findAll(text.lowercase()).map { it.value }.toSet()

    private companion object {
        val SENTENCE_SPLIT_REGEX = Regex("[.!?]+")
        val CLAIM_SPLIT_REGEX = Regex("\\b(?:and|but|while|whereas|although)\\b|,")
        val NUMBER_TOKEN_REGEX = Regex("\\b\\d+(?:[.,]\\d+)?\\b")
        val GENERIC_FACT_WORDS =
            setOf(
                "answer",
                "question",
                "response",
                "information",
                "process",
                "system",
                "theory",
                "model",
            )
    }
}

class TopicAdherenceMetric(
    name: String = "topic_adherence",
    private val mode: Mode = Mode.F1,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input", "reference_topics")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    MultiTurnMetric {
    enum class Mode {
        PRECISION,
        RECALL,
        F1,
    }

    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val referenceTopics =
            sample.referenceTopics
                .orEmpty()
                .map { normalizeTopic(it) }
                .filter { it.isNotBlank() }
        if (referenceTopics.isEmpty()) {
            return 0.0
        }

        val extracted = extractTopics(sample.userInput, referenceTopics)
        if (extracted.isEmpty()) {
            return 0.0
        }

        val topicAnswered = extracted.map { entry -> isTopicAnswered(sample.userInput, entry.turnIndex) }.toBooleanArray()
        val topicClassifications = extracted.map { entry -> isReferenceAligned(entry.topic, referenceTopics) }.toBooleanArray()

        val truePositives = topicAnswered.indices.count { i -> topicAnswered[i] && topicClassifications[i] }
        val falsePositives = topicAnswered.indices.count { i -> topicAnswered[i] && !topicClassifications[i] }
        val falseNegatives = topicAnswered.indices.count { i -> !topicAnswered[i] && topicClassifications[i] }

        val precision = safeDivide(truePositives.toDouble(), (truePositives + falsePositives).toDouble())
        val recall = safeDivide(truePositives.toDouble(), (truePositives + falseNegatives).toDouble())
        val score =
            when (mode) {
                Mode.PRECISION -> {
                    precision
                }

                Mode.RECALL -> {
                    recall
                }

                Mode.F1 -> {
                    if (precision + recall == 0.0) {
                        0.0
                    } else {
                        (2.0 * precision * recall) / (precision + recall)
                    }
                }
            }
        return clamp01(score)
    }

    private fun extractTopics(
        messages: List<ConversationMessage>,
        referenceTopics: List<String>,
    ): List<ExtractedTopic> {
        val extracted = mutableListOf<ExtractedTopic>()

        messages.forEachIndexed { index, message ->
            if (message !is HumanMessage) {
                return@forEachIndexed
            }
            val content = message.content
            val contentTokens = topicTokenSet(content)

            var matchedReference = false
            referenceTopics.forEach { topic ->
                val topicTokens = topicTokenSet(topic)
                if (hasSufficientTopicOverlap(topicTokens, contentTokens)) {
                    extracted += ExtractedTopic(topic = topic, turnIndex = index)
                    matchedReference = true
                }
            }

            if (!matchedReference) {
                inferOutOfScopeTopic(content)?.let { inferred ->
                    extracted += ExtractedTopic(topic = inferred, turnIndex = index)
                }
            }
        }

        return extracted.distinctBy { "${it.turnIndex}|${it.topic}" }
    }

    private fun isTopicAnswered(
        messages: List<ConversationMessage>,
        humanTurnIndex: Int,
    ): Boolean {
        val nextHumanIndex =
            messages
                .indices
                .firstOrNull { idx -> idx > humanTurnIndex && messages[idx] is HumanMessage } ?: messages.size

        val aiReplies =
            messages
                .subList(humanTurnIndex + 1, nextHumanIndex)
                .filterIsInstance<AiMessage>()

        if (aiReplies.isEmpty()) {
            return false
        }

        val nonRefusalReply =
            aiReplies.any { ai ->
                val normalized = ai.content.lowercase().trim()
                normalized.isNotBlank() &&
                    REFUSAL_PATTERNS.none { pattern -> pattern.containsMatchIn(normalized) }
            }

        return nonRefusalReply || aiReplies.any { ai -> !ai.toolCalls.isNullOrEmpty() }
    }

    private fun isReferenceAligned(
        topic: String,
        referenceTopics: List<String>,
    ): Boolean {
        val topicTokens = topicTokenSet(topic)
        if (topicTokens.isEmpty()) {
            return false
        }

        return referenceTopics.any { ref ->
            val refTokens = topicTokenSet(ref)
            hasSufficientTopicOverlap(topicTokens, refTokens)
        }
    }

    private fun topicTokenSet(text: String): Set<String> =
        tokenize(normalizeTopic(text)).filter { token -> token !in COMMON_STOP_WORDS }.toSet()

    private fun hasSufficientTopicOverlap(
        left: Set<String>,
        right: Set<String>,
    ): Boolean {
        if (left.isEmpty() || right.isEmpty()) {
            return false
        }
        val overlap = left.intersect(right).size.toDouble()
        if (overlap < MIN_TOPIC_TOKEN_OVERLAP) {
            return false
        }

        val leftCoverage = overlap / left.size.toDouble()
        val rightCoverage = overlap / right.size.toDouble()
        return leftCoverage >= MIN_TOPIC_OVERLAP_RATIO || rightCoverage >= MIN_TOPIC_OVERLAP_RATIO
    }

    private fun inferOutOfScopeTopic(content: String): String? {
        val normalized = content.lowercase()
        OUT_OF_SCOPE_KEYWORDS.forEach { (topic, keywords) ->
            if (keywords.any { keyword -> keyword in normalized }) {
                return topic
            }
        }
        return null
    }

    private fun normalizeTopic(text: String): String =
        text
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun safeDivide(
        numerator: Double,
        denominator: Double,
    ): Double =
        if (denominator <= 0.0) {
            0.0
        } else {
            numerator / denominator
        }

    private data class ExtractedTopic(
        val topic: String,
        val turnIndex: Int,
    )

    private companion object {
        const val MIN_TOPIC_TOKEN_OVERLAP = 1.0
        const val MIN_TOPIC_OVERLAP_RATIO = 0.5
        val REFUSAL_PATTERNS =
            listOf(
                Regex("\\b(can'?t|cannot|won'?t|unable|refuse|decline|sorry)\\b"),
                Regex("\\b(not\\s+able|do\\s+not\\s+have|don't\\s+have|outside\\s+my\\s+scope)\\b"),
            )

        val OUT_OF_SCOPE_KEYWORDS =
            mapOf(
                "sports" to setOf("sports", "football", "soccer", "nba", "nfl", "cricket"),
                "finance" to setOf("finance", "stock", "market", "bitcoin", "crypto", "investment"),
                "politics" to setOf("politics", "election", "senate", "president", "government"),
                "travel" to setOf("travel", "hotel", "flight", "tourism", "visa"),
                "cooking" to setOf("cooking", "recipe", "kitchen", "bake", "meal"),
            )
    }
}
