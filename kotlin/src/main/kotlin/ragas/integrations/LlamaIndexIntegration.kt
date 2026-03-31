package ragas.integrations

import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluate
import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunFailed
import ragas.integrations.tracing.RunStarted
import ragas.integrations.tracing.TraceObserver
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import java.util.UUID

data class LlamaIndexRecord(
    val query: String,
    val response: String,
    val retrievedNodes: List<String> = emptyList(),
    val referenceNodes: List<String> = emptyList(),
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

object LlamaIndexIntegration {
    fun toDataset(records: List<LlamaIndexRecord>): EvaluationDataset<SingleTurnSample> =
        EvaluationDataset(
            records.map { record ->
                SingleTurnSample(
                    userInput = record.query,
                    response = record.response,
                    retrievedContexts = record.retrievedNodes,
                    referenceContexts = record.referenceNodes.ifEmpty { null },
                    reference = record.reference,
                )
            },
        )

    fun evaluateRecords(
        records: List<LlamaIndexRecord>,
        metrics: List<Metric>? = null,
        llm: BaseRagasLlm? = null,
        embeddings: BaseRagasEmbedding? = null,
        runConfig: RunConfig = RunConfig(),
        raiseExceptions: Boolean = false,
        runName: String = "ragas-llamaindex-evaluation",
        tags: Map<String, String> = emptyMap(),
        metadata: Map<String, String> = emptyMap(),
        observers: List<TraceObserver> = emptyList(),
    ): EvaluationResult =
        traceEvaluation(
            framework = "llama_index",
            runName = runName,
            tags = tags,
            metadata = metadata,
            observers = observers,
        ) {
            evaluate(
                dataset = toDataset(records),
                metrics = metrics,
                llm = llm,
                embeddings = embeddings,
                runConfig = runConfig,
                raiseExceptions = raiseExceptions,
            )
        }

    fun toMetricPayload(result: EvaluationResult): List<Map<String, Any?>> = result.scores

    private inline fun traceEvaluation(
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

        return try {
            val result = block()
            result.scores.forEachIndexed { index, row ->
                observers.forEach { observer ->
                    observer.onEvent(
                        MetricRowLogged(
                            runId = runId,
                            framework = framework,
                            runName = runName,
                            timestampMs = System.currentTimeMillis(),
                            rowIndex = index,
                            scores = row,
                        ),
                    )
                }
            }

            val aggregate = numericMeans(result.scores)
            observers.forEach { observer ->
                observer.onEvent(
                    RunCompleted(
                        runId = runId,
                        framework = framework,
                        runName = runName,
                        timestampMs = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - start,
                        aggregateMetrics = aggregate,
                    ),
                )
            }
            result
        } catch (error: Throwable) {
            observers.forEach { observer ->
                observer.onEvent(
                    RunFailed(
                        runId = runId,
                        framework = framework,
                        runName = runName,
                        timestampMs = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - start,
                        errorType = error::class.simpleName ?: "UnknownError",
                        errorMessage = error.message.orEmpty(),
                    ),
                )
            }
            throw error
        }
    }

    private fun numericMeans(scores: List<Map<String, Any?>>): Map<String, Double> {
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
}
