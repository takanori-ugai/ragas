package ragas.metrics

data class MetricResult<T>(
    val value: T,
    val reason: String? = null,
    val traces: Map<String, Any?>? = null,
) {
    init {
        if (traces != null) {
            val invalidKeys = traces.keys.filterNot { it == "input" || it == "output" }
            require(invalidKeys.isEmpty()) {
                "Invalid trace keys: $invalidKeys. Allowed keys are 'input' and 'output'."
            }
        }
    }
}
