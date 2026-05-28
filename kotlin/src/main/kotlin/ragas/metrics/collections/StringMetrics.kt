package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.model.SingleTurnSample
import kotlin.math.max
import kotlin.math.min

/**
 * Distance measures supported by [NonLLMStringSimilarity].
 */
enum class DistanceMeasure {
    LEVENSHTEIN,
    HAMMING,
    JARO,
    JARO_WINKLER,
}

/**
 * Exact string equality metric over `response` and `reference`.
 */
class ExactMatch(
    name: String = "exact_match",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
        outputType = MetricOutputType.BINARY,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any =
        if (sample.reference.orEmpty() == sample.response.orEmpty()) 1.0 else 0.0
}

/**
 * Checks whether `reference` is present in `response`.
 */
class StringPresence(
    name: String = "string_present",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
        outputType = MetricOutputType.BINARY,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val reference = sample.reference.orEmpty()
        if (reference.isEmpty()) {
            return 0.0
        }
        val response = sample.response.orEmpty()
        return if (reference in response) 1.0 else 0.0
    }
}

/**
 * String similarity metric without LLM dependencies.
 */
class NonLLMStringSimilarity(
    name: String = "non_llm_string_similarity",
    private val distanceMeasure: DistanceMeasure = DistanceMeasure.LEVENSHTEIN,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val reference = sample.reference.orEmpty()
        val response = sample.response.orEmpty()
        return clamp01(
            when (distanceMeasure) {
                DistanceMeasure.LEVENSHTEIN -> levenshteinSimilarity(reference, response)
                DistanceMeasure.HAMMING -> hammingSimilarity(reference, response)
                DistanceMeasure.JARO -> jaroSimilarity(reference, response)
                DistanceMeasure.JARO_WINKLER -> jaroWinklerSimilarity(reference, response)
            },
        )
    }
}

private fun levenshteinSimilarity(
    left: String,
    right: String,
): Double {
    if (left == right) {
        return 1.0
    }
    if (left.isEmpty() || right.isEmpty()) {
        return 0.0
    }
    val leftLen = left.length
    val rightLen = right.length
    val maxLen = max(leftLen, rightLen)
    if (maxLen == 0) {
        return 1.0
    }

    var prev = IntArray(rightLen + 1) { it }
    var curr = IntArray(rightLen + 1)
    for (i in 1..leftLen) {
        curr[0] = i
        val lc = left[i - 1]
        for (j in 1..rightLen) {
            val substitutionCost = if (lc == right[j - 1]) 0 else 1
            curr[j] =
                min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + substitutionCost,
                )
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    val distance = prev[rightLen]
    return 1.0 - (distance.toDouble() / maxLen.toDouble())
}

private fun hammingSimilarity(
    left: String,
    right: String,
): Double {
    val leftLen = left.length
    val rightLen = right.length
    val maxLen = max(leftLen, rightLen)
    if (maxLen == 0) {
        return 1.0
    }
    val minLen = min(leftLen, rightLen)
    var mismatches = maxLen - minLen
    for (i in 0 until minLen) {
        if (left[i] != right[i]) {
            mismatches += 1
        }
    }
    return 1.0 - (mismatches.toDouble() / maxLen.toDouble())
}

private fun jaroSimilarity(
    left: String,
    right: String,
): Double {
    if (left == right) {
        return 1.0
    }
    if (left.isEmpty() || right.isEmpty()) {
        return 0.0
    }

    val leftLen = left.length
    val rightLen = right.length
    val matchDistance = (max(leftLen, rightLen) / 2) - 1
    if (matchDistance < 0) {
        return 0.0
    }

    val leftMatches = BooleanArray(leftLen)
    val rightMatches = BooleanArray(rightLen)

    var matches = 0
    for (i in 0 until leftLen) {
        val start = max(0, i - matchDistance)
        val end = min(i + matchDistance + 1, rightLen)
        for (j in start until end) {
            if (rightMatches[j]) {
                continue
            }
            if (left[i] != right[j]) {
                continue
            }
            leftMatches[i] = true
            rightMatches[j] = true
            matches += 1
            break
        }
    }

    if (matches == 0) {
        return 0.0
    }

    var transpositions = 0
    var rightIndex = 0
    for (i in 0 until leftLen) {
        if (!leftMatches[i]) {
            continue
        }
        while (!rightMatches[rightIndex]) {
            rightIndex += 1
        }
        if (left[i] != right[rightIndex]) {
            transpositions += 1
        }
        rightIndex += 1
    }

    val m = matches.toDouble()
    val t = (transpositions / 2.0)
    return ((m / leftLen) + (m / rightLen) + ((m - t) / m)) / 3.0
}

private fun jaroWinklerSimilarity(
    left: String,
    right: String,
): Double {
    val jaro = jaroSimilarity(left, right)
    if (jaro <= 0.0) {
        return 0.0
    }
    var prefix = 0
    val maxPrefix = min(4, min(left.length, right.length))
    while (prefix < maxPrefix && left[prefix] == right[prefix]) {
        prefix += 1
    }
    val scalingFactor = 0.1
    return jaro + (prefix * scalingFactor * (1.0 - jaro))
}
