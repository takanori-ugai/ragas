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

class LangsmithIntegrationTest {
    @Test
    fun toDatasetMapsLangsmithRecordFields() {
        val dataset =
            LangsmithIntegration.toDataset(
                listOf(
                    LangsmithRecord(
                        input = "What is Kotlin?",
                        output = "A JVM language.",
                        retrievedContexts = listOf("Kotlin runs on JVM"),
                        referenceContexts = listOf("Kotlin is a JVM language"),
                        reference = "Kotlin is statically typed.",
                    ),
                ),
            )

        val sample = dataset.samples.single()
        assertEquals("What is Kotlin?", sample.userInput)
        assertEquals("A JVM language.", sample.response)
        assertEquals(listOf("Kotlin runs on JVM"), sample.retrievedContexts)
        assertEquals(listOf("Kotlin is a JVM language"), sample.referenceContexts)
        assertEquals("Kotlin is statically typed.", sample.reference)
    }

    @Test
    fun toDatasetMapsEmptyReferenceContextsToNull() {
        val dataset =
            LangsmithIntegration.toDataset(
                listOf(
                    LangsmithRecord(
                        input = "What is Kotlin?",
                        output = "A JVM language.",
                        referenceContexts = emptyList(),
                        reference = "Kotlin is statically typed.",
                    ),
                ),
            )

        assertNull(dataset.samples.single().referenceContexts)
        assertEquals("Kotlin is statically typed.", dataset.samples.single().reference)
    }

    @Test
    fun evaluateRecordsEvaluatesAndEmitsCompletedTrace() {
        val observer = InMemoryTraceObserver()

        val result =
            LangsmithIntegration.evaluateRecords(
                records =
                    listOf(
                        LangsmithRecord(
                            input = "q",
                            output = "a",
                            retrievedContexts = listOf("context"),
                            reference = "a",
                        ),
                    ),
                runName = "langsmith-phase1",
                tags = mapOf("env" to "test"),
                metadata = mapOf("tenant" to "acme"),
                observers = listOf(observer),
            )
        val payload = LangsmithIntegration.toMetricPayload(result)

        assertEquals(1, payload.size)
        assertTrue(payload.first().isNotEmpty())
        val started = observer.events.first() as RunStarted
        assertEquals("langsmith", started.framework)
        assertEquals("langsmith-phase1", started.runName)
        assertEquals(mapOf("env" to "test"), started.tags)
        assertEquals(mapOf("tenant" to "acme"), started.metadata)

        assertTrue(observer.events.any { event -> event is MetricRowLogged })
        assertTrue(observer.events.last() is RunCompleted)
        assertFalse(observer.events.any { event -> event is RunFailed })
    }
}
