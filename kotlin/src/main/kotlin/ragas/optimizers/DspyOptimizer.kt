package ragas.optimizers

class DspyOptimizer : Optimizer {
    override fun optimize(
        dataset: OptimizationDataset,
        initialPrompts: List<String>,
        evaluator: PromptEvaluator,
        config: OptimizerConfig,
    ): Map<String, String> {
        throw UnsupportedOperationException(
            "DSPy optimizer is not yet implemented in ragas-kotlin. Use GeneticOptimizer for now.",
        )
    }
}
