package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ragas.evaluation.evaluate
import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.MultiTurnMetric
import ragas.model.AiMessage
import ragas.model.EvaluationDataset
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.runtime.Executor
import ragas.runtime.RunConfig

class MultiTurnAndExecutorTest {
    @Test
    fun evaluateRunsMultiTurnMetric() {
        val sample =
            MultiTurnSample(
                userInput =
                    listOf(
                        HumanMessage("Hi"),
                        AiMessage("Hello"),
                    ),
                reference = "Greeting",
            )

        val dataset = EvaluationDataset(listOf(sample))
        val result = evaluate(dataset = dataset, metrics = listOf(ConversationLengthMetric()))

        assertEquals(2.0, result.scores.first()["conversation_length"])
    }

    @Test
    fun executorReturnsNanWhenRaiseExceptionsIsFalse() = runBlocking {
        val executor =
            Executor(
                raiseExceptions = false,
                runConfig = RunConfig(maxRetries = 1, maxWaitSeconds = 0, timeoutSeconds = 1),
            )
        executor.submit(name = "ok") { 1 }
        executor.submit(name = "boom") { error("fail") }

        val results = executor.aresults()
        assertEquals(2, results.size)
        assertEquals(1, results[0])
        assertTrue(results[1] is Double && (results[1] as Double).isNaN())
    }

    @Test
    fun executorPropagatesWhenRaiseExceptionsIsTrue() {
        runBlocking {
            val executor =
                Executor(
                    raiseExceptions = true,
                    runConfig = RunConfig(maxRetries = 1, maxWaitSeconds = 0, timeoutSeconds = 1),
                )
            executor.submit { 1 }
            executor.submit { error("hard fail") }

            assertFailsWith<IllegalStateException> {
                executor.aresults()
            }
        }
    }

    @Test
    fun executorCanBeCancelledBeforeRun() = runBlocking {
        val executor = Executor(runConfig = RunConfig(timeoutSeconds = 1, maxWorkers = 2))
        repeat(3) {
            executor.submit {
                delay(50)
                it
            }
        }

        executor.cancel()
        val results = executor.aresults()
        assertEquals(0, results.size)
    }
}

private class ConversationLengthMetric : BaseMetric(
    name = "conversation_length",
    requiredColumns = mapOf(MetricType.MULTI_TURN to setOf("user_input")),
    outputType = MetricOutputType.CONTINUOUS,
), MultiTurnMetric {
    override suspend fun multiTurnAscore(sample: MultiTurnSample): Any =
        sample.userInput.size.toDouble()
}
