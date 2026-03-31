package ragas.metrics.primitives

import ragas.llms.BaseRagasLlm
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
) : BaseMetric(name = name, requiredColumns = requiredColumns, outputType = MetricOutputType.CONTINUOUS), SingleTurnMetric, MetricWithLlm {
    private val template = PromptTemplate(prompt)

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val llmInstance = checkNotNull(llm) { "Metric '$name' has no LLM configured." }
        val response =
            llmInstance.generateText(
                prompt =
                    template.render(
                        mapOf(
                            "user_input" to sample.userInput.orEmpty(),
                            "response" to sample.response.orEmpty(),
                            "reference" to sample.reference.orEmpty(),
                            "retrieved_contexts" to sample.retrievedContexts.orEmpty().joinToString("\n"),
                        ),
                    ),
            ).generations.firstOrNull()?.text.orEmpty()

        val numeric = extractFirstNumber(response)
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
}
