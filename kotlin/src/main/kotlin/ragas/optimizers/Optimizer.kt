package ragas.optimizers

import ragas.prompt.PromptContentPart

data class OptimizationExample(
    val promptInput: Map<String, String>,
    val expectedOutput: String,
)

data class OptimizationDataset(
    val metricName: String,
    val examples: List<OptimizationExample>,
)

data class OptimizerConfig(
    val iterations: Int = 3,
    val populationSize: Int = 6,
    val mutationProbability: Double = 0.2,
    val seed: Int = 42,
)

fun interface PromptEvaluator {
    fun score(
        prompt: String,
        dataset: OptimizationDataset,
    ): Double
}

sealed interface OptimizerPrompt {
    data class Text(
        val value: String,
    ) : OptimizerPrompt

    data class MultiModal(
        val content: List<PromptContentPart>,
    ) : OptimizerPrompt
}

fun OptimizerPrompt.asTextPrompt(): String =
    when (this) {
        is OptimizerPrompt.Text -> value
        is OptimizerPrompt.MultiModal -> content.joinToString(separator = "\n") { it.toPromptText() }
    }

fun interface PromptObjectEvaluator {
    fun score(
        prompt: OptimizerPrompt,
        dataset: OptimizationDataset,
    ): Double
}

data class OptimizerOutcome(
    val optimizedPrompt: OptimizerPrompt,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toLegacyMap(): Map<String, String> {
        val base =
            when (optimizedPrompt) {
                is OptimizerPrompt.Text -> mapOf("optimized_prompt" to optimizedPrompt.value)
                is OptimizerPrompt.MultiModal ->
                    mapOf(
                        "optimized_prompt" to optimizedPrompt.asTextPrompt(),
                        "optimized_prompt_type" to "multimodal",
                    )
            }
        return base + metadata
    }
}

interface Optimizer {
    fun optimizePrompts(
        dataset: OptimizationDataset,
        initialPrompts: List<OptimizerPrompt>,
        evaluator: PromptObjectEvaluator,
        config: OptimizerConfig = OptimizerConfig(),
    ): OptimizerOutcome

    fun optimize(
        dataset: OptimizationDataset,
        initialPrompts: List<String>,
        evaluator: PromptEvaluator,
        config: OptimizerConfig = OptimizerConfig(),
    ): Map<String, String> =
        optimizePrompts(
            dataset = dataset,
            initialPrompts = initialPrompts.map { OptimizerPrompt.Text(it) },
            evaluator = PromptObjectEvaluator { prompt, ds ->
                when (prompt) {
                    is OptimizerPrompt.Text -> evaluator.score(prompt.value, ds)
                    is OptimizerPrompt.MultiModal -> evaluator.score(prompt.asTextPrompt(), ds)
                }
            },
            config = config,
        ).toLegacyMap()
}
