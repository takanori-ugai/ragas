package ragas.metrics

private val tokenPattern = Regex("[A-Za-z0-9]+")

fun tokenize(text: String): List<String> =
    tokenPattern.findAll(text.lowercase()).map { match -> match.value }.toList()

fun tokenSet(text: String): Set<String> = tokenize(text).toSet()

fun jaccardSimilarity(
    left: Set<String>,
    right: Set<String>,
): Double {
    if (left.isEmpty() || right.isEmpty()) {
        return 0.0
    }
    val intersection = left.intersect(right).size.toDouble()
    val union = left.union(right).size.toDouble()
    return if (union == 0.0) 0.0 else intersection / union
}

fun clamp01(value: Double): Double =
    when {
        value < 0.0 -> 0.0
        value > 1.0 -> 1.0
        else -> value
    }
