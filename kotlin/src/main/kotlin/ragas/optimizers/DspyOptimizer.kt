package ragas.optimizers

import ragas.cache.CacheBackend
import ragas.cache.stableCacheKey

class DspyOptimizer : Optimizer {
    constructor() : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        cache = null,
    )

    constructor(cache: CacheBackend) : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        cache = cache,
    )

    constructor(
        adapter: DspyAdapter,
        cache: CacheBackend? = null,
    ) {
        this.adapter = adapter
        this.cache = cache
    }

    private val adapter: DspyAdapter
    private val cache: CacheBackend?

    override fun optimizePrompts(
        dataset: OptimizationDataset,
        initialPrompts: List<OptimizerPrompt>,
        evaluator: PromptObjectEvaluator,
        config: OptimizerConfig,
    ): OptimizerOutcome {
        require(initialPrompts.isNotEmpty()) { "initialPrompts cannot be empty" }
        require(config.iterations > 0) { "iterations must be > 0" }

        var best = initialPrompts.maxBy { prompt -> scoreWithCache(prompt, dataset, evaluator) }
        var bestScore = scoreWithCache(best, dataset, evaluator)

        repeat(config.iterations) { iteration ->
            val context =
                DspyCompileContext(
                    iteration = iteration + 1,
                    dataset = dataset,
                    currentBestPrompt = best,
                )
            val candidates = (listOf(best) + adapter.proposeCandidates(context)).distinctBy { signatureForPrompt(it) }
            val candidateBest = candidates.maxBy { prompt -> scoreWithCache(prompt, dataset, evaluator) }
            val candidateScore = scoreWithCache(candidateBest, dataset, evaluator)
            if (candidateScore >= bestScore) {
                best = candidateBest
                bestScore = candidateScore
            }
        }

        return OptimizerOutcome(
            optimizedPrompt = best,
            metadata =
                mapOf(
                    "optimizer" to "dspy",
                    "adapter" to adapter.javaClass.simpleName,
                ),
        )
    }

    private fun scoreWithCache(
        prompt: OptimizerPrompt,
        dataset: OptimizationDataset,
        evaluator: PromptObjectEvaluator,
    ): Double {
        val cacheBackend = cache ?: return evaluator.score(prompt, dataset)
        val key =
            stableCacheKey(
                buildString {
                    append("optimizer|dspy|score|")
                    append(signatureForDataset(dataset))
                    append("|")
                    append(signatureForPrompt(prompt))
                },
            )
        val cached = cacheBackend.get(key)
        if (cached is Number) {
            return cached.toDouble()
        }

        val computed = evaluator.score(prompt, dataset)
        cacheBackend.put(key, computed)
        return computed
    }

    private fun signatureForDataset(dataset: OptimizationDataset): String =
        buildString {
            append("metric=")
            append(dataset.metricName)
            append("|examples=")
            append(dataset.examples.size)
            dataset.examples.forEach { ex ->
                append("|in=")
                append(
                    ex.promptInput
                        .toSortedMap()
                        .entries
                        .joinToString(",") { (k, v) -> "$k=$v" },
                )
                append("|out=")
                append(ex.expectedOutput)
            }
        }

    private fun signatureForPrompt(prompt: OptimizerPrompt): String =
        when (prompt) {
            is OptimizerPrompt.Text -> {
                "text:${prompt.value}"
            }

            is OptimizerPrompt.MultiModal -> {
                "multimodal:${prompt.asTextPrompt()}"
            }
        }
}
