<!-- Adapted for ragas-kotlin on 2026-05-27 -->
> [!NOTE]
> Kotlin quickstart programs should use Gradle entrypoints (`./gradlew execute -PmainClass=...`) and Kotlin APIs from package `ragas`.

# RAG Evaluation Quickstart

The `rag_eval` template evaluates a RAG pipeline against grading notes and stores experiment CSVs.

## Create the Project

```sh
ragas quickstart rag_eval
cd rag_eval
```

## Build

```sh
./gradlew build
```

## Set API Key

```sh
export OPENAI_API_KEY="your-openai-key"
```

## Run the Evaluation

```sh
./gradlew execute -PmainClass=ragas.examples.rageval.EvalsKt
```

## Provider Examples

### OpenAI (default)

```kotlin
import dev.langchain4j.model.openai.OpenAiChatModel
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

val llm =
    LangChain4jLlm(
        model =
            OpenAiChatModel
                .builder()
                .apiKey(System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is required"))
                .modelName("gpt-5.4-mini")
                .temperature(0.0)
                .build(),
        runConfig = RunConfig(timeoutSeconds = 90),
    )
```

### Gemini

```kotlin
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

val llm =
    LangChain4jLlm(
        model =
            GoogleGenAiChatModel
                .builder()
                .apiKey(System.getenv("GOOGLE_API_KEY") ?: error("GOOGLE_API_KEY is required"))
                .modelName("gemma-4-31b-it")
                .temperature(0.0)
                .build(),
        runConfig = RunConfig(timeoutSeconds = 90),
    )
```

### Ollama

```kotlin
import dev.langchain4j.model.ollama.OllamaChatModel
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

val llm =
    LangChain4jLlm(
        model =
            OllamaChatModel
                .builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .temperature(0.0)
                .build(),
        runConfig = RunConfig(timeoutSeconds = 90),
    )
```

## Core Evaluation Loop

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment
import ragas.llms.BaseRagasLlm
import ragas.metrics.MetricType
import ragas.metrics.primitives.DiscreteMetric
import ragas.model.SingleTurnSample

data class RagRow(val question: String, val gradingNotes: String)
interface RagPipeline {
    suspend fun answer(question: String): String
}

val llm: BaseRagasLlm = TODO("Configure LLM")
val ragPipeline: RagPipeline = TODO("Provide RAG pipeline implementation")

val metric =
    DiscreteMetric(
        name = "correctness",
        prompt = "Check if response covers grading notes. Response: {response} Notes: {reference}",
        llm = llm,
        allowedValues = listOf("pass", "fail"),
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response", "reference")),
    )

val runner =
    experiment<RagRow>(backend = LocalCsvBackend("experiments"), namePrefix = "rag-eval") { row ->
        val answer = ragPipeline.answer(row.question)
        val score =
            metric.singleTurnAscore(
                SingleTurnSample(
                    userInput = row.question,
                    response = answer,
                    reference = row.gradingNotes,
                ),
            )
        mapOf("question" to row.question, "grading_notes" to row.gradingNotes, "response" to answer, "score" to score)
    }
```

## Dataset Snippet

```kotlin
import ragas.backends.LocalCsvBackend

val dataset =
    listOf(
        RagRow("What is Ragas?", "evaluation framework; LLM applications"),
        RagRow("How do experiments work?", "track runs; compare runs; store metrics"),
    )

LocalCsvBackend("datasets").saveDataset(
    name = "test_dataset",
    data = dataset.map { mapOf("question" to it.question, "grading_notes" to it.gradingNotes) },
)
```

## Analyze Results

```kotlin
import ragas.backends.LocalCsvBackend

val rows = LocalCsvBackend(".").loadExperiment("rag-eval-baseline")
val passCount = rows.count { (it["score"] as? String)?.equals("pass", ignoreCase = true) == true }
println("pass=$passCount total=${rows.size}")
```
