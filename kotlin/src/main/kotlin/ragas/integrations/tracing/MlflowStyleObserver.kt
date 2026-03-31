package ragas.integrations.tracing

data class MlflowRun(
    val runId: String,
    val runName: String,
    val params: Map<String, String>,
    val metrics: MutableMap<String, Double> = mutableMapOf(),
    var status: String = "RUNNING",
)

class MlflowStyleObserver : TraceObserver {
    private val runs = mutableMapOf<String, MlflowRun>()

    override fun onEvent(event: TraceEvent) {
        when (event) {
            is RunStarted -> {
                runs[event.runId] =
                    MlflowRun(
                        runId = event.runId,
                        runName = event.runName,
                        params = event.tags + event.metadata,
                    )
            }
            is MetricRowLogged -> {
                // MLflow-style row logs are usually reduced into final metrics
            }
            is RunCompleted -> {
                val run = runs[event.runId] ?: return
                run.metrics.putAll(event.aggregateMetrics)
                run.status = "FINISHED"
            }
            is RunFailed -> {
                runs[event.runId]?.status = "FAILED"
            }
        }
    }

    fun getRun(runId: String): MlflowRun? = runs[runId]

    fun allRuns(): List<MlflowRun> = runs.values.toList()
}
