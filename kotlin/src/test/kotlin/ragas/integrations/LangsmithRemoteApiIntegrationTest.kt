package ragas.integrations

import com.langchain.smith.client.LangsmithClient
import com.langchain.smith.models.datasets.DataType
import com.langchain.smith.models.datasets.Dataset
import com.langchain.smith.models.datasets.DatasetCreateParams
import com.langchain.smith.models.datasets.runs.ExampleWithRunsCh
import com.langchain.smith.models.examples.Example
import com.langchain.smith.models.examples.bulk.BulkCreateParams
import com.langchain.smith.models.runs.RunQueryPage
import com.langchain.smith.models.runs.RunQueryParams
import com.langchain.smith.models.runs.RunSchema
import com.langchain.smith.models.runs.RunTypeEnum
import com.langchain.smith.models.sessions.SessionListPage
import com.langchain.smith.models.sessions.SessionListParams
import com.langchain.smith.models.sessions.TracerSession
import com.langchain.smith.models.sessions.TracerSessionWithoutVirtualFields
import com.langchain.smith.services.blocking.DatasetService
import com.langchain.smith.services.blocking.ExampleService
import com.langchain.smith.services.blocking.RunService
import com.langchain.smith.services.blocking.SessionService
import com.langchain.smith.services.blocking.examples.BulkService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.langchain.smith.models.datasets.runs.RunQueryParams as DatasetRunQueryParams

class LangsmithRemoteApiIntegrationTest {
    private lateinit var originalClientFactory: (LangsmithClientConfig) -> LangsmithClient

    @BeforeTest
    fun setUp() {
        originalClientFactory = LangsmithIntegration.clientFactory
    }

    @AfterTest
    fun tearDown() {
        LangsmithIntegration.clientFactory = originalClientFactory
    }

    @Test
    fun uploadDatasetToLangsmithCreatesDatasetAndExamples() {
        val client = mockk<LangsmithClient>(relaxed = true)
        val datasetService = mockk<DatasetService>()
        val exampleService = mockk<ExampleService>()
        val bulkService = mockk<BulkService>()
        val dataset = mockk<Dataset>()
        val ex1 = mockk<Example>()
        val ex2 = mockk<Example>()

        val datasetParams = slot<DatasetCreateParams>()
        val exampleParams = slot<List<BulkCreateParams.Body>>()

        every { client.datasets() } returns datasetService
        every { client.examples() } returns exampleService
        every { exampleService.bulk() } returns bulkService
        every { datasetService.create(capture(datasetParams)) } returns dataset
        every { dataset.id() } returns "ds-1"
        every { dataset.name() } returns "ragas-dataset"
        every { bulkService.create(capture(exampleParams)) } returns listOf(ex1, ex2)
        every { ex1.id() } returns "ex-1"
        every { ex2.id() } returns "ex-2"

        LangsmithIntegration.clientFactory = { client }

        val result =
            LangsmithIntegration.uploadDatasetToLangsmith(
                records =
                    listOf(
                        LangsmithRecord(
                            input = "q1",
                            output = "a1",
                            retrievedContexts = listOf("ctx1"),
                            referenceContexts = listOf("refctx1"),
                            reference = "ref1",
                            metadata = mapOf("tenant" to "acme"),
                        ),
                        LangsmithRecord(
                            input = "q2",
                            output = "a2",
                        ),
                    ),
                datasetName = "ragas-dataset",
                datasetDescription = "smoke dataset",
            )

        assertEquals("ds-1", result.datasetId)
        assertEquals("ragas-dataset", result.datasetName)
        assertEquals(listOf("ex-1", "ex-2"), result.exampleIds)

        assertEquals("ragas-dataset", datasetParams.captured.name())
        assertEquals("smoke dataset", datasetParams.captured.description().orElse(null))
        assertEquals(DataType.KV, datasetParams.captured.dataType().orElse(null))

        val createdExamples = exampleParams.captured
        assertEquals(2, createdExamples.size)

        assertEquals("ds-1", createdExamples[0].datasetId())
        assertEquals(
            "q1",
            createdExamples[0]
                .inputs()
                .orElseThrow()
                ._additionalProperties()["input"]
                ?.convert(String::class.java),
        )
        assertEquals(
            "a1",
            createdExamples[0]
                .outputs()
                .orElseThrow()
                ._additionalProperties()["output"]
                ?.convert(String::class.java),
        )
        assertEquals(
            "acme",
            createdExamples[0]
                .metadata()
                .orElseThrow()
                ._additionalProperties()["tenant"]
                ?.convert(String::class.java),
        )

        assertEquals("ds-1", createdExamples[1].datasetId())
        assertEquals(
            "q2",
            createdExamples[1]
                .inputs()
                .orElseThrow()
                ._additionalProperties()["input"]
                ?.convert(String::class.java),
        )
        assertEquals(
            "a2",
            createdExamples[1]
                .outputs()
                .orElseThrow()
                ._additionalProperties()["output"]
                ?.convert(String::class.java),
        )

        verify(exactly = 1) { client.close() }
    }

