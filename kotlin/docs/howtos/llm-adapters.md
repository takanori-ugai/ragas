<!-- Adapted for ragas-kotlin on 2026-05-27 -->
> [!NOTE]
> This page was adapted from `../docs/howtos/llm-adapters.md` for the Kotlin port (`ragas-kotlin`).
> Kotlin does not expose Python's `llm_factory(..., adapter=...)` API. Use provider chat models via `LangChain4jLlm`.

# LLM Adapters: Using Multiple Structured Output Backends

Ragas Kotlin uses provider-specific LangChain4j chat models wrapped by `LangChain4jLlm`.

## Overview

In Kotlin, the practical "adapter" is:

- Provider chat model (`OpenAiChatModel`, `GoogleGenAiChatModel`, `OllamaChatModel`, etc.)
- Wrapped as `LangChain4jLlm`
- Passed into metrics/evaluation APIs as `BaseRagasLlm`

```kotlin
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

fun wrap(model: dev.langchain4j.model.chat.ChatModel): BaseRagasLlm =
    LangChain4jLlm(model = model, runConfig = RunConfig(timeoutSeconds = 90))
```

## Quick Start

### OpenAI

```kotlin
import dev.langchain4j.model.openai.OpenAiChatModel
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

fun createOpenAiLlm(modelName: String = "gpt-5.4-mini"): BaseRagasLlm {
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("OPENAI_API_KEY is required")

    val chatModel =
        OpenAiChatModel
            .builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.0)
            .build()

    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}
```

### Gemini

```kotlin
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

fun createGeminiLlm(modelName: String = "gemma-4-31b-it"): BaseRagasLlm {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: System.getenv("GOOGLE_API_KEY")
        ?: error("GEMINI_API_KEY or GOOGLE_API_KEY is required")

    val chatModel =
        GoogleGenAiChatModel
            .builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.0)
            .build()

    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}
```

### Ollama

```kotlin
import dev.langchain4j.model.ollama.OllamaChatModel
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

fun createOllamaLlm(
    modelName: String = "llama3.2",
    baseUrl: String = "http://localhost:11434",
): BaseRagasLlm {
    val chatModel =
        OllamaChatModel
            .builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(0.0)
            .build()

    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}
```

## Simple Provider Selector

Kotlin does not have Python's `auto_detect_adapter(...)` API. Use an explicit provider selector:

```kotlin
import ragas.llms.BaseRagasLlm

fun createLlm(provider: String, modelName: String? = null): BaseRagasLlm =
    when (provider.lowercase()) {
        "openai" -> createOpenAiLlm(modelName ?: "gpt-5.4-mini")
        "gemini", "google" -> createGeminiLlm(modelName ?: "gemma-4-31b-it")
        "ollama" -> createOllamaLlm(modelName ?: "llama3.2")
        else -> error("Unsupported provider: $provider")
    }
```

## Provider-Specific Examples

### OpenAI-Compatible Endpoints (LiteLLM proxy, local gateway, etc.)

```kotlin
import dev.langchain4j.model.openai.OpenAiChatModel
import ragas.llms.BaseRagasLlm
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

fun createOpenAiCompatibleLlm(
    baseUrl: String,
    apiKey: String,
    modelName: String,
): BaseRagasLlm {
    val chatModel =
        OpenAiChatModel
            .builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.0)
            .build()

    return LangChain4jLlm(model = chatModel, runConfig = RunConfig(timeoutSeconds = 90))
}

// Example usage:
// val llm = createOpenAiCompatibleLlm(
//     baseUrl = "http://0.0.0.0:4000",
//     apiKey = "anything",
//     modelName = "gemini-2.0-flash",
// )
```

## Working with Structured Output

`LangChain4jLlm` implements `StructuredOutputRagasLlm`, which metrics use when available.

```kotlin
import kotlinx.coroutines.runBlocking
import ragas.llms.StructuredOutputRagasLlm

fun main() = runBlocking {
    val llm = createOpenAiLlm()
    val structured = llm as? StructuredOutputRagasLlm
        ?: error("Selected LLM does not support structured output")

    val verdict = structured.generateDiscreteValue(
        "Return only one token: pass or fail."
    )
    println("verdict=$verdict")
}
```

## Complete Evaluation Example

```kotlin
import ragas.evaluate
import ragas.llms.BaseRagasLlm
import ragas.metrics.collections.AnswerCorrectnessMetric
import ragas.metrics.defaults.ContextPrecisionMetric
import ragas.metrics.defaults.ContextRecallMetric
import ragas.metrics.defaults.FaithfulnessMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample

fun runEvaluation(llm: BaseRagasLlm) {
    val dataset =
        EvaluationDataset(
            samples =
                listOf(
                    SingleTurnSample(
                        userInput = "What is the capital of France?",
                        response = "Paris",
                        reference = "Paris",
                        retrievedContexts = listOf("France is in Europe. Paris is its capital."),
                    ),
                ),
        )

    val metrics =
        listOf(
            ContextPrecisionMetric(),
            ContextRecallMetric().apply { this.llm = llm },
            FaithfulnessMetric().apply { this.llm = llm },
            AnswerCorrectnessMetric().apply { this.llm = llm },
        )

    val results = evaluate(dataset = dataset, metrics = metrics)
    println(results.scores)
}
```

## Troubleshooting

### Missing API key

```kotlin
val apiKey = System.getenv("OPENAI_API_KEY")
require(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY must be set" }
```

### Unsupported provider value

```kotlin
val llm = createLlm(provider = "openai") // openai | gemini | ollama
```

### Structured output not available

```kotlin
import ragas.llms.StructuredOutputRagasLlm

val structured = llm as? StructuredOutputRagasLlm
if (structured == null) {
    error("Use a structured-output capable model wrapper (for example LangChain4jLlm)")
}
```

## Migration Guide

### Python factory style -> Kotlin provider constructors

```kotlin
// Python-style (reference)
// llm = llm_factory("gpt-4o-mini", client=client)

// Kotlin-style
val llm = createOpenAiLlm(modelName = "gpt-5.4-mini")
```

### Switch provider with minimal changes

```kotlin
val openAi = createOpenAiLlm("gpt-5.4-mini")
val gemini = createGeminiLlm("gemma-4-31b-it")
val ollama = createOllamaLlm("llama3.2")
```

## See Also

- [LLMs Reference](../references/llms.md)
- [evaluate()](../references/evaluate.md)
- [Gemini Integration Guide](./integrations/gemini.md)
