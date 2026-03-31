package ragas.integrations.tracing

data class LangfuseSpan(
    val traceId: String,
    val name: String,
    val startTimeMs: Long,
    var endTimeMs: Long? = null,
    val tags: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)

class LangfuseStyleObserver : TraceObserver {
    private val traces = mutableMapOf<String, LangfuseSpan>()

    override fun onEvent(event: TraceEvent) {
        when (event) {
            is RunStarted -> {
                traces[event.runId] =
                    LangfuseSpan(
                        traceId = event.runId,
                        name = event.runName,
                        startTimeMs = event.timestampMs,
                        tags = event.tags,
                        metadata = event.metadata,
                    )
            }
            is RunCompleted -> {
                traces[event.runId]?.endTimeMs = event.timestampMs
            }
            is RunFailed -> {
                traces[event.runId]?.endTimeMs = event.timestampMs
            }
            is MetricRowLogged -> {
                // row-level spans can be derived externally; we keep trace-level state only
            }
        }
    }

    fun getTrace(runId: String): LangfuseSpan? = traces[runId]

    fun allTraces(): List<LangfuseSpan> = traces.values.toList()
}
