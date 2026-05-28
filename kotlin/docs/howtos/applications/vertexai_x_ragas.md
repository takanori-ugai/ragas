<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Vertex AI x Ragas

Use Vertex-backed chat models through LangChain4j and evaluate with Ragas Kotlin.

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

```kotlin
import ragas.evaluate
import ragas.defaultMetrics
import ragas.model.EvaluationDataset
import ragas.model.Sample

val dataset: EvaluationDataset<out Sample> = TODO("Provide evaluation dataset")
val result = evaluate(dataset = dataset, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```
