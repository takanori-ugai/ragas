package ragas.optimizers

import ragas.cache.CacheBackend
import ragas.cache.stableCacheKey

/**
 * Optimizer that uses an optional DSPy adapter to propose prompt candidates.
 */
class DspyOptimizer : Optimizer {
    constructor() : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        runtimeConfig = DspyRuntimeConfig(),
        cache = null,
    )

    constructor(cache: CacheBackend) : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        runtimeConfig = DspyRuntimeConfig(),
        cache = cache,
    )

    constructor(runtimeConfig: DspyRuntimeConfig) : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        runtimeConfig = runtimeConfig,
        cache = null,
    )

    constructor(
        runtimeConfig: DspyRuntimeConfig,
        cache: CacheBackend,
    ) : this(
        adapter = DspyAdapterLoader.loadFirstOrNull() ?: HeuristicDspyAdapter(),
        runtimeConfig = runtimeConfig,
        cache = cache,
    )

    constructor(
        adapter: DspyAdapter,
        runtimeConfig: DspyRuntimeConfig = DspyRuntimeConfig(),
        cache: CacheBackend? = null,
    ) {
        this.adapter = adapter
        this.runtimeConfig = runtimeConfig
        this.cache = cache
    }

    private val adapter: DspyAdapter
    private val runtimeConfig: DspyRuntimeConfig
    private val cache: CacheBackend?

    /**
     * Optimizes a prompt on a dataset and returns the best discovered outcome.
     */
    override fun optimizePrompts(
        dataset: OptimizationDataset,
        initialPrompts: List<OptimizerPrompt>,
        evaluator: PromptObjectEvaluator,
        config: OptimizerConfig,
    ): OptimizerOutcome {
        require(initialPrompts.isNotEmpty()) { "initialPrompts cannot be empty" }
        require(config.iterations > 0) { "iterations must be > 0" }

        val datasetSignature = cache?.let { signatureForDataset(dataset) }
        val promptSignatures = mutableMapOf<OptimizerPrompt, String>()
        val rng = kotlin.random.Random(runtimeConfig.seed)
        val maxErrors = runtimeConfig.maxErrors ?: Int.MAX_VALUE
        var errors = 0
        var cacheHits = 0
        var candidatesEvaluated = 0
        var iterationsCompleted = 0

        fun signature(prompt: OptimizerPrompt): String = promptSignatures.getOrPut(prompt) { signatureForPrompt(prompt) }

        fun bestWithScore(prompts: List<OptimizerPrompt>): Pair<OptimizerPrompt, Double> {
            fun safeScore(prompt: OptimizerPrompt): Double =
                runCatching {
                    scoreWithCache(prompt, signature(prompt), datasetSignature, dataset, evaluator) {
                        cacheHits += 1
                    }
                }.getOrElse {
                    errors += 1
                    if (errors > maxErrors) {
                        throw IllegalStateException("DSPy optimizer exceeded maxErrors=$maxErrors", it)
                    }
                    Double.NEGATIVE_INFINITY
                }

            var currentBest = prompts.first()
            var currentBestScore = safeScore(currentBest)
            candidatesEvaluated += 1
            prompts.drop(1).forEach { prompt ->
                val score = safeScore(prompt)
                candidatesEvaluated += 1
                if (score > currentBestScore) {
                    currentBest = prompt
                    currentBestScore = score
                }
            }
            return currentBest to currentBestScore
        }

        var (best, bestScore) = bestWithScore(initialPrompts)

        for (iteration in 0 until config.iterations) {
            iterationsCompleted = iteration + 1
            val labeledDemos = selectLabeledDemos(dataset, runtimeConfig.maxLabeledDemos, rng)
            val bootstrappedDemoHints =
                buildBootstrappedDemoHints(
                    prompt = best,
                    demos = selectLabeledDemos(dataset, runtimeConfig.maxBootstrappedDemos, rng),
                )
            val context =
                DspyCompileContext(
                    iteration = iteration + 1,
                    dataset = dataset,
                    currentBestPrompt = best,
                    runtimeConfig = runtimeConfig,
                    labeledDemos = labeledDemos,
                    bootstrappedDemoHints = bootstrappedDemoHints,
                )
            val candidates =
                (listOf(best) + adapter.proposeCandidates(context))
                    .distinctBy(::signature)
                    .take(runtimeConfig.numCandidates.coerceAtLeast(1))
            val (candidateBest, candidateScore) = bestWithScore(candidates)
            if (candidateScore >= bestScore) {
                best = candidateBest
                bestScore = candidateScore
            }
            val threshold = runtimeConfig.metricThreshold
            if (threshold != null && bestScore >= threshold) {
                break
            }
        }

        val metadata =
            mutableMapOf(
                "optimizer" to "dspy",
                "adapter" to adapter.javaClass.simpleName,
            )
        if (runtimeConfig.trackStats) {
            metadata["num_candidates"] = runtimeConfig.numCandidates.toString()
            metadata["max_bootstrapped_demos"] = runtimeConfig.maxBootstrappedDemos.toString()
            metadata["max_labeled_demos"] = runtimeConfig.maxLabeledDemos.toString()
            metadata["init_temperature"] = runtimeConfig.initTemperature.toString()
            metadata["auto"] = runtimeConfig.auto?.name?.lowercase() ?: "none"
            metadata["iterations_completed"] = iterationsCompleted.toString()
            metadata["candidates_evaluated"] = candidatesEvaluated.toString()
            metadata["errors"] = errors.toString()
            metadata["cache_hits"] = cacheHits.toString()
        }

        return OptimizerOutcome(
            optimizedPrompt = best,
            metadata = metadata,
        )
    }

    private fun scoreWithCache(
        prompt: OptimizerPrompt,
        promptSignature: String,
        datasetSignature: String?,
        dataset: OptimizationDataset,
        evaluator: PromptObjectEvaluator,
        onCacheHit: () -> Unit = {},
    ): Double {
        val cacheBackend = cache ?: return evaluator.score(prompt, dataset)
        val stableDatasetSignature = datasetSignature ?: signatureForDataset(dataset)
        val key =
            stableCacheKey(
                buildString {
                    append("optimizer|dspy|score|")
                    append(stableDatasetSignature)
                    append("|")
                    append(promptSignature)
                },
            )
        val cached = cacheBackend.get(key)
        if (cached is Number) {
            onCacheHit()
            return cached.toDouble()
        }

        val computed = evaluator.score(prompt, dataset)
        cacheBackend.put(key, computed)
        return computed
    }

    private fun selectLabeledDemos(
        dataset: OptimizationDataset,
        maxCount: Int,
        rng: kotlin.random.Random,
    ): List<OptimizationExample> {
        if (maxCount <= 0 || dataset.examples.isEmpty()) {
            return emptyList()
        }
        return dataset.examples.shuffled(rng).take(maxCount)
    }

    private fun buildBootstrappedDemoHints(
        prompt: OptimizerPrompt,
        demos: List<OptimizationExample>,
    ): List<String> {
        if (demos.isEmpty()) {
            return emptyList()
        }
        val projectedPrompt = prompt.asTextPrompt().lowercase()
        return demos.map { demo ->
            val fieldList =
                demo.promptInput.keys
                    .sorted()
                    .joinToString(",")
            val expectedSnippet = demo.expectedOutput.take(80)
            when {
                "json" in projectedPrompt -> {
                    "Bootstrapped demo: map fields [$fieldList] to expected JSON-style output like '$expectedSnippet'."
                }

                else -> {
                    "Bootstrapped demo: align response to fields [$fieldList] and expected output pattern '$expectedSnippet'."
                }
            }
        }
    }

    private fun signatureForDataset(dataset: OptimizationDataset): String =
        buildString {
            append("metric:")
            append(dataset.metricName.length)
            append(":")
            append(dataset.metricName)
            append("|examples:")
            append(dataset.examples.size)
            dataset.examples.forEach { ex ->
                append("|in:")
                append(ex.promptInput.size)
                ex.promptInput
                    .toSortedMap()
                    .forEach { (k, v) ->
                        append("|k:")
                        append(k.length)
                        append(":")
                        append(k)
                        append("|v:")
                        append(v.length)
                        append(":")
                        append(v)
                    }
                append("|out:")
                append(ex.expectedOutput.length)
                append(":")
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
