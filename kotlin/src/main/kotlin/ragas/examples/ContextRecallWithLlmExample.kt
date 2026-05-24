package ragas.examples

import dev.langchain4j.model.openai.OpenAiChatModel
import ragas.evaluate
import ragas.llms.LangChain4jLlm
import ragas.metrics.defaults.ContextRecallMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig

/**
 * Example: run ContextRecallMetric with an LLM-backed judge.
 *
 * When the LLM path fails or yields an invalid score, this example logs the reason
 * and falls back to the metric's heuristic mode.
 *
 * Optional environment:
 * - OPENAI_API_KEY
 *
 * Run:
 *   ./gradlew run --args="" -PmainClass=ragas.examples.ContextRecallWithLlmExampleKt
 */
fun main() {
    val modelName = "gpt-5.4-mini"

    val dataset =
        EvaluationDataset(
            listOf(
                SingleTurnSample(
                    userInput = "When was Kotlin announced and by whom?",
                    retrievedContexts =
                        listOf(
                            "Kotlin was created by JetBrains and publicly announced in 2011.",
                            "Kotlin became an officially supported Android language in 2017.",
                        ),
                    reference = "Kotlin was announced in 2011 by JetBrains.",
                ),
            ),
        )

    val metric = ContextRecallMetric()
    val apiKey = System.getenv("OPENAI_API_KEY")

    val llmScore =
        if (!apiKey.isNullOrBlank()) {
            runCatching {
                val chatModel =
                    OpenAiChatModel
                        .builder()
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .temperature(0.0)
                        .build()

                val ragasLlm =
                    LangChain4jLlm(
                        model = chatModel,
                        runConfig = RunConfig(timeoutSeconds = 90),
                    )

                evaluate(
                    dataset = dataset,
                    metrics = listOf(metric),
                    llm = ragasLlm,
                ).scores
                    .firstOrNull()
                    ?.get(metric.name)
                    .asDoubleOrNull()
            }.onFailure { error ->
                println("[WARN] LLM evaluation failed; falling back to heuristic mode. reason=${error.message}")
            }.getOrNull()
        } else {
            println("[WARN] OPENAI_API_KEY is not set; falling back to heuristic mode.")
            null
        }

    val finalScore: Double
    val mode: String

    if (llmScore != null && !llmScore.isNaN()) {
        finalScore = llmScore
        mode = "llm"
    } else {
        if (llmScore != null && llmScore.isNaN()) {
            println("[WARN] LLM evaluation returned NaN; falling back to heuristic mode.")
        }
        finalScore =
            evaluate(
                dataset = dataset,
                metrics = listOf(metric),
            ).scores
                .firstOrNull()
                ?.get(metric.name)
                .asDoubleOrNull()
                ?: Double.NaN
        mode = "heuristic"
    }

    println("Model: $modelName")
    println("Mode: $mode")
    println("${metric.name} = $finalScore")
}

private fun Any?.asDoubleOrNull(): Double? =
    when (this) {
        is Number -> this.toDouble()
        is String -> this.toDoubleOrNull()
        else -> null
    }
