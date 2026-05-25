package ragas.integrations

import com.langchain.smith.client.LangsmithClient
import com.langchain.smith.client.okhttp.LangsmithOkHttpClient
import com.langchain.smith.core.JsonValue
import com.langchain.smith.models.datasets.DataType
import com.langchain.smith.models.datasets.DatasetCreateParams
import com.langchain.smith.models.datasets.runs.RunCreateParams
import com.langchain.smith.models.examples.bulk.BulkCreateParams
import com.langchain.smith.models.runs.RunQueryParams
import com.langchain.smith.models.runs.RunTypeEnum
import com.langchain.smith.models.sessions.SessionCreateParams
import com.langchain.smith.models.sessions.SessionListParams
import ragas.embeddings.BaseRagasEmbedding
import ragas.evaluate
import ragas.integrations.tracing.TraceObserver
import ragas.llms.BaseRagasLlm
import ragas.metrics.Metric
import ragas.model.EvaluationDataset
import ragas.model.EvaluationResult
import ragas.model.SingleTurnSample
import ragas.runtime.RunConfig
import java.time.OffsetDateTime

/**
 * Input record schema for LangSmith integration adapters.
 *
 * @property input User prompt text.
 * @property output Model output text.
 * @property retrievedContexts Retrieved context strings.
 * @property referenceContexts Optional reference context strings.
 * @property reference Optional reference answer.
 * @property metadata Optional record metadata.
 */
