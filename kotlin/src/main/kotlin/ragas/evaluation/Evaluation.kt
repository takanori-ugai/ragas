package ragas.evaluation

import kotlinx.coroutines.runBlocking
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
import ragas.runtime.Executor
import ragas.runtime.RunConfig

private const val EVALUATION_DESC = "Evaluating"

suspend fun aevaluate(
    dataset: EvaluationDataset<out Sample>,
    metrics: List<Metric>? = null,
    llm: BaseRagasLlm? = null,
    embeddings: BaseRagasEmbedding? = null,
    runConfig: RunConfig = RunConfig(),
    raiseExceptions: Boolean = false,
    batchSize: Int? = null,
): EvaluationResult {
    val selectedMetrics = metrics ?: defaultMetricsForDataset(dataset)
    require(selectedMetrics.isNotEmpty()) { "Provide at least one metric for evaluation." }

    validateRequiredColumns(dataset, selectedMetrics)
    validateSupportedMetrics(dataset, selectedMetrics)

    val llmChanged = mutableListOf<MetricWithLlm>()
    val embeddingsChanged = mutableListOf<MetricWithEmbeddings>()

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

    dataset.samples.forEachIndexed { rowIndex, sample ->
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

    return try {
        val flatResults = executor.aresults()
        val rowScores = mutableListOf<Map<String, Any?>>()
        var cursor = 0
        repeat(dataset.samples.size) {
            val row = linkedMapOf<String, Any?>()
            selectedMetrics.forEach { metric ->
                row[metric.name] = flatResults[cursor++]
            }
            rowScores += row
        }

        EvaluationResult(
            scores = rowScores,
            dataset = dataset,
        )
    } finally {
        llmChanged.forEach { metric -> metric.llm = null }
        embeddingsChanged.forEach { metric -> metric.embeddings = null }
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

private fun defaultMetricsForDataset(dataset: EvaluationDataset<out Sample>): List<Metric> =
    when (dataset.getSampleType()) {
        SingleTurnSample::class -> defaultSingleTurnMetrics()
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
