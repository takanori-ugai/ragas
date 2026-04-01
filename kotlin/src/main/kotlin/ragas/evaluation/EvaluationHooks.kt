package ragas.evaluation

import ragas.model.Sample
import java.util.concurrent.atomic.AtomicBoolean

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
) {
    val totalTokens: Int = promptTokens + completionTokens
}

data class TokenUsageEvent(
    val rowIndex: Int,
    val metricName: String,
    val usage: TokenUsage,
)

data class CostEvent(
    val rowIndex: Int,
    val metricName: String,
    val usage: TokenUsage,
    val cost: Double,
)

data class EvaluationRunStartedEvent(
    val runId: String,
    val sampleCount: Int,
    val metricNames: List<String>,
)

data class EvaluationMetricComputedEvent(
    val runId: String,
    val rowIndex: Int,
    val metricName: String,
    val score: Any?,
)

data class EvaluationRunCompletedEvent(
    val runId: String,
    val rowCount: Int,
    val metricCount: Int,
)

data class EvaluationRunFailedEvent(
    val runId: String,
    val error: Throwable,
)

fun interface EvaluationCallback {
    fun onMetricComputed(event: EvaluationMetricComputedEvent)
}

typealias TokenUsageParser = (rowIndex: Int, metricName: String, sample: Sample, metricOutput: Any?) -> TokenUsage?

interface EvaluationLifecycleCallback : EvaluationCallback {
    fun onRunStarted(event: EvaluationRunStartedEvent) {}

    fun onRunCompleted(event: EvaluationRunCompletedEvent) {}

    fun onRunFailed(event: EvaluationRunFailedEvent) {}
}

class EvaluationCancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val executors = mutableListOf<ragas.runtime.Executor>()

    fun cancel() {
        cancelled.set(true)
        synchronized(executors) {
            executors.forEach { it.cancel() }
        }
    }

    fun isCancelled(): Boolean = cancelled.get()

    internal fun bind(executor: ragas.runtime.Executor) {
        synchronized(executors) {
            executors += executor
        }
        if (isCancelled()) {
            executor.cancel()
        }
    }
}

