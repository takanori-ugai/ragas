package ragas.examples.rag_eval

import ragas.backends.rowToJsonLine
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

val DOCUMENTS: List<String> =
    listOf(
        "Ragas are melodic frameworks in Indian classical music.",
        "There are many types of ragas, each with its own mood and time of day.",
        "Ragas are used to evoke specific emotions in the listener.",
        "The performance of a raga involves improvisation within a set structure.",
        "Ragas can be performed on various instruments or sung vocally.",
    )

data class TraceEvent(
    val eventType: String,
    val component: String,
    val data: Map<String, Any?>,
)

data class RetrievedDoc(
    val content: String,
    val similarityScore: Int,
    val documentId: Int,
)

data class QueryResult(
    val answer: String,
    val runId: String,
    val logs: String,
)

interface ChatClient {
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
    ): String
}

class EchoChatClient : ChatClient {
    override suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val normalized = userPrompt.replace('\n', ' ')
        return "Based on the retrieved documents: ${normalized.take(220)}"
    }
}

interface BaseRetriever {
    fun fit(documents: List<String>)

    fun getTopK(
        query: String,
        k: Int = 3,
    ): List<Pair<Int, Int>>
}

class SimpleKeywordRetriever : BaseRetriever {
    private val documents = mutableListOf<String>()

    override fun fit(documents: List<String>) {
        this.documents.clear()
        this.documents.addAll(documents)
    }

    override fun getTopK(
        query: String,
        k: Int,
    ): List<Pair<Int, Int>> {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return documents
            .mapIndexed { index, doc ->
                val documentWords = doc.lowercase().split(Regex("\\s+")).toSet()
                val matches = queryWords.count { word -> word in documentWords }
                index to matches
            }.sortedByDescending { (_, matches) -> matches }
            .take(k)
    }
}

class ExampleRag(
    private val chatClient: ChatClient,
    private val retriever: BaseRetriever = SimpleKeywordRetriever(),
    private val systemPrompt: String =
        """
        Answer the following question based on the provided documents:
        Question: {query}
        Documents:
        {context}
        Answer:
        """.trimIndent(),
    private val logDir: String = "logs",
) {
    private val documents = mutableListOf<String>()
    private val traces = mutableListOf<TraceEvent>()
    private var isFitted = false

    init {
        File(logDir).mkdirs()
        traces +=
            TraceEvent(
                eventType = "init",
                component = "rag_system",
                data =
                    mapOf(
                        "retriever_type" to retriever::class.simpleName,
                        "system_prompt_length" to systemPrompt.length,
                        "logdir" to logDir,
                    ),
            )
    }

    fun addDocuments(newDocuments: List<String>) {
        traces +=
            TraceEvent(
                eventType = "document_operation",
                component = "rag_system",
                data =
                    mapOf(
                        "operation" to "add_documents",
                        "num_new_documents" to newDocuments.size,
                        "total_documents_before" to documents.size,
                        "document_lengths" to newDocuments.map { it.length },
                    ),
            )
        documents += newDocuments
        retriever.fit(documents)
        isFitted = true
    }

    fun setDocuments(newDocuments: List<String>) {
        documents.clear()
        documents += newDocuments
        retriever.fit(documents)
        isFitted = true
    }

    fun retrieveDocuments(
        query: String,
        topK: Int = 3,
    ): List<RetrievedDoc> {
        check(isFitted) { "No documents have been added. Call addDocuments() or setDocuments() first." }
        val topDocs = retriever.getTopK(query, topK)
        return topDocs
            .filter { (_, score) -> score > 0 }
            .map { (index, score) ->
                RetrievedDoc(
                    content = documents[index],
                    similarityScore = score,
                    documentId = index,
                )
            }
    }

    suspend fun generateResponse(
        query: String,
        topK: Int = 3,
    ): String {
        val retrieved = retrieveDocuments(query, topK)
        if (retrieved.isEmpty()) {
            return "I couldn't find any relevant documents to answer your question."
        }

        val context =
            retrieved
                .mapIndexed { idx, doc -> "Document ${idx + 1}:\n${doc.content}" }
                .joinToString("\n\n")
        val prompt =
            systemPrompt
                .replace("{query}", query)
                .replace("{context}", context)

        return runCatching {
            chatClient.complete(systemPrompt = systemPrompt, userPrompt = prompt).trim()
        }.getOrElse { error ->
            "Error generating response: ${error.message}"
        }
    }

    suspend fun query(
        question: String,
        topK: Int = 3,
        runId: String? = null,
    ): QueryResult {
        val actualRunId = runId ?: generateRunId(question)
        traces.clear()
        traces +=
            TraceEvent(
                eventType = "query_start",
                component = "rag_system",
                data =
                    mapOf(
                        "run_id" to actualRunId,
                        "question" to question,
                        "question_length" to question.length,
                        "top_k" to topK,
                        "total_documents" to documents.size,
                    ),
            )

        return runCatching {
            val retrieved = retrieveDocuments(question, topK)
            val answer = generateResponse(question, topK)
            traces +=
                TraceEvent(
                    eventType = "query_complete",
                    component = "rag_system",
                    data =
                        mapOf(
                            "run_id" to actualRunId,
                            "success" to true,
                            "response_length" to answer.length,
                            "num_retrieved" to retrieved.size,
                        ),
                )
            val logs = exportTracesToLog(actualRunId, question, mapOf("answer" to answer, "run_id" to actualRunId))
            QueryResult(answer = answer, runId = actualRunId, logs = logs)
        }.getOrElse { error ->
            traces +=
                TraceEvent(
                    eventType = "error",
                    component = "rag_system",
                    data =
                        mapOf(
                            "run_id" to actualRunId,
                            "operation" to "query",
                            "error" to error.message,
                        ),
                )
            val logs = exportTracesToLog(actualRunId, question, null)
            QueryResult(
                answer = "Error processing query: ${error.message}",
                runId = actualRunId,
                logs = logs,
            )
        }
    }

    fun exportTracesToLog(
        runId: String,
        query: String?,
        result: Map<String, Any?>?,
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val filename = "rag_run_${runId}_${timestamp.replace(':', '-').replace('.', '-')}.jsonl"
        val filepath = File(logDir, filename)
        val logRows =
            listOf(
                mapOf(
                    "run_id" to runId,
                    "timestamp" to timestamp,
                    "query" to query,
                    "result" to result,
                    "num_documents" to documents.size,
                    "traces" to traces.map { trace ->
                        mapOf(
                            "event_type" to trace.eventType,
                            "component" to trace.component,
                            "data" to trace.data,
                        )
                    },
                ),
            )
        filepath.parentFile.mkdirs()
        filepath.writeText(logRows.joinToString("\n") { row -> rowToJsonLine(row) } + "\n")
        return filepath.absolutePath
    }

    private fun generateRunId(question: String): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val suffix = (question.hashCode().absoluteValue % 10_000).toString().padStart(4, '0')
        return "${now}_$suffix"
    }
}

fun defaultRagClient(
    chatClient: ChatClient,
    logDir: String = "logs",
): ExampleRag =
    ExampleRag(
        chatClient = chatClient,
        retriever = SimpleKeywordRetriever(),
        logDir = logDir,
    ).also { client ->
        client.addDocuments(DOCUMENTS)
    }
