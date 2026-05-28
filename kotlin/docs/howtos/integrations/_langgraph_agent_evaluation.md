<!-- Adapted for ragas-kotlin on 2026-05-28 -->
# Building and Evaluating a ReAct Agent for Fetching Metal Prices

AI agents are increasingly useful in finance, e-commerce, and support workflows. They can call tools, retrieve real-time data, and complete user goals. Evaluating these agents is essential to keep behavior accurate and reliable.

In this guide, we:

1. Build a ReAct-style metal-price agent workflow.
2. Convert agent traces into ragas-compatible messages.
3. Evaluate tool use and goal completion with ragas metrics.

## Prerequisites

- Kotlin/JVM project setup
- Basic understanding of agent workflows and tool calling
- An LLM provider key (for example, OpenAI)

## Installing Dependencies

Use Gradle dependencies already used in this repo (LangChain4j + ragas-kotlin). For execution, run with:

```sh
./gradlew execute -PmainClass=ragas.examples.agent.AgentAppKt
```

## Building the ReAct Agent

### Initializing External Components

You can either:

1. Use a live API (for example [metals.dev](https://metals.dev/)).
2. Use a predefined local map to simulate API responses.

### Predefined JSON Object to Simulate API Response

```kotlin
val metalPrice =
    mapOf(
        "gold" to 88.1553,
        "silver" to 1.0523,
        "platinum" to 32.169,
        "palladium" to 35.8252,
        "copper" to 0.0098,
        "aluminum" to 0.0026,
        "lead" to 0.0021,
        "nickel" to 0.0159,
        "zinc" to 0.0031,
    )
```

### Define the `getMetalPrice` Tool

```kotlin
fun getMetalPrice(metalName: String): Double {
    val key = metalName.lowercase().trim()
    return metalPrice[key] ?: error("Metal '$key' not found")
}
```

### Bind the Tool to the LLM

In a ReAct workflow, the assistant decides whether to call a tool or answer directly. In Kotlin, this is typically wired through your agent framework/runtime.

```kotlin
data class ToolSpec(
    val name: String,
    val description: String,
)

val tools =
    listOf(
        ToolSpec(
            name = "getMetalPrice",
            description = "Fetches the current per-gram metal price.",
        ),
    )
```

### Define Workflow State

A graph state tracks message history across assistant and tool nodes.

```kotlin
data class GraphState(
    val messages: MutableList<Map<String, Any?>> = mutableListOf(),
)
```

### Define `shouldContinue`

```kotlin
fun shouldContinue(state: GraphState): String {
    val last = state.messages.lastOrNull() ?: return "end"
    val toolCalls = last["tool_calls"] as? List<*>
    return if (!toolCalls.isNullOrEmpty()) "tools" else "end"
}
```

### Assistant and Tool Nodes

```kotlin
fun assistant(state: GraphState): GraphState {
    // Replace with your LLM call and tool-call planning.
    return state
}

fun toolNode(state: GraphState): GraphState {
    // Execute tool calls (for example getMetalPrice) and append tool messages.
    return state
}
```

### Build and Run the Graph

```kotlin
val state = GraphState(messages = mutableListOf(mapOf("role" to "user", "content" to "What is the price of copper?")))
val afterAssistant = assistant(state)
val finalState = if (shouldContinue(afterAssistant) == "tools") toolNode(afterAssistant) else afterAssistant
println(finalState.messages)
```

## Converting Messages to Ragas Evaluation Format

Ragas needs conversation messages in its own schema. For LangGraph-like traces, use `LangGraphIntegration.convertToRagasMessages(...)`.

```kotlin
// @compile
import ragas.integrations.LangGraphIntegration

val rawMessages =
    listOf(
        mapOf("role" to "user", "content" to "What is the price of copper?"),
        mapOf(
            "role" to "assistant",
            "content" to "",
            "tool_calls" to
                listOf(
                    mapOf(
                        "name" to "get_metal_price",
                        "args" to mapOf("metal_name" to "copper"),
                    ),
                ),
        ),
        mapOf("role" to "tool", "content" to "0.0098"),
        mapOf("role" to "assistant", "content" to "The price of copper is $0.0098 per gram."),
    )

val ragasMessages = LangGraphIntegration.convertToRagasMessages(rawMessages)
println(ragasMessages)
```

## Evaluating the Agent's Performance

As in the Python tutorial, two key metrics are:

- Tool call accuracy
- Agent goal accuracy

### Tool Call Accuracy

```kotlin
import kotlinx.serialization.json.JsonPrimitive
import ragas.metrics.collections.ToolCallAccuracyMetric
import ragas.model.AiMessage
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.ToolCall

val ragasMessages: List<ConversationMessage> =
    listOf(
        HumanMessage(content = "What is the price of copper?"),
        AiMessage(content = "", toolCalls = listOf(ToolCall(name = "get_metal_price", args = mapOf("metal_name" to JsonPrimitive("copper"))))),
        AiMessage(content = "The price of copper is $0.0098 per gram."),
    )

val sample =
    MultiTurnSample(
        userInput = ragasMessages,
        referenceToolCalls = listOf(ToolCall(name = "get_metal_price", args = mapOf("metal_name" to JsonPrimitive("copper")))),
    )

val toolCallMetric = ToolCallAccuracyMetric()
// Score with evaluate(...) over an EvaluationDataset<MultiTurnSample> in your experiment flow.
```

### Agent Goal Accuracy

```kotlin
import ragas.model.ConversationMessage
import ragas.model.HumanMessage
import ragas.metrics.collections.AgentGoalAccuracyWithReferenceMetric
import ragas.model.MultiTurnSample

val ragasMessages: List<ConversationMessage> = listOf(HumanMessage(content = "What is the price of copper?"))

val goalSample =
    MultiTurnSample(
        userInput = ragasMessages,
        reference = "Return the current price of copper per gram.",
    )

val goalMetric = AgentGoalAccuracyWithReferenceMetric()
// Score with evaluate(...) over an EvaluationDataset<MultiTurnSample> in your experiment flow.
```

## Optional: Evaluate LangGraph Records Directly

If you already have flattened `input`/`output` records, use the integration adapter:

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LangGraphIntegration
import ragas.integrations.LangGraphRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records = listOf(LangGraphRecord(input = "What is Ragas?", output = "Ragas is an eval framework."))
val result = LangGraphIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
