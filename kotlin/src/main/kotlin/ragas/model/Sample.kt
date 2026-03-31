package ragas.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface Sample {
    fun toMap(): Map<String, Any?>
}

@Serializable
data class SingleTurnSample(
    val userInput: String? = null,
    val retrievedContexts: List<String>? = null,
    val referenceContexts: List<String>? = null,
    val retrievedContextIds: List<String>? = null,
    val referenceContextIds: List<String>? = null,
    val response: String? = null,
    val multiResponses: List<String>? = null,
    val reference: String? = null,
    val rubrics: Map<String, String>? = null,
    val personaName: String? = null,
    val queryStyle: String? = null,
    val queryLength: String? = null,
) : Sample {
    override fun toMap(): Map<String, Any?> =
        mapOf(
            "user_input" to userInput,
            "retrieved_contexts" to retrievedContexts,
            "reference_contexts" to referenceContexts,
            "retrieved_context_ids" to retrievedContextIds,
            "reference_context_ids" to referenceContextIds,
            "response" to response,
            "multi_responses" to multiResponses,
            "reference" to reference,
            "rubrics" to rubrics,
            "persona_name" to personaName,
            "query_style" to queryStyle,
            "query_length" to queryLength,
        ).filterValues { it != null }
}

@Serializable
data class MultiTurnSample(
    val userInput: List<ConversationMessage>,
    val reference: String? = null,
    val referenceToolCalls: List<ToolCall>? = null,
    val rubrics: Map<String, String>? = null,
    val referenceTopics: List<String>? = null,
) : Sample {
    init {
        validateUserInput(userInput)
    }

    fun toMessages(): List<Map<String, Any?>> =
        userInput.map { message ->
            when (message) {
                is HumanMessage -> {
                    mapOf(
                        "type" to "human",
                        "content" to message.content,
                        "metadata" to message.metadata,
                    )
                }

                is ToolMessage -> {
                    mapOf(
                        "type" to "tool",
                        "content" to message.content,
                        "metadata" to message.metadata,
                    )
                }

                is AiMessage -> {
                    val content: Any =
                        if (message.toolCalls.isNullOrEmpty()) {
                            message.content
                        } else {
                            mapOf(
                                "text" to message.content,
                                "tool_calls" to message.toolCalls,
                            )
                        }
                    mapOf(
                        "type" to "ai",
                        "content" to content,
                        "metadata" to message.metadata,
                    )
                }
            }
        }

    fun prettyRepr(): String = userInput.joinToString(separator = "\n") { it.prettyRepr() }

    override fun toMap(): Map<String, Any?> =
        mapOf(
            "user_input" to toMessages(),
            "reference" to reference,
            "reference_tool_calls" to referenceToolCalls,
            "rubrics" to rubrics,
            "reference_topics" to referenceTopics,
        ).filterValues { it != null }

    private fun validateUserInput(messages: List<ConversationMessage>) {
        var hasSeenAiMessage = false

        messages.forEachIndexed { index, message ->
            when (message) {
                is AiMessage -> {
                    hasSeenAiMessage = true
                }

                is ToolMessage -> {
                    require(hasSeenAiMessage) {
                        "ToolMessage must be preceded by an AiMessage somewhere in the conversation."
                    }

                    if (index > 0) {
                        val previousMessage = messages[index - 1]
                        when (previousMessage) {
                            is AiMessage -> {
                                require(!previousMessage.toolCalls.isNullOrEmpty()) {
                                    "ToolMessage must follow an AiMessage where tools were called."
                                }
                            }

                            is ToolMessage -> {
                                Unit
                            }

                            is HumanMessage -> {
                                error("ToolMessage must follow an AiMessage or another ToolMessage.")
                            }
                        }
                    }
                }

                is HumanMessage -> {
                    Unit
                }
            }
        }
    }
}
