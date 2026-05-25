package ragas.integrations

import ragas.integrations.tracing.InMemoryTraceObserver
import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunStarted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangGraphIntegrationTest {
    @Test
    fun toDatasetMapsLangGraphRecordFields() {
        val dataset =
            LangGraphIntegration.toDataset(
                listOf(
                    LangGraphRecord(
                        input = "What is a graph workflow?",
                        output = "A node and edge based workflow.",
                        retrievedContexts = listOf("LangGraph composes graph-based agents"),
                        referenceContexts = listOf("Graphs model stateful workflows"),
                        reference = "Graph workflows use nodes and edges.",
                    ),
                ),
            )

        val sample = dataset.samples.single()
        assertEquals("What is a graph workflow?", sample.userInput)
        assertEquals("A node and edge based workflow.", sample.response)
        assertEquals(listOf("LangGraph composes graph-based agents"), sample.retrievedContexts)
        assertEquals(listOf("Graphs model stateful workflows"), sample.referenceContexts)
        assertEquals("Graph workflows use nodes and edges.", sample.reference)
    }

    @Test
    fun evaluateRecordsEvaluatesAndEmitsCompletedTrace() {
        val observer = InMemoryTraceObserver()

        val result =
            LangGraphIntegration.evaluateRecords(
                records =
                    listOf(
                        LangGraphRecord(
                            input = "q",
                            output = "a",
                            retrievedContexts = listOf("context"),
                            reference = "a",
                        ),
                    ),
                runName = "langgraph-phase2",
                tags = mapOf("env" to "test"),
                metadata = mapOf("tenant" to "acme"),
                observers = listOf(observer),
            )
        val payload = LangGraphIntegration.toMetricPayload(result)

        assertEquals(1, payload.size)
        assertTrue(payload.first().containsKey("answer_relevancy"))
        val started = observer.events.first() as RunStarted
        assertEquals("langgraph", started.framework)
        assertEquals("langgraph-phase2", started.runName)
        assertEquals(mapOf("env" to "test"), started.tags)
        assertEquals(mapOf("tenant" to "acme"), started.metadata)

        assertTrue(observer.events.any { event -> event is MetricRowLogged })
        assertTrue(observer.events.last() is RunCompleted)
    }
}
