package ragas.metrics.collections

import kotlinx.coroutines.runBlocking
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.model.AiMessage
import ragas.model.HumanMessage
import ragas.model.MultiTurnSample
import ragas.model.ToolCall
import ragas.model.ToolMessage
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentWorkflowLlmParityTest {
    @Test
    fun goalAccuracyWithReferenceUsesLlmInferenceAndComparison() =
        runBlocking {
            val llm =
                ScriptedAgentWorkflowLlm(
                    responses =
                        listOf(
                            """{"user_goal":"Book a table at Golden Dragon for 8pm.","end_state":"A table was booked at Golden Dragon for 8pm."}""",
                            """{"reason":"The booking request was completed.","verdict":"1"}""",
                        ),
                )
            val metric = AgentGoalAccuracyWithReferenceMetric(maxRetries = 1).apply { this.llm = llm }
            metric.init(RunConfig(seed = 101))

            val score = metric.multiTurnAscore(successfulBookingSample()) as Double

            assertEquals(1.0, score)
            assertEquals(2, llm.prompts.size)
            assertTrue(llm.prompts[0].contains("identify the user_goal"))
            assertTrue(llm.prompts[1].contains("desired_outcome"))
            assertEquals(101, llm.runConfig.seed)
        }

    @Test
    fun goalAccuracyWithoutReferenceUsesLlmInferredGoal() =
        runBlocking {
            val llm =
                ScriptedAgentWorkflowLlm(
                    responses =
                        listOf(
                            """{"user_goal":"Find flights and hotels for Tokyo.","end_state":"Only flights were found and hotel search failed."}""",
                            """{"reason":"The final state does not satisfy the full goal.","verdict":0}""",
                        ),
                )
            val metric = AgentGoalAccuracyWithoutReferenceMetric(maxRetries = 1).apply { this.llm = llm }

            val score = metric.multiTurnAscore(partialTravelPlanningSample()) as Double

            assertEquals(0.0, score)
            assertEquals(2, llm.prompts.size)
        }

    @Test
    fun malformedLlmOutputFallsBackToHeuristicPath() =
        runBlocking {
            val llm = ScriptedAgentWorkflowLlm(responses = listOf("not json"))
            val metric = AgentGoalAccuracyWithReferenceMetric(maxRetries = 1).apply { this.llm = llm }

            val score = metric.multiTurnAscore(successfulBookingSample()) as Double

            assertEquals(1.0, score)
            assertEquals(1, llm.prompts.size)
        }

    @Test
    fun workflowCompletionUsesLlmCompletionScoreWhenAvailable() =
        runBlocking {
            val llm =
                ScriptedAgentWorkflowLlm(
                    responses =
                        listOf(
                            """{"completion_score":0.74,"reason":"Substantial progress with one missing step."}""",
                        ),
                )
            val metric = AgentWorkflowCompletionMetric(maxRetries = 1).apply { this.llm = llm }

            val score = metric.multiTurnAscore(partialTravelPlanningSample()) as Double

            assertEquals(0.74, score, absoluteTolerance = 1e-9)
            assertEquals(1, llm.prompts.size)
            assertTrue(llm.prompts[0].contains("completion_score"))
        }

    @Test
    fun workflowCompletionFallsBackWhenLlmOutputMalformed() =
        runBlocking {
            val llm = ScriptedAgentWorkflowLlm(responses = listOf("""{"reason":"missing score"}"""))
            val metric = AgentWorkflowCompletionMetric(maxRetries = 1).apply { this.llm = llm }

            val score = metric.multiTurnAscore(partialTravelPlanningSample()) as Double

            assertEquals(0.14, score, absoluteTolerance = 1e-9)
            assertEquals(1, llm.prompts.size)
        }

    private fun successfulBookingSample(): MultiTurnSample =
        MultiTurnSample(
            userInput =
                listOf(
                    HumanMessage("Book a table at Golden Dragon for 8pm."),
                    AiMessage(
                        content = "I will place the booking now.",
                        toolCalls = listOf(ToolCall(name = "restaurant_book")),
                    ),
                    ToolMessage("Reservation confirmed at Golden Dragon for 8pm."),
                    AiMessage("Your table at Golden Dragon is booked for 8pm."),
                ),
            reference = "A table is booked at Golden Dragon for 8pm.",
        )

    private fun partialTravelPlanningSample(): MultiTurnSample =
        MultiTurnSample(
            userInput =
                listOf(
                    HumanMessage("Find flights and hotels for Tokyo next weekend."),
                    AiMessage(
                        content = "I will gather both.",
                        toolCalls =
                            listOf(
                                ToolCall(name = "search_flights"),
                                ToolCall(name = "search_hotels"),
                            ),
                    ),
                    ToolMessage("Found multiple flights to Tokyo."),
                    AiMessage("Flights found, but hotel provider returned an error."),
                ),
        )
}

private class ScriptedAgentWorkflowLlm(
    private val responses: List<String>,
) : BaseRagasLlm {
    override var runConfig: RunConfig = RunConfig()
    val prompts: MutableList<String> = mutableListOf()
    private var index: Int = 0

    override suspend fun generateText(
        prompt: String,
        n: Int,
        temperature: Double?,
        stop: List<String>?,
    ): LlmResult {
        prompts += prompt
        val text = responses.getOrElse(index) { responses.lastOrNull().orEmpty() }
        index += 1
        return LlmResult(generations = listOf(LlmGeneration(text)))
    }
}
