package ragas.integrations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ragas.backends.anyToJsonElement
import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluate
import ragas.integrations.tracing.TraceObserver
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.SingleTurnSample
import ragas.model.ToolCall
import ragas.model.ToolMessage
import ragas.runtime.RunConfig

/**
 * Input record schema for LangGraph integration adapters.
 *
 * @property input User prompt text.
 * @property output Model output text.
 * @property retrievedContexts Retrieved context strings.
 * @property referenceContexts Optional reference context strings.
 * @property reference Optional reference answer.
 * @property metadata Optional record metadata.
 */
data class LangGraphRecord(
    val input: String,
    val output: String,
    val retrievedContexts: List<String> = emptyList(),
    val referenceContexts: List<String> = emptyList(),
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Helper functions for evaluating LangGraph records with ragas metrics.
 */
object LangGraphIntegration {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Converts integration records into an evaluation dataset.
     *
     * @param records Integration records to process.
     */
    fun toDataset(records: List<LangGraphRecord>): EvaluationDataset<SingleTurnSample> =
        EvaluationDataset(
            records.map { record ->
                SingleTurnSample(
                    userInput = record.input,
                    response = record.output,
                    retrievedContexts = record.retrievedContexts,
                    referenceContexts = record.referenceContexts.ifEmpty { null },
                    reference = record.reference,
                )
            },
        )

    /**
     * Evaluates integration records with the selected metrics and model dependencies.
     *
     * @param records Integration records to process.
     * @param metrics Metrics to run.
     * @param llm LLM dependency used during generation/evaluation.
     * @param embeddings Embedding dependency used during evaluation.
     * @param runConfig Runtime retry/concurrency configuration.
     * @param raiseExceptions Whether metric failures should be thrown.
     * @param runName Logical run name used in tracing output.
     * @param tags Run-level tags.
     * @param metadata Run-level metadata.
     * @param observers Trace observers notified during execution.
     */
    fun evaluateRecords(
        records: List<LangGraphRecord>,
        metrics: List<Metric>? = null,
        llm: BaseRagasLlm? = null,
        embeddings: BaseRagasEmbedding? = null,
        runConfig: RunConfig = RunConfig(),
        raiseExceptions: Boolean = false,
        runName: String = "ragas-langgraph-evaluation",
        tags: Map<String, String> = emptyMap(),
        metadata: Map<String, String> = emptyMap(),
        observers: List<TraceObserver> = emptyList(),
    ): EvaluationResult =
        traceEvaluation(
            framework = "langgraph",
            runName = runName,
            tags = tags,
            metadata = metadata,
            observers = observers,
        ) {
            evaluate(
                dataset = toDataset(records),
                metrics = metrics,
                llm = llm,
                embeddings = embeddings,
                runConfig = runConfig,
                raiseExceptions = raiseExceptions,
            )
        }

    /**
     * Converts evaluation scores into integration-friendly metric rows.
     *
     * @param result Evaluation result payload.
     */
    fun toMetricPayload(result: EvaluationResult): List<Map<String, Any?>> = result.scores

    /**
     * Converts LangGraph-like message maps to ragas conversation messages.
     *
     * Message role/type mapping:
     * - `"user"`/`"human"` -> [HumanMessage]
     * - `"assistant"`/`"ai"` -> [AiMessage]
     * - `"tool"` -> [ToolMessage]
     * - `"system"` -> skipped
     *
     * @param messages Raw message maps.
     * @param includeMetadata Whether non-core message fields should be copied into metadata.
     */
    fun convertToRagasMessages(
        messages: List<Map<String, Any?>>,
        includeMetadata: Boolean = false,
    ): List<ConversationMessage> =
        messages.mapIndexedNotNull { index, message ->
            val role = resolveMessageRole(message, index)
            val metadata = extractMetadata(message, includeMetadata)
            when (role) {
                "system" -> {
                    null
                }

                "user", "human" -> {
                    HumanMessage(content = requireStringContent(message, index, role), metadata = metadata)
                }

                "assistant", "ai" -> {
                    AiMessage(
                        content = requireStringContent(message, index, role),
                        toolCalls = extractToolCalls(message, index),
                        metadata = metadata,
                    )
                }

                "tool" -> {
                    ToolMessage(content = requireStringContent(message, index, role), metadata = metadata)
                }

                else -> {
                    throw IllegalArgumentException("Unsupported message role/type at messages[$index]: '$role'")
                }
            }
        }

    /**
     * Builds a [MultiTurnSample] from LangGraph-like message maps.
     *
     * @param messages Raw message maps.
     * @param reference Optional reference answer/outcome.
     * @param referenceToolCalls Optional reference tool calls for agent metrics.
     * @param rubrics Optional rubric labels.
     * @param referenceTopics Optional expected topics.
     */
    fun toMultiTurnSample(
        messages: List<Map<String, Any?>>,
        reference: String? = null,
        referenceToolCalls: List<ToolCall>? = null,
        rubrics: Map<String, String>? = null,
        referenceTopics: List<String>? = null,
    ): MultiTurnSample =
        MultiTurnSample(
            userInput = convertToRagasMessages(messages),
            reference = reference,
            referenceToolCalls = referenceToolCalls,
            rubrics = rubrics,
            referenceTopics = referenceTopics,
        )

    private fun resolveMessageRole(
        message: Map<String, Any?>,
        index: Int,
    ): String {
        val role =
            message["type"]?.toString()?.trim()?.lowercase()
                ?: message["role"]?.toString()?.trim()?.lowercase()
        return role ?: throw IllegalArgumentException("messages[$index] must include either 'type' or 'role'")
    }

    private fun requireStringContent(
        message: Map<String, Any?>,
        index: Int,
        role: String,
    ): String {
        val content = message["content"]
        return content as? String
            ?: throw IllegalArgumentException(
                "messages[$index] with role/type '$role' requires string 'content', got ${content?.javaClass?.simpleName ?: "null"}",
            )
    }

    private fun extractToolCalls(
        message: Map<String, Any?>,
        messageIndex: Int,
    ): List<ToolCall>? {
        val additionalKwargs = message["additional_kwargs"] as? Map<*, *>
        val source = message["tool_calls"] ?: additionalKwargs?.get("tool_calls") ?: return null
        val list =
            source as? List<*>
                ?: throw IllegalArgumentException("messages[$messageIndex].tool_calls must be a list when present")
        return list.mapIndexed { callIndex, call ->
            parseToolCall(call, messageIndex, callIndex)
        }
    }

    private fun parseToolCall(
        rawToolCall: Any?,
        messageIndex: Int,
        callIndex: Int,
    ): ToolCall {
        val toolCall =
            rawToolCall as? Map<*, *>
                ?: throw IllegalArgumentException("messages[$messageIndex].tool_calls[$callIndex] must be an object")
        val function = toolCall["function"] as? Map<*, *>
        val name =
            toolCall["name"]?.toString()?.trim().orEmpty().ifBlank {
                function
                    ?.get("name")
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            }
        require(name.isNotEmpty()) {
            "messages[$messageIndex].tool_calls[$callIndex] is missing function name"
        }

        val argsSource =
            when {
                toolCall.containsKey("args") -> toolCall["args"]
                function?.containsKey("arguments") == true -> function["arguments"]
                function?.containsKey("args") == true -> function["args"]
                else -> null
            }

        return ToolCall(
            name = name,
            args = parseToolCallArgs(argsSource, messageIndex, callIndex),
        )
    }

    private fun parseToolCallArgs(
        argsSource: Any?,
        messageIndex: Int,
        callIndex: Int,
    ): Map<String, JsonElement> =
        when (argsSource) {
            null -> {
                emptyMap()
            }

            is JsonObject -> {
                argsSource.toMap()
            }

            is Map<*, *> -> {
                argsSource.entries.associate { (key, value) ->
                    key.toString() to anyToJsonElement(value)
                }
            }

            is String -> {
                val parsed =
                    runCatching { json.parseToJsonElement(argsSource) }.getOrElse { error ->
                        throw IllegalArgumentException(
                            "messages[$messageIndex].tool_calls[$callIndex] has invalid JSON arguments",
                            error,
                        )
                    }
                val jsonObject =
                    parsed as? JsonObject
                        ?: throw IllegalArgumentException(
                            "messages[$messageIndex].tool_calls[$callIndex] arguments must decode to a JSON object",
                        )
                jsonObject.toMap()
            }

            else -> {
                throw IllegalArgumentException(
                    "messages[$messageIndex].tool_calls[$callIndex] arguments must be a map or JSON string",
                )
            }
        }

    private fun extractMetadata(
        message: Map<String, Any?>,
        includeMetadata: Boolean,
    ): Map<String, JsonElement>? {
        if (!includeMetadata) {
            return null
        }
        val metadata =
            buildMap<String, JsonElement> {
                message.entries.forEach { (key, value) ->
                    when (key) {
                        "type", "role", "content", "tool_calls" -> {}

                        "additional_kwargs" -> {
                            val sanitized =
                                (value as? Map<*, *>)?.filterKeys { it.toString() != "tool_calls" } ?: value
                            if (sanitized is Map<*, *> && sanitized.isEmpty()) {
                                return@forEach
                            }
                            put(key, anyToJsonElement(sanitized))
                        }

                        else -> {
                            put(key, anyToJsonElement(value))
                        }
                    }
                }
            }
        return metadata.ifEmpty { null }
    }
}
