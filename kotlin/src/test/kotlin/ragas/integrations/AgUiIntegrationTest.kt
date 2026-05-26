package ragas.integrations

import ragas.integrations.tracing.InMemoryTraceObserver
import ragas.integrations.tracing.MetricRowLogged
import ragas.integrations.tracing.RunCompleted
import ragas.integrations.tracing.RunFailed
import ragas.integrations.tracing.RunStarted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgUiIntegrationTest {
    @Test
    fun toDatasetMapsAgUiRecordFields() {
        val dataset =
            AgUiIntegration.toDataset(
                listOf(
                    AgUiRecord(
                        input = "What is AG-UI?",
                        output = "A user interface layer for agent workflows.",
                        retrievedContexts = listOf("AG-UI standardizes agent interaction surfaces"),
                        referenceContexts = listOf("Agent UIs expose workflow state and controls"),
                        reference = "AG-UI provides UI conventions for agent systems.",
                    ),
                ),
            )

        val sample = dataset.samples.single()
        assertEquals("What is AG-UI?", sample.userInput)
        assertEquals("A user interface layer for agent workflows.", sample.response)
        assertEquals(listOf("AG-UI standardizes agent interaction surfaces"), sample.retrievedContexts)
        assertEquals(listOf("Agent UIs expose workflow state and controls"), sample.referenceContexts)
        assertEquals("AG-UI provides UI conventions for agent systems.", sample.reference)
    }

    @Test
    fun toDatasetMapsEmptyReferenceContextsToNullAndPreservesReference() {
        val dataset =
            AgUiIntegration.toDataset(
                listOf(
                    AgUiRecord(
                        input = "q",
                        output = "a",
                        referenceContexts = emptyList(),
                        reference = "gold",
                    ),
                ),
            )

        val sample = dataset.samples.single()
        assertNull(sample.referenceContexts)
        assertEquals("gold", sample.reference)
    }

    @Test
    fun evaluateRecordsEvaluatesAndEmitsCompletedTrace() {
        val observer = InMemoryTraceObserver()

        val result =
            AgUiIntegration.evaluateRecords(
                records =
                    listOf(
                        AgUiRecord(
                            input = "q",
                            output = "a",
                            retrievedContexts = listOf("context"),
                            reference = "a",
                        ),
                    ),
                runName = "ag-ui-phase3",
                tags = mapOf("env" to "test"),
                metadata = mapOf("tenant" to "acme"),
                observers = listOf(observer),
            )
        val payload = AgUiIntegration.toMetricPayload(result)

        assertEquals(1, payload.size)
        assertTrue(payload.first().containsKey("answer_relevancy"))
        val started = observer.events.first() as RunStarted
        assertEquals("ag-ui", started.framework)
        assertEquals("ag-ui-phase3", started.runName)
        assertEquals(mapOf("env" to "test"), started.tags)
        assertEquals(mapOf("tenant" to "acme"), started.metadata)

        assertTrue(observer.events.any { event -> event is MetricRowLogged })
        assertTrue(observer.events.last() is RunCompleted)
        assertFalse(observer.events.any { event -> event is RunFailed })
    }
}
