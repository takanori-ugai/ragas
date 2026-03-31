package ragas

import ragas.optimizers.DspyOptimizer
import ragas.optimizers.GeneticOptimizer
import ragas.optimizers.OptimizationDataset
import ragas.optimizers.OptimizationExample
import ragas.optimizers.OptimizerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OptimizersTest {
    @Test
    fun geneticOptimizerFindsPromptContainingTargetWord() {
        val optimizer = GeneticOptimizer()
        val dataset =
            OptimizationDataset(
                metricName = "dummy",
                examples = listOf(OptimizationExample(promptInput = mapOf("q" to "x"), expectedOutput = "y")),
            )

        val result =
            optimizer.optimize(
                dataset = dataset,
                initialPrompts = listOf("Answer freely", "Return concise result"),
                evaluator = { prompt, _ -> if ("concise" in prompt.lowercase()) 1.0 else 0.2 },
                config = OptimizerConfig(iterations = 4, populationSize = 6, seed = 7),
            )

        assertTrue(result.containsKey("optimized_prompt"))
        assertTrue("concise" in result.getValue("optimized_prompt").lowercase())
    }

    @Test
    fun geneticOptimizerIsDeterministicWithSeed() {
        val optimizer = GeneticOptimizer()
        val dataset =
            OptimizationDataset(
                metricName = "dummy",
                examples = listOf(OptimizationExample(promptInput = mapOf("q" to "x"), expectedOutput = "y")),
            )

        val cfg = OptimizerConfig(iterations = 3, populationSize = 5, seed = 123)
        val eval = { prompt: String, _: OptimizationDataset -> if ("final" in prompt.lowercase()) 0.9 else 0.1 }

        val r1 = optimizer.optimize(dataset, listOf("Return final value", "Explain"), eval, cfg)
        val r2 = optimizer.optimize(dataset, listOf("Return final value", "Explain"), eval, cfg)
        assertEquals(r1, r2)
    }

    @Test
    fun dspyOptimizerExplicitlyUnsupported() {
        val optimizer = DspyOptimizer()
        val dataset =
            OptimizationDataset(
                metricName = "dummy",
                examples = emptyList(),
            )

        assertFailsWith<UnsupportedOperationException> {
            optimizer.optimize(
                dataset = dataset,
                initialPrompts = listOf("a"),
                evaluator = { _, _ -> 0.0 },
            )
        }
    }
}
