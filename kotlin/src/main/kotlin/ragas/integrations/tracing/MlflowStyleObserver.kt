package ragas.integrations.tracing

data class MlflowRun(
    val runId: String,
    val runName: String,
    val params: Map<String, String>,
    val metrics: Map<String, Double> = emptyMap(),
    val status: String = "RUNNING",
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
                        params = (event.tags + event.metadata).toMap(),
                    )
            }

            is MetricRowLogged -> {
                // MLflow-style row logs are usually reduced into final metrics
            }

            is RunCompleted -> {
                val run = runs[event.runId] ?: return
                runs[event.runId] =
                    run.copy(
                        metrics = run.metrics + event.aggregateMetrics,
                        status = "FINISHED",
                    )
            }

            is RunFailed -> {
                val run = runs[event.runId] ?: return
                runs[event.runId] = run.copy(status = "FAILED")
            }
        }
    }

    fun getRun(runId: String): MlflowRun? =
        runs[runId]?.let { run ->
            run.copy(params = run.params.toMap(), metrics = run.metrics.toMap())
        }

    fun allRuns(): List<MlflowRun> =
        runs.values.map { run ->
            run.copy(params = run.params.toMap(), metrics = run.metrics.toMap())
        }
}
