package ragas.metrics.primitives

import ragas.llms.BaseRagasLlm
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class DiscreteMetric(
    override val name: String,
    prompt: String,
    override var llm: BaseRagasLlm?,
    private val allowedValues: List<String>,
    override val requiredColumns: Map<MetricType, Set<String>> = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response")),
) : BaseMetric(name = name, requiredColumns = requiredColumns, outputType = MetricOutputType.DISCRETE),
    SingleTurnMetric,
    MetricWithLlm {
    init {
        require(allowedValues.isNotEmpty()) { "allowedValues cannot be empty" }
    }

    private val template = PromptTemplate(prompt)

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any? {
        val llmInstance = checkNotNull(llm) { "Metric '$name' has no LLM configured." }
        val raw =
            llmInstance
                .generateText(
                    prompt =
                        template.render(
                            mapOf(
                                "user_input" to sample.userInput.orEmpty(),
                                "response" to sample.response.orEmpty(),
                                "reference" to sample.reference.orEmpty(),
                                "retrieved_contexts" to sample.retrievedContexts.orEmpty().joinToString("\n"),
                            ),
                        ),
                ).generations
                .firstOrNull()
                ?.text
                .orEmpty()
                .trim()

        val normalized = raw.lowercase()
        val selected =
            allowedValues.firstOrNull { allowed ->
                allowed.lowercase() == normalized || normalized.contains(allowed.lowercase())
            }
        return selected
    }
}
