package ragas.integrations

import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluate
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

data class LangChainRecord(
    val question: String,
    val answer: String,
    val retrievedContexts: List<String> = emptyList(),
    val referenceContexts: List<String> = emptyList(),
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

object LangChainIntegration {
    fun toDataset(records: List<LangChainRecord>): EvaluationDataset<SingleTurnSample> =
        EvaluationDataset(
            records.map { record ->
                SingleTurnSample(
                    userInput = record.question,
                    response = record.answer,
                    retrievedContexts = record.retrievedContexts,
                    referenceContexts = record.referenceContexts.ifEmpty { null },
                    reference = record.reference,
                )
            },
        )

    fun evaluateRecords(
        records: List<LangChainRecord>,
        metrics: List<Metric>? = null,
        llm: BaseRagasLlm? = null,
        embeddings: BaseRagasEmbedding? = null,
        runConfig: RunConfig = RunConfig(),
        raiseExceptions: Boolean = false,
    ): EvaluationResult =
        evaluate(
            dataset = toDataset(records),
            metrics = metrics,
            llm = llm,
            embeddings = embeddings,
            runConfig = runConfig,
            raiseExceptions = raiseExceptions,
        )

    fun toMetricPayload(result: EvaluationResult): List<Map<String, Any?>> = result.scores
}
