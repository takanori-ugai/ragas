package ragas.metrics

data class MetricResult<T>(
    val value: T,
    val reason: String? = null,
    val traces: Map<String, Any?>? = null,
)
