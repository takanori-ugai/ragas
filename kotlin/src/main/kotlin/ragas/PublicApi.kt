@file:JvmName("Ragas")

package ragas

import ragas.backends.BACKEND_REGISTRY
import ragas.backends.BackendRegistry
import ragas.cache.CacheBackend
import ragas.embeddings.BaseRagasEmbedding
import ragas.embeddings.CachedRagasEmbedding
import ragas.llms.BaseRagasLlm
import ragas.llms.CachedRagasLlm
import ragas.metrics.Metric
import ragas.metrics.collections.agentToolCallTier2Metrics
import ragas.metrics.collections.retrievalGroundednessTier1Metrics
import ragas.metrics.defaults.defaultSingleTurnMetrics
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.Sample
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
): EvaluationResult =
    evaluateInternal(
        dataset = dataset,
        metrics = metrics,
        llm = llm,
        embeddings = embeddings,
        runConfig = runConfig,
        raiseExceptions = raiseExceptions,
        batchSize = batchSize,
    )

suspend fun aevaluate(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>? = null,
    llm: BaseRagasLlm? = null,
    embeddings: BaseRagasEmbedding? = null,
    runConfig: RunConfig = RunConfig(),
    raiseExceptions: Boolean = false,
    batchSize: Int? = null,
): EvaluationResult =
    aevaluateInternal(
        dataset = dataset,
        metrics = metrics,
        llm = llm,
        embeddings = embeddings,
        runConfig = runConfig,
        raiseExceptions = raiseExceptions,
        batchSize = batchSize,
    )

fun defaultMetrics(): List<Metric> = defaultSingleTurnMetrics()

fun tier1Metrics(): List<Metric> = retrievalGroundednessTier1Metrics()

fun tier2Metrics(): List<Metric> = agentToolCallTier2Metrics()

fun withCache(
    llm: BaseRagasLlm,
    cache: CacheBackend,
): BaseRagasLlm = CachedRagasLlm(llm, cache)

fun withCache(
    embedding: BaseRagasEmbedding,
    cache: CacheBackend,
): BaseRagasEmbedding = CachedRagasEmbedding(embedding, cache)

fun backendRegistry(): BackendRegistry = BACKEND_REGISTRY
