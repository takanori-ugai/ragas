package ragas.optimizers

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
    fun score(prompt: String, dataset: OptimizationDataset): Double
}

interface Optimizer {
    fun optimize(
        dataset: OptimizationDataset,
        initialPrompts: List<String>,
        evaluator: PromptEvaluator,
        config: OptimizerConfig = OptimizerConfig(),
    ): Map<String, String>
}
