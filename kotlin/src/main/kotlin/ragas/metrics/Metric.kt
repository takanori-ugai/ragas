package ragas.metrics

import ragas.embeddings.BaseRagasEmbedding
import ragas.llms.BaseRagasLlm
import ragas.model.MultiTurnSample
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

enum class MetricType {
    SINGLE_TURN,
    MULTI_TURN,
}

enum class MetricOutputType {
    BINARY,
    DISCRETE,
    CONTINUOUS,
    RANKING,
}

val VALID_COLUMNS: Set<String> =
    setOf(
        "user_input",
        "retrieved_contexts",
        "reference_contexts",
        "retrieved_context_ids",
        "reference_context_ids",
        "response",
        "multi_responses",
        "reference",
        "rubrics",
        "persona_name",
        "query_style",
        "query_length",
        "reference_tool_calls",
        "reference_topics",
    )

interface Metric {
    val name: String
    val requiredColumns: Map<MetricType, Set<String>>
    val outputType: MetricOutputType?

    suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
    }

    fun validateRequiredColumns() {
        requiredColumns.forEach { (_, columns) ->
            columns.forEach { column ->
                require(column in VALID_COLUMNS) {
                    "Invalid column '$column' in metric '$name'. Allowed columns: $VALID_COLUMNS"
                }
            }
        }
    }
}

interface MetricWithLlm {
    var llm: BaseRagasLlm?
}

interface MetricWithEmbeddings {
    var embeddings: BaseRagasEmbedding?
}

interface SingleTurnMetric : Metric {
    suspend fun singleTurnAscore(sample: SingleTurnSample): Any?
}

interface MultiTurnMetric : Metric {
    suspend fun multiTurnAscore(sample: MultiTurnSample): Any?
}

abstract class BaseMetric(
    override val name: String,
    override val requiredColumns: Map<MetricType, Set<String>> = emptyMap(),
    override val outputType: MetricOutputType? = null,
) : Metric
