package ragas.metrics.primitives

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import ragas.llms.BaseRagasLlm
import ragas.llms.StructuredOutputRagasLlm
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

    private val template =
        PromptTemplate(
            instructionTemplate = prompt,
            outputSchema =
                buildJsonObject {
                    put("type", "string")
                    putJsonArray("enum") {
                        allowedValues.forEach { value -> add(JsonPrimitive(value)) }
                    }
                },
        )

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any? {
        val llmInstance = checkNotNull(llm) { "Metric '$name' has no LLM configured." }
        val prompt =
            template.render(
                mapOf(
                    "user_input" to sample.userInput.orEmpty(),
                    "response" to sample.response.orEmpty(),
                    "reference" to sample.reference.orEmpty(),
                    "retrieved_contexts" to sample.retrievedContexts.orEmpty().joinToString("\n"),
                ),
            )
        val raw =
            if (llmInstance is StructuredOutputRagasLlm) {
                llmInstance.generateDiscreteValue(prompt).orEmpty()
            } else {
                llmInstance
                    .generateText(prompt = prompt)
                    .generations
                    .firstOrNull()
                    ?.text
                    .orEmpty()
            }.trim()

        val normalized = raw.lowercase()
        val selected =
            allowedValues.firstOrNull { allowed ->
                allowed.lowercase() == normalized || normalized.contains(allowed.lowercase())
            }
        return selected
    }
}
