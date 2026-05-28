<!-- Adapted for ragas-kotlin on 2026-05-28 -->
> [!NOTE]
> This guide is for teams moving Ragas usage from Python to Kotlin (`ragas-kotlin`).
> For current Kotlin parity status, check [PARITY_MATRIX.md](../../../PARITY_MATRIX.md) and [MIGRATION.md](../../../MIGRATION.md).

# Migration from Python to Kotlin

This guide focuses on practical API migration from Python Ragas code to Kotlin code in this repository.

## What Changes

When moving from Python to Kotlin, you should expect three major shifts:

1. **Typed data models**: use Kotlin data classes and `EvaluationDataset`/`SingleTurnSample` instead of Python dict-heavy flows.
2. **JVM LLM providers**: use LangChain4j-backed providers and Kotlin constructors/config.
3. **Project-first execution**: run examples and evaluations through Gradle tasks (`./gradlew execute -PmainClass=...`).

## Quick Mapping

| Python (typical) | Kotlin (`ragas-kotlin`) |
|---|---|
| `evaluate(dataset, metrics=...)` | `evaluate(dataset = ..., metrics = ...)` |
| `SingleTurnSample(...)` / dict rows | `SingleTurnSample(...)` in `EvaluationDataset(...)` |
| `ragas.metrics.*` names | `ragas.metrics.collections.*` metric classes |
| provider SDK + wrappers | `LangChain4jLlm(...)` with provider model |
| tokenizer defaults | `DEFAULT_TOKENIZER` (JTokkit-backed) |
| script entrypoint | `./gradlew execute -PmainClass=...` |

## 1) Replace Dataset Construction

### Python style

```python
rows = [
  {
    "user_input": "What is RAG?",
    "response": "RAG combines retrieval and generation.",
    "retrieved_contexts": ["RAG retrieves context before generation."],
    "reference": "RAG uses retrieved knowledge to ground generation."
  }
]
```

### Kotlin style

```kotlin
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample

val dataset =
    EvaluationDataset(
        listOf(
            SingleTurnSample(
                userInput = "What is RAG?",
                response = "RAG combines retrieval and generation.",
                retrievedContexts = listOf("RAG retrieves context before generation."),
                reference = "RAG uses retrieved knowledge to ground generation.",
            ),
        ),
    )
```

## 2) Replace Evaluate Calls

```kotlin
import ragas.evaluate
import ragas.metrics.collections.AnswerCorrectnessMetric
import ragas.metrics.defaults.AnswerRelevancyMetric
import ragas.metrics.defaults.FaithfulnessMetric

val metrics =
    listOf(
        FaithfulnessMetric(),
        AnswerRelevancyMetric(),
        AnswerCorrectnessMetric(),
    )

val result = evaluate(dataset = dataset, metrics = metrics)
println(result.scores)
```

Notes:

- If you omit `metrics`, Kotlin uses default single-turn metrics.
- For multi-turn datasets, use multi-turn-compatible metrics.

## 3) Replace LLM Wiring

Use provider-backed Kotlin LLMs. In this repo, OpenAI provider examples are available and use `gpt-5.4-mini`.

```kotlin
import dev.langchain4j.model.openai.OpenAiChatModel
import ragas.llms.LangChain4jLlm
import ragas.llms.LlmFeatures

val model =
    OpenAiChatModel
        .builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.4-mini")
        .build()

val llm =
    LangChain4jLlm(
        model = model,
        features = LlmFeatures.defaultWithStructuredOutput(),
    )
```

## 4) Replace Tokenizer Assumptions

Kotlin uses `DEFAULT_TOKENIZER`, backed by JTokkit.

```kotlin
import ragas.DEFAULT_TOKENIZER

val tokenCount = DEFAULT_TOKENIZER.countTokens("hello world")
println(tokenCount)
```

If your Python code relied on tokenizer internals, migrate to public tokenizer APIs and avoid provider-specific token counters where possible.

## 5) Migrate Experiment Pipelines

Python users often run ad-hoc scripts; Kotlin favors repeatable experiment programs and CSV backends.

```kotlin
import ragas.backends.LocalCsvBackend
import ragas.experiment

data class EvalRow(val question: String, val expected: String)

val runner =
    experiment<EvalRow>(backend = LocalCsvBackend("experiments"), namePrefix = "migration-eval") { row ->
        mapOf(
            "question" to row.question,
            "expected" to row.expected,
            // add model outputs + scores here
        )
    }
```

## 6) CLI/Execution Migration

Run Kotlin programs with:

```sh
./gradlew execute -PmainClass=ragas.examples.prompteval.PromptAppKt
./gradlew execute -PmainClass=ragas.examples.prompteval.EvalsKt
./gradlew execute -PmainClass=ragas.examples.rageval.EvalsKt
./gradlew execute -PmainClass=ragas.examples.workflow.EvalsKt
./gradlew execute -PmainClass=ragas.examples.agent.EvalsKt
```

## 7) Migration Checklist

- [ ] Replace Python row/dict datasets with `EvaluationDataset` + typed sample models.
- [ ] Replace Python metric imports with Kotlin metric classes from `ragas.metrics`.
- [ ] Update LLM wiring to Kotlin provider models (OpenAI via `OPENAI_API_KEY` if applicable).
- [ ] Validate tokenizer-sensitive logic against `DEFAULT_TOKENIZER` behavior.
- [ ] Move one-off scripts into reproducible Kotlin examples/experiments.
- [ ] Run formatting and checks:

```sh
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew test
```

## Common Pitfalls

1. **Copying Python code into `kotlin` fences**: translate semantics, not syntax.
2. **Assuming all Python integrations exist 1:1**: verify in `PARITY_MATRIX.md` first.
3. **Using old run command patterns**: use `./gradlew execute -PmainClass=...`.
4. **Ignoring nullable/typed fields**: Kotlin compile-time checks will surface model mismatches early.

## Recommended Path

1. Start by migrating a single evaluation flow (prompt, RAG, or workflow).
2. Keep Python and Kotlin outputs side-by-side in CSV for a short parity window.
3. Promote Kotlin as the primary path once metric and regression deltas are within your acceptance range.
