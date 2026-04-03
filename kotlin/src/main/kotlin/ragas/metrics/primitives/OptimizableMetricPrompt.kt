package ragas.metrics.primitives

import ragas.optimizers.OptimizationDataset
import ragas.optimizers.Optimizer
import ragas.optimizers.OptimizerConfig
import ragas.optimizers.OptimizerOutcome
import ragas.optimizers.OptimizerPrompt
import ragas.optimizers.PromptObjectEvaluator

/**
 * Defines [OptimizableMetricPrompt].
 */
interface OptimizableMetricPrompt {
    /**
     * Executes optimizerPrompt.
     */
    fun optimizerPrompt(): OptimizerPrompt

    /**
     * Executes applyOptimizerPrompt.
     *
     * @param prompt Optimizer prompt payload.
     */
    fun applyOptimizerPrompt(prompt: OptimizerPrompt)
}

/**
 * Applies an optimizer [outcome] to this prompt holder.
 *
 * @param outcome Optimizer outcome payload.
 */
fun OptimizableMetricPrompt.applyOptimizerOutcome(outcome: OptimizerOutcome) {
    applyOptimizerPrompt(outcome.optimizedPrompt)
}

/**
 * Optimizes this prompt and applies the best resulting prompt before returning the outcome.
 *
 * @param optimizer Optimizer implementation.
 * @param dataset Optimization dataset.
 * @param evaluator Prompt evaluator.
 * @param config Optimizer configuration.
 */
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
