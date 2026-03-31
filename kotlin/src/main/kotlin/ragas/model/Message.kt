package ragas.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCall(
    val name: String,
    val args: Map<String, JsonElement> = emptyMap(),
)

@Serializable
sealed interface ConversationMessage {
    val content: String
    val metadata: Map<String, JsonElement>?

    fun prettyRepr(): String
}

@Serializable
data class HumanMessage(
    override val content: String,
    override val metadata: Map<String, JsonElement>? = null,
) : ConversationMessage {
    override fun prettyRepr(): String = "Human: $content"
}

@Serializable
data class ToolMessage(
    override val content: String,
    override val metadata: Map<String, JsonElement>? = null,
) : ConversationMessage {
    override fun prettyRepr(): String = "ToolOutput: $content"
}

@Serializable
data class AiMessage(
    override val content: String,
    val toolCalls: List<ToolCall>? = null,
    override val metadata: Map<String, JsonElement>? = null,
) : ConversationMessage {
    override fun prettyRepr(): String {
        val lines = mutableListOf<String>()
        if (content.isNotBlank()) {
            lines += "AI: $content"
        }
        if (!toolCalls.isNullOrEmpty()) {
            lines += "Tools:"
            toolCalls.forEach { tool ->
                lines += "  ${tool.name}: ${tool.args}"
            }
        }
        return lines.joinToString("\n")
    }
}