    @Test
    fun runRemoteEvaluationViaLangsmithCreatesSessionAndMapsRuns() {
        val client = mockk<LangsmithClient>(relaxed = true)
        val sessionService = mockk<SessionService>()
        val datasetService = mockk<DatasetService>()
        val datasetRunService = mockk<com.langchain.smith.services.blocking.datasets.RunService>()
        val createdSession = mockk<TracerSessionWithoutVirtualFields>()
        val exampleWithRuns = mockk<ExampleWithRunsCh>()
        val run = mockk<ExampleWithRunsCh.Run>()

        val sessionCreateParams = slot<com.langchain.smith.models.sessions.SessionCreateParams>()
        val runQueryParams = slot<com.langchain.smith.models.datasets.runs.RunQueryParams>()

        every { client.sessions() } returns sessionService
        every { client.datasets() } returns datasetService
        every { sessionService.create(capture(sessionCreateParams)) } returns createdSession
        every { createdSession.id() } returns "proj-1"
        every { datasetService.runs() } returns datasetRunService
        every { datasetRunService.query(capture(runQueryParams)) } returns java.util.Optional.of(listOf(exampleWithRuns))

        every { exampleWithRuns.id() } returns "ex-1"
        every { exampleWithRuns.datasetId() } returns "ds-1"
        every { exampleWithRuns.runs() } returns listOf(run)

        every { run.id() } returns "run-1"
        every { run.traceId() } returns "trace-1"
        every { run.name() } returns "judge-run"
        every { run.status() } returns "success"
        every { run.runType() } returns RunTypeEnum.LLM
        every { run.sessionId() } returns "proj-1"
        every { run.error() } returns java.util.Optional.empty()
        every { run.startTime() } returns java.util.Optional.empty()
        every { run.endTime() } returns java.util.Optional.empty()
        every { run.totalCost() } returns java.util.Optional.of("0.01")
        every { run.totalTokens() } returns java.util.Optional.of(42L)
        every { run.inputs() } returns java.util.Optional.empty()
        every { run.outputs() } returns java.util.Optional.empty()

        LangsmithIntegration.clientFactory = { client }

        val result =
            LangsmithIntegration.runRemoteEvaluationViaLangsmith(
                datasetId = "ds-1",
                projectName = "my-project",
                limit = 10,
                preview = true,
                includeAnnotatorDetail = true,
            )

        assertEquals("ds-1", result.datasetId)
        assertEquals("proj-1", result.projectId)
        assertEquals("my-project", result.projectName)
        assertEquals(1, result.evaluatedExamples.size)
        assertEquals(1, result.evaluatedExamples.single().runCount)
        assertEquals(
            "run-1",
            result.evaluatedExamples
                .single()
                .runs
                .single()
                .runId,
        )

        assertEquals("my-project", sessionCreateParams.captured.name().orElse(null))
        assertEquals("ds-1", sessionCreateParams.captured.referenceDatasetId().orElse(null))
        assertEquals(true, sessionCreateParams.captured.upsert().orElse(false))

        assertEquals("ds-1", runQueryParams.captured.datasetId().orElse(null))
        assertEquals(listOf("proj-1"), runQueryParams.captured.sessionIds())
        assertEquals(10L, runQueryParams.captured.limit().orElse(null))
        assertEquals(true, runQueryParams.captured.preview().orElse(false))
        assertEquals(true, runQueryParams.captured.includeAnnotatorDetail().orElse(false))

        verify(exactly = 1) { client.close() }
    }

