package ragas.metrics.primitives

import ragas.optimizers.OptimizationDataset
import ragas.optimizers.Optimizer
import ragas.optimizers.OptimizerConfig
import ragas.optimizers.OptimizerOutcome
import ragas.optimizers.OptimizerPrompt
import ragas.optimizers.PromptObjectEvaluator

interface OptimizableMetricPrompt {
    fun optimizerPrompt(): OptimizerPrompt

    fun applyOptimizerPrompt(prompt: OptimizerPrompt)
}

fun OptimizableMetricPrompt.applyOptimizerOutcome(outcome: OptimizerOutcome) {
    applyOptimizerPrompt(outcome.optimizedPrompt)
}

fun OptimizableMetricPrompt.optimizePrompt(
    optimizer: Optimizer,
    dataset: OptimizationDataset,
    evaluator: PromptObjectEvaluator,
    config: OptimizerConfig = OptimizerConfig(),
): OptimizerOutcome {
    val outcome =
        optimizer.optimizePrompts(
            dataset = dataset,
            initialPrompts = listOf(optimizerPrompt()),
            evaluator = evaluator,
            config = config,
        )
    applyOptimizerOutcome(outcome)
    return outcome
}
