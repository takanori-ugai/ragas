<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Gemini Integration

```kotlin
// @compile
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel
import ragas.llms.LangChain4jLlm
import ragas.runtime.RunConfig

val llm =
    LangChain4jLlm(
        model =
            GoogleGenAiChatModel
                .builder()
                .apiKey(System.getenv("GOOGLE_API_KEY") ?: error("GOOGLE_API_KEY is required"))
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .build(),
        runConfig = RunConfig(timeoutSeconds = 90),
    )
```
