@file:JvmName("Ragas")

package ragas

import ragas.backends.BACKEND_REGISTRY
import ragas.backends.BackendRegistry
import ragas.cache.CacheBackend
import ragas.embeddings.BaseRagasEmbedding
import ragas.embeddings.CachedRagasEmbedding
import ragas.evaluation.CostEvent
import ragas.evaluation.EvaluationCallback
import ragas.evaluation.EvaluationCancellationToken
import ragas.evaluation.TokenUsage
import ragas.evaluation.TokenUsageEvent
import ragas.evaluation.TokenUsageParser
import ragas.llms.BaseRagasLlm
import ragas.llms.CachedRagasLlm
import ragas.integrations.tracing.TraceObserver
import ragas.metrics.Metric
import ragas.metrics.collections.agentToolCallTier2Metrics
import ragas.metrics.collections.answerQualityTier3Metrics
import ragas.metrics.collections.retrievalGroundednessTier1Metrics
import ragas.metrics.collections.tier4CollectionMetrics
import ragas.metrics.defaults.defaultSingleTurnMetrics
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.Sample
import ragas.optimizers.DspyOptimizer
import ragas.optimizers.GeneticOptimizer
import ragas.optimizers.Optimizer
import ragas.runtime.RunConfig
import ragas.evaluation.aevaluate as aevaluateInternal
import ragas.evaluation.evaluate as evaluateInternal

const val VERSION: String = "0.0.1"

fun evaluate(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>? = null,
    llm: BaseRagasLlm? = null,
    embeddings: BaseRagasEmbedding? = null,
    runConfig: RunConfig = RunConfig(),
    raiseExceptions: Boolean = false,
    batchSize: Int? = null,
    callbacks: List<EvaluationCallback> = emptyList(),
    traceObservers: List<TraceObserver> = emptyList(),
    tokenUsageParser: TokenUsageParser? = null,
    tokenUsageCallback: ((TokenUsageEvent) -> Unit)? = null,
    costCallback: ((CostEvent) -> Unit)? = null,
    costParser: ((TokenUsage) -> Double)? = null,
    columnMap: Map<String, String> = emptyMap(),
    returnExecutor: Boolean = false,
    executorSink: ((ragas.runtime.Executor) -> Unit)? = null,
    cancellationToken: EvaluationCancellationToken? = null,
): EvaluationResult =
    evaluateInternal(
        dataset = dataset,
        metrics = metrics,
        llm = llm,
        embeddings = embeddings,
        runConfig = runConfig,
        raiseExceptions = raiseExceptions,
        batchSize = batchSize,
        callbacks = callbacks,
        traceObservers = traceObservers,
        tokenUsageParser = tokenUsageParser,
        tokenUsageCallback = tokenUsageCallback,
        costCallback = costCallback,
        costParser = costParser,
        columnMap = columnMap,
        returnExecutor = returnExecutor,
        executorSink = executorSink,
        cancellationToken = cancellationToken,
    )

suspend fun aevaluate(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>? = null,
    llm: BaseRagasLlm? = null,
    embeddings: BaseRagasEmbedding? = null,
    runConfig: RunConfig = RunConfig(),
    raiseExceptions: Boolean = false,
    batchSize: Int? = null,
    callbacks: List<EvaluationCallback> = emptyList(),
    traceObservers: List<TraceObserver> = emptyList(),
    tokenUsageParser: TokenUsageParser? = null,
    tokenUsageCallback: ((TokenUsageEvent) -> Unit)? = null,
    costCallback: ((CostEvent) -> Unit)? = null,
    costParser: ((TokenUsage) -> Double)? = null,
    columnMap: Map<String, String> = emptyMap(),
    returnExecutor: Boolean = false,
    executorSink: ((ragas.runtime.Executor) -> Unit)? = null,
    cancellationToken: EvaluationCancellationToken? = null,
): EvaluationResult =
    aevaluateInternal(
        dataset = dataset,
        metrics = metrics,
        llm = llm,
        embeddings = embeddings,
        runConfig = runConfig,
        raiseExceptions = raiseExceptions,
        batchSize = batchSize,
        callbacks = callbacks,
        traceObservers = traceObservers,
        tokenUsageParser = tokenUsageParser,
        tokenUsageCallback = tokenUsageCallback,
        costCallback = costCallback,
        costParser = costParser,
        columnMap = columnMap,
        returnExecutor = returnExecutor,
        executorSink = executorSink,
        cancellationToken = cancellationToken,
    )

fun defaultMetrics(): List<Metric> = defaultSingleTurnMetrics()

fun tier1Metrics(): List<Metric> = retrievalGroundednessTier1Metrics()

fun tier2Metrics(): List<Metric> = agentToolCallTier2Metrics()

fun tier3Metrics(): List<Metric> = answerQualityTier3Metrics()

fun tier4Metrics(): List<Metric> = tier4CollectionMetrics()

fun withCache(
    llm: BaseRagasLlm,
    cache: CacheBackend,
): BaseRagasLlm = CachedRagasLlm(llm, cache)

fun withCache(
    embedding: BaseRagasEmbedding,
    cache: CacheBackend,
): BaseRagasEmbedding = CachedRagasEmbedding(embedding, cache)

fun backendRegistry(): BackendRegistry = BACKEND_REGISTRY

fun geneticOptimizer(): Optimizer = GeneticOptimizer()

fun dspyOptimizer(cache: CacheBackend? = null): Optimizer =
    if (cache == null) {
        DspyOptimizer()
    } else {
        DspyOptimizer(cache = cache)
    }
