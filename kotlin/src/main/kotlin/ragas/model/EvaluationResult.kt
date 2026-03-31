package ragas.model

data class EvaluationResult(
    val scores: List<Map<String, Any?>>,
    val dataset: EvaluationDataset<out Sample>,
    val binaryColumns: Set<String> = emptySet(),
    val traces: Map<String, Any?> = emptyMap(),
) {
    fun metricValues(metricName: String): List<Any?> = scores.map { row -> row[metricName] }

    fun metricMean(metricName: String): Double? {
        val numericValues =
            metricValues(metricName).filterIsInstance<Number>().map { it.toDouble() }
        return if (numericValues.isEmpty()) {
            null
        } else {
            numericValues.average()
        }
    }
}
