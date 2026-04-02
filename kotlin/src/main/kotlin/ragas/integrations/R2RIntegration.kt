package ragas.integrations

import ragas.embeddings.BaseRagasEmbedding
import ragas.integrations.tracing.TraceObserver
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

data class R2RRecord(
    val input: String,
    val output: String,
    val retrievedContexts: List<String> = emptyList(),
    val referenceContexts: List<String> = emptyList(),
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

object R2RIntegration {
    fun toDataset(records: List<R2RRecord>): EvaluationDataset<SingleTurnSample> =
        EvaluationDataset(
            records.map { record ->
                SingleTurnSample(
                    userInput = record.input,
                    response = record.output,
                    retrievedContexts = record.retrievedContexts,
                    referenceContexts = record.referenceContexts.ifEmpty { null },
                    reference = record.reference,
                )
            },
        )

    @Suppress("UNUSED_PARAMETER")
    fun evaluateRecords(
        records: List<R2RRecord>,
        metrics: List<Metric>? = null,
        llm: BaseRagasLlm? = null,
        embeddings: BaseRagasEmbedding? = null,
        runConfig: RunConfig = RunConfig(),
        raiseExceptions: Boolean = false,
        runName: String = "ragas-r2r-evaluation",
        tags: Map<String, String> = emptyMap(),
        metadata: Map<String, String> = emptyMap(),
        observers: List<TraceObserver> = emptyList(),
    ): EvaluationResult =
        traceEvaluation(
            framework = "r2r",
            runName = runName,
            tags = tags,
            metadata = metadata,
            observers = observers,
        ) {
            unsupportedIntegration("r2r")
        }

    fun toMetricPayload(result: EvaluationResult): List<Map<String, Any?>> = result.scores
}
