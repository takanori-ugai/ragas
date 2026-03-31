package ragas.integrations.tracing

sealed interface TraceEvent {
    val runId: String
    val framework: String
    val runName: String
    val timestampMs: Long
}

data class RunStarted(
    override val runId: String,
    override val framework: String,
    override val runName: String,
    override val timestampMs: Long,
    val tags: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
) : TraceEvent

data class MetricRowLogged(
    override val runId: String,
    override val framework: String,
    override val runName: String,
    override val timestampMs: Long,
    val rowIndex: Int,
    val scores: Map<String, Any?>,
) : TraceEvent

data class RunCompleted(
    override val runId: String,
    override val framework: String,
    override val runName: String,
    override val timestampMs: Long,
    val durationMs: Long,
    val aggregateMetrics: Map<String, Double>,
) : TraceEvent

data class RunFailed(
    override val runId: String,
    override val framework: String,
    override val runName: String,
    override val timestampMs: Long,
    val durationMs: Long,
    val errorType: String,
    val errorMessage: String,
) : TraceEvent
