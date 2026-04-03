package ragas.metrics

/**
 * Named metric score with optional reason metadata.
 */
data class MetricResult<T>(
    val value: T,
    val reason: String? = null,
    val traces: Map<String, Any?>? = null,
)