    @Test
    fun fetchRunsFromLangsmithResolvesProjectByNameAndQueriesRuns() {
        val client = mockk<LangsmithClient>(relaxed = true)
        val sessionService = mockk<SessionService>()
        val runService = mockk<RunService>()
        val sessionListPage = mockk<SessionListPage>()
        val session = mockk<TracerSession>()
        val runQueryResponse = mockk<RunQueryPage>()
        val run = mockk<RunSchema>()

        val sessionListParams = slot<SessionListParams>()
        val runQueryParams = slot<RunQueryParams>()

        every { client.sessions() } returns sessionService
        every { client.runs() } returns runService
        every { sessionService.list(capture(sessionListParams)) } returns sessionListPage
        every { sessionListPage.items() } returns listOf(session)
        every { session.id() } returns "proj-1"

        every { runService.query(capture(runQueryParams)) } returns runQueryResponse
        every { runQueryResponse.runs() } returns listOf(run)

        every { run.id() } returns "run-1"
        every { run.traceId() } returns "trace-1"
        every { run.name() } returns "eval-run"
        every { run.status() } returns "success"
        every { run.runType() } returns RunTypeEnum.LLM
        every { run.sessionId() } returns "proj-1"
        every { run.error() } returns java.util.Optional.empty()
        every { run.startTime() } returns java.util.Optional.empty()
        every { run.endTime() } returns java.util.Optional.empty()
        every { run.totalCost() } returns java.util.Optional.empty()
        every { run.totalTokens() } returns java.util.Optional.of(123L)
        every { run.inputs() } returns java.util.Optional.empty()
        every { run.outputs() } returns java.util.Optional.empty()

        LangsmithIntegration.clientFactory = { client }

        val runs =
            LangsmithIntegration.fetchRunsFromLangsmith(
                projectName = "my-project",
                runIds = listOf("run-1"),
                runType = "llm",
                limit = 7,
            )

        assertEquals(1, runs.size)
        assertEquals("run-1", runs.single().runId)
        assertEquals("llm", runs.single().runType)

        assertEquals("my-project", sessionListParams.captured.name().orElse(null))
        assertEquals(25L, sessionListParams.captured.limit().orElse(null))

        assertEquals(7L, runQueryParams.captured.limit().orElse(null))
        assertEquals(true, runQueryParams.captured.skipPagination().orElse(false))
        assertEquals(listOf("proj-1"), runQueryParams.captured.session().orElse(emptyList()))
        assertEquals(listOf("run-1"), runQueryParams.captured.id().orElse(emptyList()))
        assertEquals(
            "llm",
            runQueryParams.captured
                .runType()
                .orElseThrow()
                .asString(),
        )

        verify(exactly = 1) { client.close() }
    }

    @Test
    fun fetchRunsFromLangsmithThrowsWhenProjectNameIsMissing() {
        val client = mockk<LangsmithClient>(relaxed = true)
        val sessionService = mockk<SessionService>()
        val sessionListPage = mockk<SessionListPage>()
        every { client.sessions() } returns sessionService
        every { sessionService.list(any<SessionListParams>()) } returns sessionListPage
        every { sessionListPage.items() } returns emptyList()

        LangsmithIntegration.clientFactory = { client }

        val thrown =
            assertFailsWith<IllegalArgumentException> {
                LangsmithIntegration.fetchRunsFromLangsmith(projectName = "missing-project")
            }

        assertTrue(thrown.message!!.contains("missing-project"))
        verify(exactly = 1) { client.close() }
    }
}
