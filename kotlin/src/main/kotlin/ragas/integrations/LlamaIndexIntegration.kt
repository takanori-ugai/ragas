package ragas.integrations

import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluate
import ragas.integrations.tracing.TraceObserver
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

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
}
