package ragas

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ragas.evaluation.CostEstimate
import ragas.evaluation.EvaluationCallback
import ragas.evaluation.EvaluationEvent
import ragas.evaluation.TokenUsage
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MetricWithLlm
import ragas.metrics.SingleTurnMetric
import ragas.model.EvaluationDataset
import ragas.model.SingleTurnSample
import ragas.runtime.Executor
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluationParityHooksTest {
    @Test
    fun evaluateSupportsColumnRemapAliases() {
        val dataset =
            EvaluationDataset(
                listOf(
                    SingleTurnSample(
                        userInput = null,
                        reference = "remapped-question",
                        response = "answer",
                    ),
                ),
            )

        val result =
            evaluate(
                dataset = dataset,
                metrics = listOf(UserInputEchoMetric()),
                columnMap = mapOf("user_input" to "reference"),
            )

        assertEquals("remapped-question", result.scores.single()["user_input_echo"])
    }

    @Test
    fun evaluateEmitsCallbacksAndTokenCostHooks() {
        val events = mutableListOf<EvaluationEvent>()
        val callback = EvaluationCallback { event -> events += event }
        val dataset = EvaluationDataset(listOf(SingleTurnSample(userInput = "q", response = "r")))
        val metric = LlmEchoMetric()

        val result =
            evaluate(
                dataset = dataset,
                metrics = listOf(metric),
                llm = EchoLlm("generated answer"),
                callbacks = listOf(callback),
                tokenUsageParser = { _, _ -> TokenUsage(promptTokens = 3, completionTokens = 2) },
                costParser = { usage -> CostEstimate(amount = usage.totalTokens * 0.001) },
            )

        assertEquals("generated answer", result.scores.single()["llm_echo"])
        assertTrue(events.any { event -> event is EvaluationEvent.RunStarted })
        assertTrue(events.any { event -> event is EvaluationEvent.ExecutorReady })
        assertTrue(events.any { event -> event is EvaluationEvent.MetricComputed })
        assertTrue(events.any { event -> event is EvaluationEvent.RowCompleted })
        assertTrue(events.any { event -> event is EvaluationEvent.TokenUsageComputed })
        assertTrue(events.any { event -> event is EvaluationEvent.CostComputed })
        assertTrue(events.any { event -> event is EvaluationEvent.RunCompleted })
    }

    @Test
    fun executorObserverCanCancelEvaluation() =
        runBlocking {
            val dataset = EvaluationDataset(listOf(SingleTurnSample(response = "r1"), SingleTurnSample(response = "r2")))
            val metric = SlowMetric()
            lateinit var captured: Executor

            val result =
                aevaluate(
                    dataset = dataset,
                    metrics = listOf(metric),
                    executorObserver = { executor ->
                        captured = executor
                        executor.cancel()
                    },
                )

            assertTrue(captured.isCancelled())
            assertEquals(null, result.scores[0]["slow_metric"])
            assertEquals(null, result.scores[1]["slow_metric"])
        }
}

private class UserInputEchoMetric :
    BaseMetric(
        name = "user_input_echo",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("user_input")),
        outputType = MetricOutputType.DISCRETE,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any = sample.userInput.orEmpty()
}

private class LlmEchoMetric :
    BaseMetric(
        name = "llm_echo",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
        outputType = MetricOutputType.DISCRETE,
    ),
    SingleTurnMetric,
    MetricWithLlm {
    override var llm: BaseRagasLlm? = null

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any =
        checkNotNull(llm)
            .generateText("prompt: ${sample.response.orEmpty()}")
            .generations
            .firstOrNull()
            ?.text
            .orEmpty()
}

private class EchoLlm(
    private val output: String,
) : BaseRagasLlm {
    override var runConfig: RunConfig = RunConfig()

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult = LlmResult(generations = listOf(LlmGeneration(output)))
}

private class SlowMetric :
    BaseMetric(
        name = "slow_metric",
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        delay(250)
        return 1.0
    }
}
