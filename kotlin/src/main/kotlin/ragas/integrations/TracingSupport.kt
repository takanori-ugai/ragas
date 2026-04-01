package ragas.integrations

import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunFailed
import ragas.integrations.tracing.RunStarted
import ragas.integrations.tracing.TraceObserver
import ragas.model.EvaluationResult
import java.util.UUID

@Suppress("TooGenericExceptionCaught")
internal inline fun traceEvaluation(
    framework: String,
    runName: String,
    tags: Map<String, String>,
    metadata: Map<String, String>,
    observers: List<TraceObserver>,
    block: () -> EvaluationResult,
): EvaluationResult {
    val runId = UUID.randomUUID().toString()
    val start = System.currentTimeMillis()
    observers.forEach { observer ->
        observer.onEvent(
            RunStarted(
                runId = runId,
                framework = framework,
                runName = runName,
                timestampMs = start,
                tags = tags,
                metadata = metadata,
            ),
        )
    }

    fun notifyFailure(error: Throwable) {
        val endMs = System.currentTimeMillis()
        observers.forEach { observer ->
            try {
                observer.onEvent(
                    RunFailed(
                        runId = runId,
                        framework = framework,
                        runName = runName,
                        timestampMs = endMs,
                        durationMs = endMs - start,
                        errorType = error::class.simpleName ?: "UnknownError",
                        errorMessage = error.message.orEmpty(),
                    ),
                )
            } catch (observerError: Exception) {
                if (observerError !== error) {
                    error.addSuppressed(observerError)
                }
            } catch (observerError: Error) {
                if (observerError !== error) {
                    error.addSuppressed(observerError)
                }
            }
        }
    }

    return try {
        val result = block()
        result.scores.forEachIndexed { index, row ->
            val rowTimestamp = System.currentTimeMillis()
            observers.forEach { observer ->
                observer.onEvent(
                    MetricRowLogged(
                        runId = runId,
                        framework = framework,
                        runName = runName,
                        timestampMs = rowTimestamp,
                        rowIndex = index,
                        scores = row,
                    ),
                )
            }
        }

        val aggregate = numericMeans(result.scores)
        val endMs = System.currentTimeMillis()
        observers.forEach { observer ->
            observer.onEvent(
                RunCompleted(
                    runId = runId,
                    framework = framework,
                    runName = runName,
                    timestampMs = endMs,
                    durationMs = endMs - start,
                    aggregateMetrics = aggregate,
                ),
            )
        }
        result
    } catch (error: Exception) {
        notifyFailure(error)
        throw error
    } catch (error: Error) {
        notifyFailure(error)
        throw error
    }
}

internal fun numericMeans(scores: List<Map<String, Any?>>): Map<String, Double> {
    if (scores.isEmpty()) {
        return emptyMap()
    }
    val buckets = mutableMapOf<String, MutableList<Double>>()
    scores.forEach { row ->
        row.forEach { (metric, value) ->
            if (value is Number) {
                buckets.getOrPut(metric) { mutableListOf() } += value.toDouble()
            }
        }
    }
    return buckets.mapValues { (_, values) -> values.average() }
}
