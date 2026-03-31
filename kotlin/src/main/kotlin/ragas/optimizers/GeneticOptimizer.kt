package ragas.optimizers

import kotlin.math.max
import kotlin.random.Random

class GeneticOptimizer : Optimizer {
    override fun optimize(
        dataset: OptimizationDataset,
        initialPrompts: List<String>,
        evaluator: PromptEvaluator,
        config: OptimizerConfig,
    ): Map<String, String> {
        require(initialPrompts.isNotEmpty()) { "initialPrompts cannot be empty" }
        require(config.populationSize > 0) { "populationSize must be > 0" }
        require(config.iterations > 0) { "iterations must be > 0" }

        val random = Random(config.seed)

        var population =
            seedPopulation(
                initialPrompts = initialPrompts,
                populationSize = config.populationSize,
                random = random,
            )

        repeat(config.iterations) {
            val scored = population.map { prompt -> prompt to evaluator.score(prompt, dataset) }
            val sorted = scored.sortedByDescending { (_, score) -> score }
            val eliteCount = max(1, config.populationSize / 2)
            val elite = sorted.take(eliteCount).map { (prompt) -> prompt }

            val next = elite.toMutableList()
            while (next.size < config.populationSize) {
                val p1 = elite.random(random)
                val p2 = elite.random(random)
                val crossed = crossover(p1, p2, random)
                val mutated = maybeMutate(crossed, random, config.mutationProbability)
                next += mutated
            }
            population = next
        }

        val best = population.maxBy { prompt -> evaluator.score(prompt, dataset) }
        return mapOf("optimized_prompt" to best)
    }

    private fun seedPopulation(
        initialPrompts: List<String>,
        populationSize: Int,
        random: Random,
    ): List<String> {
        val population = initialPrompts.toMutableList()
        while (population.size < populationSize) {
            val source = initialPrompts.random(random)
            population += "$source\n(variant ${population.size + 1})"
        }
        return population.take(populationSize)
    }

    private fun crossover(
        parent1: String,
        parent2: String,
        random: Random,
    ): String {
        val split1 = parent1.split(" ")
        val split2 = parent2.split(" ")
        if (split1.isEmpty()) {
            return parent2
        }
        if (split2.isEmpty()) {
            return parent1
        }

        val cut1 = random.nextInt(split1.size)
        val cut2 = random.nextInt(split2.size)
        val child = split1.take(cut1) + split2.drop(cut2)
        return child.joinToString(" ").ifBlank { parent1 }
    }

    private fun maybeMutate(
        prompt: String,
        random: Random,
        mutationProbability: Double,
    ): String {
        if (random.nextDouble() >= mutationProbability) {
            return prompt
        }

        val mutations =
            listOf(
                "$prompt\nBe concise.",
                "$prompt\nExplain your reasoning briefly.",
                "$prompt\nReturn only the final value.",
            )
        return mutations.random(random)
    }
}
