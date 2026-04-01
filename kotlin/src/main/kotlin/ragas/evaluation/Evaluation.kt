package ragas.evaluation

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunFailed
import ragas.integrations.tracing.RunStarted
import ragas.integrations.tracing.TraceObserver
import ragas.embeddings.BaseRagasEmbedding
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.metrics.MetricWithEmbeddings
import ragas.metrics.MetricWithLlm
import ragas.metrics.MultiTurnMetric
import ragas.metrics.SingleTurnMetric
import ragas.metrics.defaults.defaultSingleTurnMetrics
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.MultiTurnSample
import ragas.model.Sample
import ragas.model.SingleTurnSample
import ragas.model.ToolCall
import ragas.runtime.Executor
import ragas.runtime.RunConfig
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.WeakHashMap

private const val EVALUATION_DESC = "Evaluating"
private val metricLocks: MutableMap<Metric, Mutex> = Collections.synchronizedMap(WeakHashMap())

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
    executorSink: ((Executor) -> Unit)? = null,
    cancellationToken: EvaluationCancellationToken? = null,
): EvaluationResult {
    val mappedDataset = remapDatasetColumns(dataset, columnMap)
    val selectedMetrics = metrics ?: defaultMetricsForDataset(mappedDataset)
    require(selectedMetrics.isNotEmpty()) { "Provide at least one metric for evaluation." }

    validateRequiredColumns(mappedDataset, selectedMetrics)
    validateSupportedMetrics(mappedDataset, selectedMetrics)

    val llmChanged = mutableListOf<MetricWithLlm>()
    val embeddingsChanged = mutableListOf<MetricWithEmbeddings>()
    val acquiredLocks = mutableListOf<Mutex>()
    val runId = UUID.randomUUID().toString()
    val startTs = System.currentTimeMillis()

    callbacks.filterIsInstance<EvaluationLifecycleCallback>().forEach { callback ->
        callback.onRunStarted(
            EvaluationRunStartedEvent(
                runId = runId,
                sampleCount = mappedDataset.samples.size,
                metricNames = selectedMetrics.map { metric -> metric.name },
            ),
        )
    }
    traceObservers.forEach { observer ->
        observer.onEvent(
            RunStarted(
                runId = runId,
                framework = "ragas-kotlin",
                runName = EVALUATION_DESC,
                timestampMs = startTs,
            ),
        )
    }

    lockMetrics(selectedMetrics, acquiredLocks)

    try {
        selectedMetrics.forEach { metric ->
            if (metric is MetricWithLlm && metric.llm == null && llm != null) {
                metric.llm = llm
                llmChanged += metric
            }
            if (metric is MetricWithEmbeddings && metric.embeddings == null && embeddings != null) {
                metric.embeddings = embeddings
                embeddingsChanged += metric
            }
            metric.init(runConfig)
        }

        val executor =
            Executor(
                description = EVALUATION_DESC,
                raiseExceptions = raiseExceptions,
                runConfig = runConfig,
                batchSize = batchSize,
            )
        cancellationToken?.bind(executor)
        executorSink?.invoke(executor)
        if (returnExecutor && executorSink == null) {
            error("returnExecutor=true requires executorSink to receive the executor instance.")
        }

        mappedDataset.samples.forEachIndexed { rowIndex, sample ->
            selectedMetrics.forEach { metric ->
                val metricName = metric.name
                when {
                    sample is SingleTurnSample && metric is SingleTurnMetric -> {
                        executor.submit(name = "$metricName-$rowIndex") {
                            metric.singleTurnAscore(sample)
                        }
                    }

                    sample is MultiTurnSample && metric is MultiTurnMetric -> {
                        executor.submit(name = "$metricName-$rowIndex") {
                            metric.multiTurnAscore(sample)
                        }
                    }
                }
            }
        }

        if (cancellationToken?.isCancelled() == true) {
            executor.cancel()
        }

        val flatResults = executor.aresults()
        val rowScores = mutableListOf<Map<String, Any?>>()
        var cursor = 0
        repeat(mappedDataset.samples.size) { rowIndex ->
            val row = linkedMapOf<String, Any?>()
            selectedMetrics.forEach { metric ->
                val score = flatResults[cursor++]
                row[metric.name] = score

                val metricEvent =
                    EvaluationMetricComputedEvent(
                        runId = runId,
                        rowIndex = rowIndex,
                        metricName = metric.name,
                        score = score,
                    )
                callbacks.forEach { callback ->
                    callback.onMetricComputed(metricEvent)
                }

                val sample = mappedDataset.samples[rowIndex]
                val usage = tokenUsageParser?.invoke(rowIndex, metric.name, sample, score)
                if (usage != null) {
                    val usageEvent =
                        TokenUsageEvent(
                            rowIndex = rowIndex,
                            metricName = metric.name,
                            usage = usage,
                        )
                    tokenUsageCallback?.invoke(usageEvent)
                    val cost = costParser?.invoke(usage)
                    if (cost != null) {
                        costCallback?.invoke(
                            CostEvent(
                                rowIndex = rowIndex,
                                metricName = metric.name,
                                usage = usage,
                                cost = cost,
                            ),
                        )
                    }
                }
            }
            rowScores += row
            traceObservers.forEach { observer ->
                observer.onEvent(
                    MetricRowLogged(
                        runId = runId,
                        framework = "ragas-kotlin",
                        runName = EVALUATION_DESC,
                        timestampMs = System.currentTimeMillis(),
                        rowIndex = rowIndex,
                        scores = row,
                    ),
                )
            }
        }

        callbacks.filterIsInstance<EvaluationLifecycleCallback>().forEach { callback ->
            callback.onRunCompleted(
                EvaluationRunCompletedEvent(
                    runId = runId,
                    rowCount = rowScores.size,
                    metricCount = selectedMetrics.size,
                ),
            )
        }
        traceObservers.forEach { observer ->
            observer.onEvent(
                RunCompleted(
                    runId = runId,
                    framework = "ragas-kotlin",
                    runName = EVALUATION_DESC,
                    timestampMs = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - startTs,
                    aggregateMetrics = numericMeans(rowScores),
                ),
            )
        }

        return EvaluationResult(
            scores = rowScores,
            dataset = mappedDataset,
        )
    } catch (error: Throwable) {
        callbacks.filterIsInstance<EvaluationLifecycleCallback>().forEach { callback ->
            callback.onRunFailed(EvaluationRunFailedEvent(runId = runId, error = error))
        }
        traceObservers.forEach { observer ->
            observer.onEvent(
                RunFailed(
                    runId = runId,
                    framework = "ragas-kotlin",
                    runName = EVALUATION_DESC,
                    timestampMs = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - startTs,
                    errorType = error::class.simpleName ?: "UnknownError",
                    errorMessage = error.message.orEmpty(),
                ),
            )
        }
        throw error
    } finally {
        llmChanged.forEach { metric -> metric.llm = null }
        embeddingsChanged.forEach { metric -> metric.embeddings = null }
        acquiredLocks.asReversed().forEach { lock -> lock.unlock() }
    }
}

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
    executorSink: ((Executor) -> Unit)? = null,
    cancellationToken: EvaluationCancellationToken? = null,
): EvaluationResult =
    runBlocking {
        aevaluate(
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
    }

private fun validateRequiredColumns(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>,
) {
    val datasetFeatures = dataset.features()
    metrics.forEach { metric ->
        metric.requiredColumns.values.flatten().forEach { column ->
            require(column in datasetFeatures) {
                "Metric '${metric.name}' requires column '$column', but dataset has $datasetFeatures"
            }
        }
    }
}

private suspend fun lockMetrics(
    metrics: List<Metric>,
    acquiredLocks: MutableList<Mutex>,
) {
    distinctByIdentity(metrics).sortedBy { metric -> System.identityHashCode(metric) }.forEach { metric ->
        val lock =
            synchronized(metricLocks) {
                metricLocks.getOrPut(metric) { Mutex() }
            }
        lock.lock()
        acquiredLocks += lock
    }
}

private fun distinctByIdentity(metrics: List<Metric>): List<Metric> {
    val seen = IdentityHashMap<Metric, Boolean>(metrics.size)
    return metrics.filter { metric -> seen.put(metric, true) == null }
}

private fun defaultMetricsForDataset(dataset: EvaluationDataset<out Sample>): List<Metric> =
    when (dataset.getSampleType()) {
        SingleTurnSample::class -> {
            defaultSingleTurnMetrics()
        }

        MultiTurnSample::class -> {
            error("Default metrics are currently implemented only for single-turn datasets.")
        }

        else -> {
            error("Unsupported sample type: ${dataset.getSampleType()}")
        }
    }

private fun validateSupportedMetrics(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>,
) {
    val sampleType = dataset.getSampleType() ?: return
    when (sampleType) {
        SingleTurnSample::class -> {
            require(metrics.all { metric -> metric is SingleTurnMetric }) {
                "Single-turn datasets support only SingleTurnMetric instances."
            }
        }

        MultiTurnSample::class -> {
            require(metrics.all { metric -> metric is MultiTurnMetric }) {
                "Multi-turn datasets support only MultiTurnMetric instances."
            }
        }

        else -> {
            error("Unsupported sample type: $sampleType")
        }
    }
}

private fun remapDatasetColumns(
    dataset: EvaluationDataset<out Sample>,
    columnMap: Map<String, String>,
): EvaluationDataset<out Sample> {
    if (columnMap.isEmpty() || dataset.samples.isEmpty()) {
        return dataset
    }

    return when (dataset.getSampleType()) {
        SingleTurnSample::class -> {
            val remapped =
                dataset.samples.map { sample ->
                    remapSingleTurnSample(sample as SingleTurnSample, columnMap)
                }
            EvaluationDataset(remapped)
        }

        MultiTurnSample::class -> {
            val remapped =
                dataset.samples.map { sample ->
                    remapMultiTurnSample(sample as MultiTurnSample, columnMap)
                }
            EvaluationDataset(remapped)
        }

        else -> dataset
    }
}

private fun remapSingleTurnSample(
    sample: SingleTurnSample,
    columnMap: Map<String, String>,
): SingleTurnSample {
    val values = sample.toMap()
    fun mapped(column: String): Any? = values[column] ?: columnMap[column]?.let { source -> values[source] }

    @Suppress("UNCHECKED_CAST")
    return SingleTurnSample(
        userInput = mapped("user_input") as? String,
        retrievedContexts = mapped("retrieved_contexts") as? List<String>,
        referenceContexts = mapped("reference_contexts") as? List<String>,
        retrievedContextIds = mapped("retrieved_context_ids") as? List<String>,
        referenceContextIds = mapped("reference_context_ids") as? List<String>,
        response = mapped("response") as? String,
        multiResponses = mapped("multi_responses") as? List<String>,
        reference = mapped("reference") as? String,
        rubrics = mapped("rubrics") as? Map<String, String>,
        personaName = mapped("persona_name") as? String,
        queryStyle = mapped("query_style") as? String,
        queryLength = mapped("query_length") as? String,
    )
}

private fun remapMultiTurnSample(
    sample: MultiTurnSample,
    columnMap: Map<String, String>,
): MultiTurnSample {
    val values = sample.toMap()
    fun mapped(column: String): Any? = values[column] ?: columnMap[column]?.let { source -> values[source] }

    @Suppress("UNCHECKED_CAST")
    return MultiTurnSample(
        userInput = sample.userInput,
        reference = mapped("reference") as? String,
        referenceToolCalls = mapped("reference_tool_calls") as? List<ToolCall>,
        rubrics = mapped("rubrics") as? Map<String, String>,
        referenceTopics = mapped("reference_topics") as? List<String>,
    )
}

private fun numericMeans(scores: List<Map<String, Any?>>): Map<String, Double> {
    if (scores.isEmpty()) {
        return emptyMap()
    }
    val buckets = mutableMapOf<String, MutableList<Double>>()
    scores.forEach { row ->
        row.forEach { (metric, value) ->
            if (value is Number) {
                buckets.getOrPut(metric) { mutableListOf() } += value.toDouble()
            }
        }
    }
    return buckets.mapValues { (_, values) -> values.average() }
}
