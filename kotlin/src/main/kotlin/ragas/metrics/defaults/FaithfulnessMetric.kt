package ragas.metrics.defaults

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import ragas.llms.BaseRagasLlm
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenSet
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

class FaithfulnessMetric :
    BaseMetric(
        name = "faithfulness",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input", "response", "retrieved_contexts")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric,
    MetricWithLlm {
    override var llm: BaseRagasLlm? = null

    private val minSentenceCoveragePerContext = 0.5

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val llmInstance = llm
        if (llmInstance != null) {
            return llmFaithfulnessScore(sample, llmInstance)
        }
        return fallbackFaithfulnessScore(sample)
    }

    private suspend fun llmFaithfulnessScore(
        sample: SingleTurnSample,
        llmInstance: BaseRagasLlm,
    ): Double {
        val userInput = sample.userInput.orEmpty().trim()
        val response = sample.response.orEmpty().trim()
        val contexts = sample.retrievedContexts.orEmpty().filter { it.isNotBlank() }
        require(userInput.isNotBlank()) { "user_input is missing. Please add user_input to the test sample." }
        require(response.isNotBlank()) { "response is missing. Please add response to the test sample." }
        require(contexts.isNotEmpty()) { "retrieved_contexts is missing. Please add retrieved_contexts to the test sample." }

        val statements = generateStatements(userInput, response, llmInstance)
        if (statements.isEmpty()) {
            return Double.NaN
        }

        val verdicts = generateVerdicts(statements, contexts.joinToString("\n"), llmInstance)
        if (verdicts.isEmpty()) {
            return Double.NaN
        }

        val faithfulCount = verdicts.count { verdict -> verdict != 0 }
        return faithfulCount.toDouble() / verdicts.size.toDouble()
    }

    private suspend fun generateStatements(
        question: String,
        response: String,
        llmInstance: BaseRagasLlm,
    ): List<String> {
        val prompt =
            buildString {
                appendLine("Given a question and an answer, break the answer into atomic statements.")
                appendLine("Rules:")
                appendLine("- Keep statements faithful to the answer.")
                appendLine("- Avoid pronouns; use explicit entities.")
                appendLine("- Return JSON only with this shape: {\"statements\": [\"...\"]}")
                appendLine()
                appendLine("Question:")
                appendLine(question)
                appendLine("Answer:")
                appendLine(response)
                append("Output:")
            }
        val raw =
            llmInstance
                .generateText(prompt = prompt)
                .generations
                .firstOrNull()
                ?.text
                .orEmpty()
        val parsed = LlmJsonSupport.parseFirstJsonObject(raw) ?: return emptyList()
        return LlmJsonSupport.readStringArray(parsed, "statements")
    }

    private suspend fun generateVerdicts(
        statements: List<String>,
        context: String,
        llmInstance: BaseRagasLlm,
    ): List<Int> {
        val statementsJson = statements.joinToString(separator = "\", \"", prefix = "[\"", postfix = "\"]")
        val prompt =
            buildString {
                appendLine("Judge whether each statement is directly inferable from the context.")
                appendLine("Return verdict=1 if inferable, else verdict=0.")
                appendLine("Return JSON only with this shape:")
                appendLine("{\"statements\":[{\"statement\":\"...\",\"reason\":\"...\",\"verdict\":0}]}")
                appendLine()
                appendLine("Context:")
                appendLine(context)
                appendLine("Statements:")
                appendLine(statementsJson)
                append("Output:")
            }
        val raw =
            llmInstance
                .generateText(prompt = prompt)
                .generations
                .firstOrNull()
                ?.text
                .orEmpty()
        val parsed = LlmJsonSupport.parseFirstJsonObject(raw) ?: return emptyList()
        val items = parsed["statements"]?.jsonArray.orEmpty()
        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            LlmJsonSupport.readIntLike(obj, "verdict")
        }
    }

    private fun fallbackFaithfulnessScore(sample: SingleTurnSample): Double {
        val contexts = sample.retrievedContexts.orEmpty()
        val response = sample.response.orEmpty()
        if (contexts.isEmpty() || response.isBlank()) {
            return 0.0
        }

        val contextTokenSets = contexts.map { context -> tokenSet(context) }
        val responseSentences =
            response
                .split(Regex("[.!?]"))
                .map { sentence -> sentence.trim() }
                .filter { sentence -> sentence.isNotBlank() }

        if (responseSentences.isEmpty()) {
            return 0.0
        }

        val supported =
            responseSentences.count { sentence ->
                val sentenceTokens = tokenSet(sentence)
                sentenceTokens.isNotEmpty() &&
                    contextTokenSets.any { contextTokens ->
                        val overlap = sentenceTokens.intersect(contextTokens).size.toDouble()
                        val coverage = overlap / sentenceTokens.size.toDouble()
                        coverage >= minSentenceCoveragePerContext
                    }
            }

        return clamp01(supported.toDouble() / responseSentences.size.toDouble())
    }
}