data class LangsmithRecord(
    val input: String,
    val output: String,
    val retrievedContexts: List<String> = emptyList(),
    val referenceContexts: List<String> = emptyList(),
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * LangSmith client connectivity options.
 *
 * @property apiKey API key. When null, SDK environment resolution is used.
 * @property baseUrl Optional LangSmith API base URL override.
 * @property tenantId Optional tenant/workspace identifier.
 */
data class LangsmithClientConfig(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val tenantId: String? = null,
)

/**
 * Result payload after uploading a dataset and examples to LangSmith.
 *
 * @property datasetId Created dataset id.
 * @property datasetName Created dataset name.
 * @property exampleIds Created example ids.
 */
data class LangsmithDatasetUploadResult(
    val datasetId: String,
    val datasetName: String,
    val exampleIds: List<String>,
)

/**
 * Summary for one run returned by LangSmith APIs.
 */
data class LangsmithRunResult(
    val runId: String,
    val traceId: String,
    val name: String,
    val status: String,
    val runType: String,
    val projectId: String,
    val error: String?,
    val startTime: OffsetDateTime?,
    val endTime: OffsetDateTime?,
    val totalCost: String?,
    val totalTokens: Long?,
    val inputs: Map<String, Any?>?,
    val outputs: Map<String, Any?>?,
)

/**
 * Example-level remote evaluation output row.
 */
data class LangsmithEvaluatedExample(
    val exampleId: String,
    val datasetId: String,
    val runCount: Int,
    val runs: List<LangsmithRunResult>,
)

/**
 * Result payload from dataset/project remote evaluation endpoints.
 */
data class LangsmithRemoteEvaluationResult(
    val datasetId: String,
    val projectId: String,
    val projectName: String,
    val evaluatedExamples: List<LangsmithEvaluatedExample>,
)

/**
 * Helper functions for evaluating LangSmith records with ragas metrics.
 */
object LangsmithIntegration {
    internal var clientFactory: (LangsmithClientConfig) -> LangsmithClient = { clientConfig ->
        LangsmithOkHttpClient
            .builder()
            .fromEnv()
            .applyClientConfig(clientConfig)
            .build()
    }

    /**
     * Converts integration records into an evaluation dataset.
     *
     * @param records Integration records to process.
     */
    fun toDataset(records: List<LangsmithRecord>): EvaluationDataset<SingleTurnSample> =
        EvaluationDataset(
            records.map { record ->
                SingleTurnSample(
                    userInput = record.input,
                    response = record.output,
                    retrievedContexts = record.retrievedContexts,
                    referenceContexts = record.referenceContexts.ifEmpty { null },
                    reference = record.reference,
                )
            },
        )

    /**
     * Evaluates integration records with the selected metrics and model dependencies.
     *
     * @param records Integration records to process.
     * @param metrics Metrics to run.
     * @param llm LLM dependency used during generation/evaluation.
     * @param embeddings Embedding dependency used during evaluation.
     * @param runConfig Runtime retry/concurrency configuration.
     * @param raiseExceptions Whether metric failures should be thrown.
     * @param runName Logical run name used in tracing output.
     * @param tags Run-level tags.
     * @param metadata Run-level metadata.
     * @param observers Trace observers notified during execution.
     */
    fun evaluateRecords(
        records: List<LangsmithRecord>,
        metrics: List<Metric>? = null,
        llm: BaseRagasLlm? = null,
        embeddings: BaseRagasEmbedding? = null,
        runConfig: RunConfig = RunConfig(),
        raiseExceptions: Boolean = false,
        runName: String = "ragas-langsmith-evaluation",
        tags: Map<String, String> = emptyMap(),
        metadata: Map<String, String> = emptyMap(),
        observers: List<TraceObserver> = emptyList(),
    ): EvaluationResult =
        traceEvaluation(
            framework = "langsmith",
            runName = runName,
            tags = tags,
            metadata = metadata,
            observers = observers,
        ) {
            evaluate(
                dataset = toDataset(records),
                metrics = metrics,
                llm = llm,
                embeddings = embeddings,
                runConfig = runConfig,
                raiseExceptions = raiseExceptions,
            )
        }

    /**
     * Converts evaluation scores into integration-friendly metric rows.
     *
     * @param result Evaluation result payload.
     */
    fun toMetricPayload(result: EvaluationResult): List<Map<String, Any?>> = result.scores

    /**
     * Uploads a dataset and associated examples to LangSmith via REST APIs.
     *
     * This function creates a LangSmith dataset, then uploads [LangsmithRecord] entries
     * as examples in a bulk request with:
     * - inputs: `input`, `retrieved_contexts`, `reference_contexts`
     * - outputs: `output`, `reference`
     * - metadata: record metadata map
     *
     * @param records Records to upload.
     * @param datasetName Dataset name in LangSmith.
     * @param datasetDescription Optional dataset description.
     * @param clientConfig LangSmith client configuration.
     */
    fun uploadDatasetToLangsmith(
        records: List<LangsmithRecord>,
        datasetName: String,
        datasetDescription: String? = null,
        clientConfig: LangsmithClientConfig = LangsmithClientConfig(),
    ): LangsmithDatasetUploadResult =
        withLangsmithClient(clientConfig) { client ->
            val datasetCreateBuilder =
                DatasetCreateParams
                    .builder()
                    .name(datasetName)
                    .dataType(DataType.KV)
            datasetDescription?.let { description -> datasetCreateBuilder.description(description) }
            val createdDataset = client.datasets().create(datasetCreateBuilder.build())

            val exampleIds =
                if (records.isEmpty()) {
                    emptyList()
                } else {
                    val exampleBodies =
                        records.map { record ->
                            val inputs =
                                BulkCreateParams.Body.Inputs
                                    .builder()
                                    .additionalProperties(
                                        toJsonValueMap(
                                            mapOf(
                                                "input" to record.input,
                                                "retrieved_contexts" to record.retrievedContexts.ifEmpty { null },
                                                "reference_contexts" to record.referenceContexts.ifEmpty { null },
                                            ),
                                        ),
                                    ).build()
                            val outputs =
                                BulkCreateParams.Body.Outputs
                                    .builder()
                                    .additionalProperties(
                                        toJsonValueMap(
                                            mapOf(
                                                "output" to record.output,
                                                "reference" to record.reference,
                                            ),
                                        ),
                                    ).build()
                            val exampleBuilder =
                                BulkCreateParams.Body
                                    .builder()
                                    .datasetId(createdDataset.id())
                                    .inputs(inputs)
                                    .outputs(outputs)
                            if (record.metadata.isNotEmpty()) {
                                val metadata =
                                    BulkCreateParams.Body.Metadata
                                        .builder()
                                        .additionalProperties(record.metadata.mapValues { (_, value) -> JsonValue.from(value) })
                                        .build()
                                exampleBuilder.metadata(metadata)
                            }
                            exampleBuilder.build()
                        }
                    client
                        .examples()
                        .bulk()
                        .create(exampleBodies)
                        .map { example -> example.id() }
                }

            LangsmithDatasetUploadResult(
                datasetId = createdDataset.id(),
                datasetName = createdDataset.name(),
                exampleIds = exampleIds,
            )
        }

    /**
     * Runs remote project/dataset evaluation view through LangSmith dataset run APIs.
     *
     * This creates or upserts a project (session) bound to the dataset and then
     * requests dataset run comparisons for that project.
     *
     * @param datasetId Existing LangSmith dataset id.
     * @param projectName Project/session name.
     * @param projectId Optional existing project/session id. When omitted, session upsert is used.
     * @param limit Optional max examples to include.
     * @param preview Whether to request preview response mode.
     * @param includeAnnotatorDetail Whether annotator details should be included.
     * @param format Optional response format selector supported by LangSmith.
     * @param clientConfig LangSmith client configuration.
     */
    fun runRemoteEvaluationViaLangsmith(
        datasetId: String,
        projectName: String,
        projectId: String? = null,
        limit: Long? = null,
        preview: Boolean = false,
        includeAnnotatorDetail: Boolean = false,
        format: String? = null,
        clientConfig: LangsmithClientConfig = LangsmithClientConfig(),
    ): LangsmithRemoteEvaluationResult =
        withLangsmithClient(clientConfig) { client ->
            val resolvedProjectId =
                projectId ?: createOrGetProjectSession(client, projectName = projectName, datasetId = datasetId).id()

            val runCreateBuilder =
                RunCreateParams
                    .builder()
                    .datasetId(datasetId)
                    .addSessionId(resolvedProjectId)
                    .preview(preview)
                    .includeAnnotatorDetail(includeAnnotatorDetail)
            limit?.let(runCreateBuilder::limit)
            format?.let { runCreateBuilder.format(RunCreateParams.Format.of(it)) }

            val examplesWithRuns =
                client
                    .datasets()
                    .runs()
                    .create(runCreateBuilder.build())
                    .orElse(emptyList())
            val evaluatedExamples =
                examplesWithRuns.map { example ->
                    val runs =
                        example.runs().map { run ->
                            LangsmithRunResult(
                                runId = run.id(),
                                traceId = run.traceId(),
                                name = run.name(),
                                status = run.status(),
                                runType = run.runType().asString(),
                                projectId = run.sessionId(),
                                error = run.error().orElse(null),
                                startTime = run.startTime().orElse(null),
                                endTime = run.endTime().orElse(null),
                                totalCost = run.totalCost().orElse(null),
                                totalTokens = run.totalTokens().orElse(null),
                                inputs = run.inputs().map { inputs -> toAnyMap(inputs._additionalProperties()) }.orElse(null),
                                outputs = run.outputs().map { outputs -> toAnyMap(outputs._additionalProperties()) }.orElse(null),
                            )
                        }
                    LangsmithEvaluatedExample(
                        exampleId = example.id(),
                        datasetId = example.datasetId(),
                        runCount = runs.size,
                        runs = runs,
                    )
                }

            LangsmithRemoteEvaluationResult(
                datasetId = datasetId,
                projectId = resolvedProjectId,
                projectName = projectName,
                evaluatedExamples = evaluatedExamples,
            )
        }

    /**
     * Fetches runs/results from LangSmith run query APIs.
     *
     * @param projectId Optional project/session id filter.
     * @param projectName Optional project/session name filter (resolved to ids).
     * @param runIds Optional explicit run ids filter.
     * @param runType Optional run type filter (`chain`, `llm`, `tool`, etc.).
     * @param limit Maximum runs to fetch.
     * @param clientConfig LangSmith client configuration.
     */
    fun fetchRunsFromLangsmith(
        projectId: String? = null,
        projectName: String? = null,
        runIds: List<String> = emptyList(),
        runType: String? = null,
        limit: Long = 100,
        clientConfig: LangsmithClientConfig = LangsmithClientConfig(),
    ): List<LangsmithRunResult> =
        withLangsmithClient(clientConfig) { client ->
            val projectIds =
                when {
                    projectId != null -> listOf(projectId)
                    projectName != null -> resolveProjectIdsByName(client = client, projectName = projectName)
                    else -> emptyList()
                }
            if (projectName != null && projectIds.isEmpty()) {
                throw IllegalArgumentException("LangSmith project '$projectName' was not found.")
            }

            val runQueryBuilder =
                RunQueryParams
                    .builder()
                    .limit(limit)
                    .skipPagination(true)
            projectIds.forEach(runQueryBuilder::addSession)
            runIds.forEach(runQueryBuilder::addId)
            runType?.let { runQueryBuilder.runType(RunTypeEnum.of(it)) }

            val response = client.runs().query(runQueryBuilder.build())
            response.runs().map { run ->
                LangsmithRunResult(
                    runId = run.id(),
                    traceId = run.traceId(),
                    name = run.name(),
                    status = run.status(),
                    runType = run.runType().asString(),
                    projectId = run.sessionId(),
                    error = run.error().orElse(null),
                    startTime = run.startTime().orElse(null),
                    endTime = run.endTime().orElse(null),
                    totalCost = run.totalCost().orElse(null),
                    totalTokens = run.totalTokens().orElse(null),
                    inputs = run.inputs().map { inputs -> toAnyMap(inputs._additionalProperties()) }.orElse(null),
                    outputs = run.outputs().map { outputs -> toAnyMap(outputs._additionalProperties()) }.orElse(null),
                )
            }
        }

    private fun <T> withLangsmithClient(
        clientConfig: LangsmithClientConfig,
        block: (LangsmithClient) -> T,
    ): T {
        val client = clientFactory(clientConfig)
        try {
            return block(client)
        } finally {
            client.close()
        }
    }

    private fun LangsmithOkHttpClient.Builder.applyClientConfig(clientConfig: LangsmithClientConfig): LangsmithOkHttpClient.Builder =
        apply {
            clientConfig.apiKey?.let(::apiKey)
            clientConfig.baseUrl?.let(::baseUrl)
            clientConfig.tenantId?.let(::tenantId)
        }

    private fun createOrGetProjectSession(
        client: LangsmithClient,
        projectName: String,
        datasetId: String,
    ) = client.sessions().create(
        SessionCreateParams
            .builder()
            .name(projectName)
            .referenceDatasetId(datasetId)
            .upsert(true)
            .build(),
    )

    private fun resolveProjectIdsByName(
        client: LangsmithClient,
        projectName: String,
    ): List<String> {
        val params =
            SessionListParams
                .builder()
                .name(projectName)
                .limit(25)
                .build()
        return client
            .sessions()
            .list(params)
            .items()
            .map { session -> session.id() }
    }

    private fun toJsonValueMap(values: Map<String, Any?>): Map<String, JsonValue> =
        values
            .filterValues { value -> value != null }
            .mapValues { (_, value) -> JsonValue.from(value) }

    private fun toAnyMap(values: Map<String, JsonValue>): Map<String, Any?> =
        values.mapValues { (_, value) -> runCatching { value.convert(Any::class.java) }.getOrNull() }
}
