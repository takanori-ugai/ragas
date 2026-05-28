<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Evaluating Multi-Turn Conversations

Define conversation quality criteria and score multi-turn samples.

```kotlin
// @compile
import ragas.model.AiMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample

val sample =
    MultiTurnSample(
        userInput =
            listOf(
                HumanMessage("I need help with my account"),
                AiMessage("Sure, I can help. What issue are you facing?"),
            ),
        reference = "Assistant should ask clarifying questions and avoid policy violations.",
    )
```

```kotlin
// @compile
import ragas.evaluate
import ragas.tier2Metrics
import ragas.llms.BaseRagasLlm
import ragas.model.AiMessage
import ragas.model.EvaluationDataset
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample

val sample =
    MultiTurnSample(
        userInput =
            listOf(
                HumanMessage("I need help with my account"),
                AiMessage("Sure, I can help. What issue are you facing?"),
            ),
        reference = "Assistant should ask clarifying questions and avoid policy violations.",
    )

val dataset = EvaluationDataset(samples = listOf(sample))
val llm: BaseRagasLlm = TODO("Configure LLM")
val result = evaluate(dataset = dataset, metrics = tier2Metrics(), llm = llm)
println(result.scores)
```
