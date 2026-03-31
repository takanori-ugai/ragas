package ragas.metrics.primitives

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ragas.llms.BaseRagasLlm
import ragas.llms.StructuredOutputRagasLlm
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class NumericMetric(
    override val name: String,
    prompt: String,
    override var llm: BaseRagasLlm?,
    private val allowedRange: ClosedFloatingPointRange<Double> = 0.0..1.0,
    override val requiredColumns: Map<MetricType, Set<String>> = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response")),
) : BaseMetric(name = name, requiredColumns = requiredColumns, outputType = MetricOutputType.CONTINUOUS),
    SingleTurnMetric,
    MetricWithLlm {
    private val template =
        PromptTemplate(
            instructionTemplate = prompt,
            outputSchema =
                buildJsonObject {
                    put("type", "number")
                },
        )

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
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
        val numeric =
            if (llmInstance is StructuredOutputRagasLlm) {
                llmInstance.generateNumericValue(prompt) ?: extractFirstNumber(generateRawResponse(llmInstance, prompt))
            } else {
                extractFirstNumber(generateRawResponse(llmInstance, prompt))
            }
        val clamped =
            when {
                numeric < allowedRange.start -> allowedRange.start
                numeric > allowedRange.endInclusive -> allowedRange.endInclusive
                else -> numeric
            }
        return clamped
    }

    private fun extractFirstNumber(text: String): Double {
        val match = numberRegex.find(text)
        return match?.value?.toDoubleOrNull() ?: allowedRange.start
    }

    companion object {
        private val numberRegex = Regex("[-+]?[0-9]*\\.?[0-9]+")
    }

    private suspend fun generateRawResponse(
        llmInstance: BaseRagasLlm,
        prompt: String,
    ): String =
        llmInstance
            .generateText(prompt = prompt)
            .generations
            .firstOrNull()
            ?.text
            .orEmpty()
}
