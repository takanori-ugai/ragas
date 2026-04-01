package ragas

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ragas.evaluation.EvaluationCallback
import ragas.evaluation.EvaluationCancellationToken
import ragas.evaluation.EvaluationLifecycleCallback
import ragas.evaluation.EvaluationMetricComputedEvent
import ragas.evaluation.EvaluationRunCompletedEvent
import ragas.evaluation.EvaluationRunFailedEvent
import ragas.evaluation.EvaluationRunStartedEvent
import ragas.evaluation.TokenUsage
import ragas.evaluation.aevaluate
import ragas.evaluation.evaluate
import ragas.integrations.tracing.InMemoryTraceObserver
import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunStarted
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvaluatorParityHooksTest {
    @Test
    fun evaluateEmitsLifecycleCallbacksAndTraceObserverEvents() {
        val callback = RecordingLifecycleCallback()
        val trace = InMemoryTraceObserver()
        val dataset = EvaluationDataset(listOf(SingleTurnSample(response = "ok")))

        val result =
            evaluate(
                dataset = dataset,
                metrics = listOf(HooksResponseLengthMetric()),
                callbacks = listOf(callback),
                traceObservers = listOf(trace),
            )

        assertEquals(1, result.scores.size)
        assertTrue(callback.started != null)
        assertTrue(callback.completed != null)
        assertEquals(1, callback.metricEvents.size)
        assertTrue(trace.events.first() is RunStarted)
        assertTrue(trace.events.any { it is MetricRowLogged })
        assertTrue(trace.events.last() is RunCompleted)
    }

    @Test
    fun evaluateSupportsColumnMapForRequiredColumnValidationAndDataProjection() {
        val sample = SingleTurnSample(reference = "fallback-response")
        val dataset = EvaluationDataset(listOf(sample))
        val metric = HooksResponseLengthMetric()

        assertFailsWith<IllegalArgumentException> {
            evaluate(dataset = dataset, metrics = listOf(metric))
        }

        val result =
            evaluate(
                dataset = dataset,
                metrics = listOf(metric),
                columnMap = mapOf("response" to "reference"),
            )

        assertEquals(17.0, result.scores.single()["response_length"])
    }

    @Test
    fun evaluateReportsTokenUsageAndCostThroughCallbacks() {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(response = "a"),
                    SingleTurnSample(response = "bbb"),
                ),
            )
        val usageEvents = mutableListOf<Pair<Int, Int>>()
        val costEvents = mutableListOf<Double>()

        evaluate(
            dataset = dataset,
            metrics = listOf(HooksResponseLengthMetric()),
            tokenUsageParser = { rowIndex, _, _, score ->
                val tokens = (score as Number).toInt()
                TokenUsage(promptTokens = rowIndex + 1, completionTokens = tokens)
            },
            tokenUsageCallback = { event ->
                usageEvents += event.rowIndex to event.usage.totalTokens
            },
            costParser = { usage -> usage.totalTokens * 0.01 },
            costCallback = { event -> costEvents += event.cost },
        )

        assertEquals(listOf(0 to 2, 1 to 5), usageEvents)
        assertEquals(listOf(0.02, 0.05), costEvents)
    }

    @Test
    fun aevaluateProvidesExecutorAndSupportsCancellationToken() =
        runBlocking {
            val token = EvaluationCancellationToken()
            var capturedExecutor: Executor? = null
            val dataset = EvaluationDataset(listOf(SingleTurnSample(response = "slow")))

            val deferred =
                async {
                    aevaluate(
                        dataset = dataset,
                        metrics = listOf(HooksSlowMetric()),
                        executorSink = { executor -> capturedExecutor = executor },
                        cancellationToken = token,
                        returnExecutor = true,
                    )
                }

            while (capturedExecutor == null) {
                delay(10)
            }
            token.cancel()

            val result = deferred.await()
            assertNotNull(capturedExecutor)
            assertTrue(capturedExecutor.isCancelled())
            assertEquals(null, result.scores.single()["slow_metric"])
        }

    @Test
    fun returnExecutorRequiresExecutorSink() {
        val dataset = EvaluationDataset(listOf(SingleTurnSample(response = "x")))
        assertFailsWith<IllegalStateException> {
            evaluate(
                dataset = dataset,
                metrics = listOf(HooksResponseLengthMetric()),
                returnExecutor = true,
            )
        }
    }
}

private class RecordingLifecycleCallback : EvaluationLifecycleCallback {
    var started: EvaluationRunStartedEvent? = null
    var completed: EvaluationRunCompletedEvent? = null
    var failed: EvaluationRunFailedEvent? = null
    val metricEvents = mutableListOf<EvaluationMetricComputedEvent>()

    override fun onRunStarted(event: EvaluationRunStartedEvent) {
        started = event
    }

    override fun onMetricComputed(event: EvaluationMetricComputedEvent) {
        metricEvents += event
    }

    override fun onRunCompleted(event: EvaluationRunCompletedEvent) {
        completed = event
    }

    override fun onRunFailed(event: EvaluationRunFailedEvent) {
        failed = event
    }
}

private class HooksResponseLengthMetric :
    BaseMetric(
        name = "response_length",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val text = sample.response.orEmpty()
        return text.length.toDouble()
    }
}

private class HooksSlowMetric :
    BaseMetric(
        name = "slow_metric",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        delay(500)
        return 1.0
    }
}
