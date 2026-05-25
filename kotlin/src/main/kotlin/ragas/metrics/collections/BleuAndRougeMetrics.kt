package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenize
import ragas.model.SingleTurnSample
import kotlin.math.exp
import kotlin.math.ln

/**
 * Implements [BleuScoreMetric].
 *
 * @property maxOrder Maximum n-gram order.
 * @property smooth Whether smoothing is enabled.
 */
class BleuScoreMetric(
    name: String = "bleu_score",
    private val maxOrder: Int = 4,
    private val smooth: Boolean = true,
    private val sentenceSplit: Boolean = true,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("reference", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    init {
        require(maxOrder > 0) { "maxOrder must be positive." }
    }

    /**
     * Executes singleTurnAscore.
     * @param sample Evaluation sample to score.
     */
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val reference = sample.reference.orEmpty()
        val response = sample.response.orEmpty()
        if (reference.isBlank() || response.isBlank()) {
            return 0.0
        }

        val tokenPairs = buildTokenPairs(reference, response)
        if (tokenPairs.isEmpty()) {
            return 0.0
        }
        return computeBleuFromTokenPairs(tokenPairs)
    }

    private fun buildTokenPairs(
        reference: String,
        response: String,
    ): List<Pair<List<String>, List<String>>> {
        if (!sentenceSplit) {
            val refTokens = tokenize(reference)
            val respTokens = tokenize(response)
            if (refTokens.isEmpty() || respTokens.isEmpty()) {
                return emptyList()
            }
            return listOf(refTokens to respTokens)
        }

        val referenceSentences = reference.split(". ").map { it.trim() }.filter { it.isNotBlank() }
        val responseSentences = response.split(". ").map { it.trim() }.filter { it.isNotBlank() }

        // Python implementation delegates to sacrebleu with sentence lists.
        // When sentence counts diverge, fallback to whole-text scoring for deterministic behavior.
        if (referenceSentences.size != responseSentences.size) {
            val refTokens = tokenize(reference)
            val respTokens = tokenize(response)
            if (refTokens.isEmpty() || respTokens.isEmpty()) {
                return emptyList()
            }
            return listOf(refTokens to respTokens)
        }

        return referenceSentences.indices
            .mapNotNull { index ->
                val refTokens = tokenize(referenceSentences[index])
                val respTokens = tokenize(responseSentences[index])
                if (refTokens.isEmpty() || respTokens.isEmpty()) null else (refTokens to respTokens)
            }
    }

    private fun computeBleuFromTokenPairs(tokenPairs: List<Pair<List<String>, List<String>>>): Double {
        val maxComparableOrder =
            tokenPairs.maxOfOrNull { (refTokens, respTokens) ->
                minOf(refTokens.size, respTokens.size)
            } ?: 0
        val effectiveOrder = minOf(maxOrder, maxComparableOrder)
        if (effectiveOrder == 0) {
            return 0.0
        }

        val clippedCounts = IntArray(effectiveOrder)
        val totalCounts = IntArray(effectiveOrder)
        var referenceLength = 0
        var responseLength = 0

        tokenPairs.forEach { (refTokens, respTokens) ->
            referenceLength += refTokens.size
            responseLength += respTokens.size

            for (n in 1..effectiveOrder) {
                val respNgrams = ngramCounts(respTokens, n)
                val refNgrams = ngramCounts(refTokens, n)
                totalCounts[n - 1] += respNgrams.values.sum()
                clippedCounts[n - 1] += respNgrams.entries.sumOf { (ngram, count) -> minOf(count, refNgrams[ngram] ?: 0) }
            }
        }

        if (responseLength == 0) {
            return 0.0
        }

        val precisions =
            (0 until effectiveOrder).map { i ->
                val clipped = clippedCounts[i].toDouble()
                val total = totalCounts[i].toDouble()
                if (total == 0.0) {
                    0.0
                } else if (smooth) {
                    (clipped + 1.0) / (total + 1.0)
                } else {
                    clipped / total
                }
            }

        if (precisions.any { it <= 0.0 }) {
            return 0.0
        }

        val logPrecisionMean = precisions.map { ln(it) }.average()
        val brevityPenalty =
            if (responseLength > referenceLength) {
                1.0
            } else {
                exp(1.0 - (referenceLength.toDouble() / responseLength.toDouble()))
            }

        return clamp01(brevityPenalty * exp(logPrecisionMean))
    }

    private fun ngramCounts(
        tokens: List<String>,
        n: Int,
    ): Map<String, Int> {
        if (tokens.size < n) {
            return emptyMap()
        }
        val counts = linkedMapOf<String, Int>()
        for (i in 0..tokens.size - n) {
            val ngram = tokens.subList(i, i + n).joinToString(" ")
            counts[ngram] = (counts[ngram] ?: 0) + 1
        }
        return counts
    }
}

