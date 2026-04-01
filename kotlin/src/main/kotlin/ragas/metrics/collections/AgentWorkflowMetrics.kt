package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MultiTurnMetric
import ragas.metrics.clamp01
import ragas.metrics.tokenize
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.ToolMessage

class AgentGoalAccuracyWithReferenceMetric(
    name: String = "agent_goal_accuracy_with_reference",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input", "reference")),
        outputType = MetricOutputType.BINARY,
    ),
    MultiTurnMetric {
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val desiredOutcome = sample.reference.orEmpty().trim()
        if (desiredOutcome.isBlank()) {
            return 0.0
        }
        val endState = inferEndState(sample.userInput)
        return if (isGoalAchieved(desiredOutcome, endState)) 1.0 else 0.0
    }
}

class AgentGoalAccuracyWithoutReferenceMetric(
    name: String = "agent_goal_accuracy_without_reference",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input")),
        outputType = MetricOutputType.BINARY,
    ),
    MultiTurnMetric {
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val desiredOutcome = inferDesiredOutcome(sample.userInput)
        if (desiredOutcome.isBlank()) {
            return 0.0
        }
        val endState = inferEndState(sample.userInput)
        return if (isGoalAchieved(desiredOutcome, endState)) 1.0 else 0.0
    }
}

class AgentWorkflowCompletionMetric(
    name: String = "agent_workflow_completion",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    MultiTurnMetric {
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any {
        val messages = sample.userInput
        if (messages.isEmpty()) {
            return 0.0
        }

        val aiMessages = messages.filterIsInstance<AiMessage>()
        val desiredOutcome = inferDesiredOutcome(messages)
        val endState = inferEndState(messages)
        val toolMessages = messages.filterIsInstance<ToolMessage>()
        val predictedToolCallCount = aiMessages.sumOf { ai -> ai.toolCalls.orEmpty().size }
        val finalAiMessage = messages.lastOrNull() as? AiMessage
        val finalAiPresent = finalAiMessage?.content?.isNotBlank() == true
        val refusal = isFailureOrRefusal(finalAiMessage?.content.orEmpty())

        val goalProgress = goalProgressScore(desiredOutcome, endState)
        val executionProgress =
            workflowExecutionScore(
                messages = messages,
                predictedToolCallCount = predictedToolCallCount,
                observedToolMessageCount = toolMessages.count { tool -> tool.content.isNotBlank() },
                aiMessages = aiMessages,
            )
        val closureProgress =
            when {
                !finalAiPresent -> 0.0
                refusal -> 0.1
                else -> 1.0
            }

        var score =
            (WORKFLOW_GOAL_WEIGHT * goalProgress) +
                (WORKFLOW_EXECUTION_WEIGHT * executionProgress) +
                (WORKFLOW_CLOSURE_WEIGHT * closureProgress)

        if (refusal) {
            score *= REFUSAL_DOWNWEIGHT
            if (isLikelySafetyRefusal(desiredOutcome, endState)) {
                score = maxOf(score, SAFETY_REFUSAL_FLOOR)
            }
        }

        return clamp01(score)
    }
}

private fun goalProgressScore(
    desiredOutcome: String,
    arrivedOutcome: String,
): Double {
    if (desiredOutcome.isBlank() || arrivedOutcome.isBlank()) {
        return 0.0
    }

    val desiredTokens = normalizeGoalTokens(desiredOutcome)
    val arrivedTokens = normalizeGoalTokens(arrivedOutcome)
    if (desiredTokens.isEmpty() || arrivedTokens.isEmpty()) {
        return 0.0
    }

    val overlap = desiredTokens.intersect(arrivedTokens)
    if (overlap.isEmpty()) {
        return 0.0
    }

    val recall = overlap.size.toDouble() / desiredTokens.size.toDouble()
    val precision = overlap.size.toDouble() / arrivedTokens.size.toDouble()
    val jaccard = overlap.size.toDouble() / desiredTokens.union(arrivedTokens).size.toDouble()

    var score =
        (WORKFLOW_GOAL_RECALL_WEIGHT * recall) +
            (WORKFLOW_GOAL_PRECISION_WEIGHT * precision) +
            (WORKFLOW_GOAL_JACCARD_WEIGHT * jaccard)

    if (isFailureOrRefusal(arrivedOutcome)) {
        score *= FAILURE_GOAL_PROGRESS_DOWNWEIGHT
    }
    return clamp01(score)
}

private fun workflowExecutionScore(
    messages: List<ConversationMessage>,
    predictedToolCallCount: Int,
    observedToolMessageCount: Int,
    aiMessages: List<AiMessage>,
): Double {
    if (predictedToolCallCount <= 0) {
        return if (aiMessages.isNotEmpty()) 1.0 else 0.0
    }

    val coverage = minOf(observedToolMessageCount.toDouble() / predictedToolCallCount.toDouble(), 1.0)
    val sequencing = toolMessageSequencingScore(messages)
    val assistantPresence =
        if (aiMessages.any { ai -> ai.content.isNotBlank() || !ai.toolCalls.isNullOrEmpty() }) {
            1.0
        } else {
            0.0
        }

    return clamp01(
        (WORKFLOW_EXECUTION_COVERAGE_WEIGHT * coverage) +
            (WORKFLOW_EXECUTION_SEQUENCE_WEIGHT * sequencing) +
            (WORKFLOW_EXECUTION_ASSISTANT_WEIGHT * assistantPresence),
    )
}

private fun toolMessageSequencingScore(messages: List<ConversationMessage>): Double {
    var plannedToolCalls = 0
    var validToolOutputs = 0
    var toolOutputs = 0

    messages.forEach { message ->
        when (message) {
            is AiMessage -> {
                plannedToolCalls += message.toolCalls.orEmpty().size
            }

            is ToolMessage -> {
                toolOutputs += 1
                if (plannedToolCalls > 0 && message.content.isNotBlank()) {
                    validToolOutputs += 1
                }
            }

            is HumanMessage -> {
                // no-op
            }
        }
    }

    if (toolOutputs == 0) {
        return 0.0
    }
    return validToolOutputs.toDouble() / toolOutputs.toDouble()
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
private const val WORKFLOW_GOAL_WEIGHT = 0.6
private const val WORKFLOW_EXECUTION_WEIGHT = 0.25
private const val WORKFLOW_CLOSURE_WEIGHT = 0.15
private const val WORKFLOW_GOAL_RECALL_WEIGHT = 0.5
private const val WORKFLOW_GOAL_PRECISION_WEIGHT = 0.3
private const val WORKFLOW_GOAL_JACCARD_WEIGHT = 0.2
private const val FAILURE_GOAL_PROGRESS_DOWNWEIGHT = 0.2
private const val WORKFLOW_EXECUTION_COVERAGE_WEIGHT = 0.75
private const val WORKFLOW_EXECUTION_SEQUENCE_WEIGHT = 0.15
private const val WORKFLOW_EXECUTION_ASSISTANT_WEIGHT = 0.1
private const val REFUSAL_DOWNWEIGHT = 0.65
private const val SAFETY_REFUSAL_FLOOR = 0.25

private fun normalizeGoalTokens(text: String): Set<String> =
    tokenize(text)
        .map { token -> token.lowercase() }
        .filter { token -> token.length >= 3 && token !in GOAL_STOP_WORDS }
        .toSet()

private fun isFailureOrRefusal(text: String): Boolean {
    val normalized = text.lowercase()
    return FAILURE_OR_REFUSAL_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }
}

private fun isLikelySafetyRefusal(
    desiredOutcome: String,
    arrivedOutcome: String,
): Boolean {
    if (!isFailureOrRefusal(arrivedOutcome)) {
        return false
    }
    val normalizedGoal = desiredOutcome.lowercase()
    return SAFETY_RISK_PATTERNS.any { pattern -> pattern.containsMatchIn(normalizedGoal) }
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

private val SAFETY_RISK_PATTERNS =
    listOf(
        Regex("\\b(hack|hacking|exploit|phish|malware|ransomware|credential\\s*stuffing)\\b"),
        Regex("\\b(unauthorized|illegal|illicit|stolen|steal|fraud|bypass\\s+security)\\b"),
        Regex("\\b(bank\\s+account|credit\\s+card|social\\s+security)\\b"),
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
