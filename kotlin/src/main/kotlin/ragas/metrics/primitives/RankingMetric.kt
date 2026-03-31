package ragas.metrics.primitives

import ragas.llms.BaseRagasLlm
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class RankingMetric(
    override val name: String,
    prompt: String,
    override var llm: BaseRagasLlm?,
    private val expectedSize: Int,
    override val requiredColumns: Map<MetricType, Set<String>> = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response")),
) : BaseMetric(name = name, requiredColumns = requiredColumns, outputType = MetricOutputType.RANKING),
    SingleTurnMetric,
    MetricWithLlm {
    init {
        require(expectedSize > 0) { "expectedSize must be greater than 0" }
    }

    private val template = PromptTemplate(prompt)
    private val listPrefixPattern = Regex("^\\s*(?:[-*•]+|\\d+[.):-]?|[A-Za-z][.):-]|item\\s+\\d+\\s*[:.)-]?)\\s*", RegexOption.IGNORE_CASE)

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
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

        val parsed =
            splitRankingItems(raw)
                .map { item -> item.trim() }
                .map { item -> item.replace(listPrefixPattern, "").trim() }
                .filter { item -> item.isNotBlank() }

        return parsed.take(expectedSize)
    }

    private fun splitRankingItems(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        return if (trimmed.contains('\n')) {
            trimmed.lines()
        } else {
            trimmed.split(',')
        }
    }
}