/**
 * Implements [RougeScoreMetric].
 *
 * @property rougeType ROUGE variant.
 * @property mode Scoring mode.
 */
class RougeScoreMetric(
    name: String = "rouge_score",
    private val rougeType: RougeType = RougeType.ROUGE_L,
    private val mode: Mode = Mode.FMEASURE,
    private val useStemmer: Boolean = true,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("reference", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    /**
     * Enumerates RougeType values.
     */
    enum class RougeType {
        ROUGE_1,
        ROUGE_L,
    }

    /**
     * Enumerates Mode values.
     */
    enum class Mode {
        FMEASURE,
        PRECISION,
        RECALL,
    }

    /**
     * Executes singleTurnAscore.
     * @param sample Evaluation sample to score.
     */
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val reference = sample.reference.orEmpty()
        val response = sample.response.orEmpty()
        if (reference.isBlank() || response.isBlank()) {
            return 0.0
        }

        val refTokens = normalizeRougeTokens(tokenize(reference))
        val respTokens = normalizeRougeTokens(tokenize(response))
        if (refTokens.isEmpty() || respTokens.isEmpty()) {
            return 0.0
        }

        val overlap =
            when (rougeType) {
                RougeType.ROUGE_1 -> rouge1Overlap(refTokens, respTokens).toDouble()
                RougeType.ROUGE_L -> lcsLength(refTokens, respTokens).toDouble()
            }

        val precision = overlap / respTokens.size.toDouble()
        val recall = overlap / refTokens.size.toDouble()
        val fmeasure =
            if (precision + recall == 0.0) {
                0.0
            } else {
                2.0 * precision * recall / (precision + recall)
            }

        val score =
            when (mode) {
                Mode.FMEASURE -> fmeasure
                Mode.PRECISION -> precision
                Mode.RECALL -> recall
            }
        return clamp01(score)
    }

    private fun normalizeRougeTokens(tokens: List<String>): List<String> =
        if (!useStemmer) {
            tokens
        } else {
            tokens.map(::stemPorterLike)
        }

    private fun rouge1Overlap(
        reference: List<String>,
        response: List<String>,
    ): Int {
        val refCounts = reference.groupingBy { it }.eachCount().toMutableMap()
        var overlap = 0
        response.forEach { token ->
            val remaining = refCounts[token] ?: 0
            if (remaining > 0) {
                overlap += 1
                refCounts[token] = remaining - 1
            }
        }
        return overlap
    }

    private fun lcsLength(
        reference: List<String>,
        response: List<String>,
    ): Int {
        val dp = Array(reference.size + 1) { IntArray(response.size + 1) }
        for (i in 1..reference.size) {
            for (j in 1..response.size) {
                dp[i][j] =
                    if (reference[i - 1] == response[j - 1]) {
                        dp[i - 1][j - 1] + 1
                    } else {
                        maxOf(dp[i - 1][j], dp[i][j - 1])
                    }
            }
        }
        return dp[reference.size][response.size]
    }

    private fun stemPorterLike(token: String): String {
        if (token.length <= 3) {
            return token
        }

        var stem = token
        when {
            stem.endsWith("ies") && stem.length > 4 -> {
                stem = stem.dropLast(3) + "y"
            }

            stem.endsWith("ing") && stem.length > 5 -> {
                stem = stem.dropLast(3)
                if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) {
                    stem = stem.dropLast(1)
                }
            }

            stem.endsWith("ed") && stem.length > 4 -> {
                stem = stem.dropLast(2)
                if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) {
                    stem = stem.dropLast(1)
                }
            }

            stem.endsWith("es") && stem.length > 4 -> {
                stem = stem.dropLast(2)
            }

            stem.endsWith("s") && stem.length > 3 -> {
                stem = stem.dropLast(1)
            }
        }

        if (stem.endsWith("ational")) return stem.dropLast(7) + "ate"
        if (stem.endsWith("tional")) return stem.dropLast(6) + "tion"
        if (stem.endsWith("izer")) return stem.dropLast(4) + "ize"
        if (stem.endsWith("iveness")) return stem.dropLast(7) + "ive"
        if (stem.endsWith("fulness")) return stem.dropLast(7) + "ful"
        if (stem.endsWith("ousness")) return stem.dropLast(7) + "ous"
        if (stem.endsWith("ation")) return stem.dropLast(5) + "ate"
        if (stem.endsWith("tion")) return stem
        if (stem.endsWith("ment") && stem.length > 6) return stem.dropLast(4)
        return stem
    }
}
