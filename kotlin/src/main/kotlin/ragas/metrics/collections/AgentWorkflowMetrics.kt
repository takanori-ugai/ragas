package ragas.metrics.collections

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import ragas.llms.BaseRagasLlm
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.MultiTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.defaults.LlmJsonSupport
import ragas.metrics.tokenize
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.ToolMessage
import ragas.runtime.RunConfig

/**
 * Implements [AgentGoalAccuracyWithReferenceMetric].
 */
class AgentGoalAccuracyWithReferenceMetric(
    name: String = "agent_goal_accuracy_with_reference",
    private val maxRetries: Int = 3,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input", "reference")),
        outputType = MetricOutputType.BINARY,
    ),
    MultiTurnMetric,
    MetricWithLlm {
    override var llm: BaseRagasLlm? = null

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    /**
     * Executes multiTurnAscore.
     * @param sample Evaluation sample to score.
     */
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val desiredOutcome = sample.reference.orEmpty().trim()
        if (desiredOutcome.isBlank()) {
            return 0.0
        }
        val llmInstance = llm
        if (llmInstance != null) {
            try {
                val inferred = inferGoalOutcomeWithLlm(llmInstance, sample.userInput, maxRetries)
                val endState = inferred?.endState.orEmpty()
                if (endState.isNotBlank()) {
                    val verdict = compareOutcomesWithLlm(llmInstance, desiredOutcome, endState, maxRetries)
                    if (verdict != null) {
                        return verdict.toDouble()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to heuristic mode for parity-safe behavior.
            }
        }
        val endState = inferEndState(sample.userInput)
        return if (isGoalAchieved(desiredOutcome, endState)) 1.0 else 0.0
    }
}

/**
 * Implements [AgentGoalAccuracyWithoutReferenceMetric].
 */
class AgentGoalAccuracyWithoutReferenceMetric(
    name: String = "agent_goal_accuracy_without_reference",
    private val maxRetries: Int = 3,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input")),
        outputType = MetricOutputType.BINARY,
    ),
    MultiTurnMetric,
    MetricWithLlm {
    override var llm: BaseRagasLlm? = null

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    /**
     * Executes multiTurnAscore.
     * @param sample Evaluation sample to score.
     */
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val llmInstance = llm
        if (llmInstance != null) {
            try {
                val inferred = inferGoalOutcomeWithLlm(llmInstance, sample.userInput, maxRetries)
                val desiredOutcome = inferred?.userGoal.orEmpty()
                val endState = inferred?.endState.orEmpty()
                if (desiredOutcome.isNotBlank() && endState.isNotBlank()) {
                    val verdict = compareOutcomesWithLlm(llmInstance, desiredOutcome, endState, maxRetries)
                    if (verdict != null) {
                        return verdict.toDouble()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to heuristic mode for parity-safe behavior.
            }
        }
        val desiredOutcome = inferDesiredOutcome(sample.userInput)
        if (desiredOutcome.isBlank()) {
            return 0.0
        }
        val endState = inferEndState(sample.userInput)
        return if (isGoalAchieved(desiredOutcome, endState)) 1.0 else 0.0
    }
}

/**
 * Implements [AgentWorkflowCompletionMetric].
 */
class AgentWorkflowCompletionMetric(
    name: String = "agent_workflow_completion",
    private val maxRetries: Int = 3,
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    MultiTurnMetric,
    MetricWithLlm {
    override var llm: BaseRagasLlm? = null

    override suspend fun init(runConfig: RunConfig) {
        validateRequiredColumns()
        llm?.runConfig = runConfig
    }

    /**
     * Executes multiTurnAscore.
     * @param sample Evaluation sample to score.
     */
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val messages = sample.userInput
        if (messages.isEmpty()) {
            return 0.0
        }

        val llmInstance = llm
        if (llmInstance != null) {
            try {
                val llmScore = workflowCompletionWithLlm(llmInstance, messages, maxRetries)
                if (llmScore != null) {
                    return llmScore
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to heuristic mode for parity-safe behavior.
            }
        }

        val aiMessages = messages.filterIsInstance<AiMessage>()
        val humanMessages = messages.filterIsInstance<HumanMessage>()
        val toolMessages = messages.filterIsInstance<ToolMessage>()
        val predictedToolCallCount = aiMessages.sumOf { ai -> ai.toolCalls.orEmpty().size }
        val finalAiMessage = messages.lastOrNull() as? AiMessage
        val finalAiPresent = finalAiMessage?.content?.isNotBlank() == true
        val refusalPenalty = if (isFailureOrRefusal(finalAiMessage?.content.orEmpty())) 0.2 else 1.0

        val score =
            if (predictedToolCallCount == 0) {
                when {
                    finalAiPresent -> 1.0 * refusalPenalty
                    aiMessages.isNotEmpty() -> 0.7 * refusalPenalty
                    else -> 0.0
                }
            } else {
                val toolExecutionCoverage =
                    minOf(toolMessages.size.toDouble() / predictedToolCallCount.toDouble(), 1.0)
                val finalResponseScore = if (finalAiPresent) 1.0 else 0.0
                val turnBalanceScore =
                    if (humanMessages.isEmpty()) {
                        0.0
                    } else {
                        minOf(aiMessages.size.toDouble() / humanMessages.size.toDouble(), 1.0)
                    }
                ((0.6 * toolExecutionCoverage) + (0.3 * finalResponseScore) + (0.1 * turnBalanceScore)) * refusalPenalty
            }

        return clamp01(score)
    }
}

private data class GoalOutcome(
    val userGoal: String,
    val endState: String,
)

private suspend fun inferGoalOutcomeWithLlm(
    llmInstance: BaseRagasLlm,
    messages: List<ConversationMessage>,
    maxRetries: Int,
): GoalOutcome? {
    val conversation = messages.toPrettyConversation()
    if (conversation.isBlank()) {
        return null
    }

    repeat(maxRetries.coerceAtLeast(1)) {
        try {
            val raw =
                llmInstance
                    .generateText(prompt = inferGoalOutcomePrompt(conversation))
                    .generations
                    .firstOrNull()
                    ?.text
                    .orEmpty()
            val parsed = LlmJsonSupport.parseFirstJsonObject(raw) ?: return@repeat
            val userGoal = (parsed["user_goal"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val endState = (parsed["end_state"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (endState.isNotBlank()) {
                return GoalOutcome(userGoal = userGoal, endState = endState)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retry parse/generation failures to mirror other metric LLM paths.
        }
    }

    return null
}

private suspend fun compareOutcomesWithLlm(
    llmInstance: BaseRagasLlm,
    desiredOutcome: String,
    arrivedOutcome: String,
    maxRetries: Int,
): Int? {
    if (desiredOutcome.isBlank() || arrivedOutcome.isBlank()) {
        return null
    }

    repeat(maxRetries.coerceAtLeast(1)) {
        try {
            val raw =
                llmInstance
                    .generateText(prompt = compareOutcomePrompt(desiredOutcome, arrivedOutcome))
                    .generations
                    .firstOrNull()
                    ?.text
                    .orEmpty()
            val parsed = LlmJsonSupport.parseFirstJsonObject(raw) ?: return@repeat
            val verdict = LlmJsonSupport.readIntLike(parsed, "verdict") ?: return@repeat
            if (verdict == 0 || verdict == 1) {
                return verdict
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retry parse/generation failures to mirror other metric LLM paths.
        }
    }

    return null
}

private suspend fun workflowCompletionWithLlm(
    llmInstance: BaseRagasLlm,
    messages: List<ConversationMessage>,
    maxRetries: Int,
): Double? {
    val conversation = messages.toPrettyConversation()
    if (conversation.isBlank()) {
        return null
    }

    repeat(maxRetries.coerceAtLeast(1)) {
        try {
            val raw =
                llmInstance
                    .generateText(prompt = workflowCompletionPrompt(conversation))
                    .generations
                    .firstOrNull()
                    ?.text
                    .orEmpty()
            val parsed = LlmJsonSupport.parseFirstJsonObject(raw) ?: return@repeat
            val completionScore =
                readDoubleLike(parsed["completion_score"] as? JsonPrimitive) ?: return@repeat
            return clamp01(completionScore)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retry parse/generation failures to mirror other metric LLM paths.
        }
    }

    return null
}

private fun List<ConversationMessage>.toPrettyConversation(): String = joinToString(separator = "\n") { message -> message.prettyRepr() }

private fun readDoubleLike(primitive: JsonPrimitive?): Double? {
    primitive ?: return null
    primitive.doubleOrNull?.takeIf(Double::isFinite)?.let { return it }
    return primitive.content
        .trim()
        .toDoubleOrNull()
        ?.takeIf(Double::isFinite)
}

private fun inferGoalOutcomePrompt(conversation: String): String =
    buildString {
        appendLine(
            "Given an agentic workflow comprised of Human, AI and Tools, identify the user_goal " +
                "(the task or objective the user wants to achieve) and the end_state (the final outcome or result of the workflow).",
        )
        appendLine("Return JSON only with this shape:")
        appendLine("{\"user_goal\":\"...\",\"end_state\":\"...\"}")
        appendLine()
        appendLine("Workflow:")
        appendLine(JsonPrimitive(conversation).toString())
        append("Output:")
    }

private fun compareOutcomePrompt(
    desiredOutcome: String,
    arrivedOutcome: String,
): String =
    buildString {
        appendLine(
            "Given user goal, desired outcome and achieved outcome compare them and identify if they are the same (1) or different (0).",
        )
        appendLine("Return JSON only with this shape:")
        appendLine("{\"reason\":\"...\",\"verdict\":0}")
        appendLine()
        appendLine("Input:")
        appendLine(
            "{\"desired_outcome\":${JsonPrimitive(desiredOutcome)},\"arrived_outcome\":${JsonPrimitive(arrivedOutcome)}}",
        )
        append("Output:")
    }

private fun workflowCompletionPrompt(conversation: String): String =
    buildString {
        appendLine(
            "Evaluate how completely the agent workflow fulfilled the user request. " +
                "Return a continuous completion score from 0.0 to 1.0.",
        )
        appendLine("Use these anchors:")
        appendLine("- 1.0: goal completed with clear final outcome")
        appendLine("- 0.6-0.9: substantial progress but partially incomplete")
        appendLine("- 0.2-0.5: limited progress or unresolved workflow")
        appendLine("- 0.0-0.2: refusal, hard failure, or no meaningful progress")
        appendLine("Return JSON only with this shape:")
        appendLine("{\"completion_score\":0.0,\"reason\":\"...\"}")
        appendLine()
        appendLine("Workflow:")
        appendLine(JsonPrimitive(conversation).toString())
        append("Output:")
    }

private fun inferDesiredOutcome(messages: List<ConversationMessage>): String {
    val humanContents =
        messages
            .filterIsInstance<HumanMessage>()
            .map { msg -> msg.content.trim() }
            .filter { text -> text.isNotBlank() }
    if (humanContents.isEmpty()) {
        return ""
    }

    val actionableMessage =
        humanContents.firstOrNull { text ->
            ACKNOWLEDGEMENT_PATTERNS.none { regex -> regex.containsMatchIn(text) }
        }

    return actionableMessage ?: humanContents.first()
}

private fun inferEndState(messages: List<ConversationMessage>): String {
    val lastAi =
        messages
            .asReversed()
            .filterIsInstance<AiMessage>()
            .firstOrNull { it.content.isNotBlank() }
            ?.content
            .orEmpty()
            .trim()
    if (lastAi.isNotBlank()) {
        return lastAi
    }
    return messages
        .asReversed()
        .filterIsInstance<ToolMessage>()
        .firstOrNull { it.content.isNotBlank() }
        ?.content
        .orEmpty()
        .trim()
}

private fun isGoalAchieved(
    desiredOutcome: String,
    arrivedOutcome: String,
): Boolean {
    if (desiredOutcome.isBlank() || arrivedOutcome.isBlank()) {
        return false
    }
    if (isFailureOrRefusal(arrivedOutcome)) {
        return false
    }

    val desiredTokens = normalizeGoalTokens(desiredOutcome)
    val arrivedTokens = normalizeGoalTokens(arrivedOutcome)
    if (desiredTokens.isEmpty() || arrivedTokens.isEmpty()) {
        return false
    }

    val overlap = desiredTokens.intersect(arrivedTokens)
    if (overlap.isEmpty()) {
        return false
    }

    val recall = overlap.size.toDouble() / desiredTokens.size.toDouble()
    val precision = overlap.size.toDouble() / arrivedTokens.size.toDouble()
    val jaccard = overlap.size.toDouble() / desiredTokens.union(arrivedTokens).size.toDouble()

    return recall >= MIN_RECALL_THRESHOLD ||
        (recall >= MIN_COMBINED_THRESHOLD && precision >= MIN_COMBINED_THRESHOLD) ||
        jaccard >= MIN_JACCARD_THRESHOLD
}

private const val MIN_RECALL_THRESHOLD = 0.5
private const val MIN_COMBINED_THRESHOLD = 0.4
private const val MIN_JACCARD_THRESHOLD = 0.33

private fun normalizeGoalTokens(text: String): Set<String> =
    tokenize(text)
        .map { token -> token.lowercase() }
        .filter { token -> token.length >= 3 && token !in GOAL_STOP_WORDS }
        .toSet()

private fun isFailureOrRefusal(text: String): Boolean {
    val normalized = text.lowercase()
    return FAILURE_OR_REFUSAL_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }
}

private val ACKNOWLEDGEMENT_PATTERNS =
    listOf(
        Regex("^thanks[.! ]*$", RegexOption.IGNORE_CASE),
        Regex("^thank\\s+you[.! ]*$", RegexOption.IGNORE_CASE),
        Regex("^ok(?:ay)?[.! ]*$", RegexOption.IGNORE_CASE),
        Regex("^great[.! ]*$", RegexOption.IGNORE_CASE),
        Regex("^sounds\\s+good[.! ]*$", RegexOption.IGNORE_CASE),
    )

private val FAILURE_OR_REFUSAL_PATTERNS =
    listOf(
        Regex("\\b(can'?t|cannot|won'?t|unable|refuse|decline|sorry)\\b"),
        Regex("\\b(failed|failure|error|unavailable|not\\s+found|did\\s+not|could\\s+not|couldn't)\\b"),
    )

private val GOAL_STOP_WORDS =
    setOf(
        "the",
        "and",
        "for",
        "with",
        "that",
        "this",
        "you",
        "your",
        "from",
        "into",
        "about",
        "please",
        "want",
        "need",
        "have",
        "has",
        "had",
        "would",
        "could",
        "should",
        "will",
        "shall",
        "just",
        "then",
        "than",
        "also",
    )
