package ragas.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class Executor(
    private val description: String = "Evaluating",
    private val raiseExceptions: Boolean = false,
    private val runConfig: RunConfig = RunConfig(),
    private val batchSize: Int? = null,
) {
    private val jobs = mutableListOf<Job>()
    private val cancelled = AtomicBoolean(false)
    private var jobsProcessed = 0

    fun cancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun submit(
        name: String? = null,
        block: suspend () -> Any?,
    ) {
        jobs += Job(index = jobsProcessed++, name = name, block = block)
    }

    fun clearJobs() {
        jobs.clear()
        jobsProcessed = 0
    }

    suspend fun aresults(): List<Any?> {
        if (jobs.isEmpty()) {
            return emptyList()
        }
        val queuedJobs = jobs.toList()
        jobs.clear()

        val semaphore = Semaphore(runConfig.maxWorkers)
        val entries =
            if (batchSize == null) {
                processJobs(queuedJobs, semaphore)
            } else {
                queuedJobs.chunked(batchSize).flatMap { chunk ->
                    if (isCancelled()) {
                        emptyList()
                    } else {
                        processJobs(chunk, semaphore)
                    }
                }
            }

        return entries.sortedBy { it.index }.map { it.value }
    }

    fun results(): List<Any?> = runBlocking { aresults() }

    private suspend fun processJobs(
        queuedJobs: List<Job>,
        semaphore: Semaphore,
    ): List<ResultEntry> =
        coroutineScope {
            queuedJobs
                .takeIf { !isCancelled() }
                .orEmpty()
                .map { job ->
                    async {
                        semaphore.withPermit {
                            runJob(job)
                        }
                    }
                }.awaitAll()
        }

    private suspend fun runJob(job: Job): ResultEntry {
        if (isCancelled()) {
            return ResultEntry(job.index, null)
        }

        return try {
            val value =
                withTimeout(runConfig.timeoutSeconds * 1_000) {
                    retryAsync(runConfig) {
                        job.block()
                    }
                }
            ResultEntry(job.index, value)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (raiseExceptions) {
                throw error
            }
            println("[$description] Job ${job.index}${job.name?.let { " ($it)" } ?: ""} failed: ${error.message}")
            ResultEntry(job.index, Double.NaN)
        }
    }

    private data class Job(
        val index: Int,
        val name: String?,
        val block: suspend () -> Any?,
    )

    private data class ResultEntry(
        val index: Int,
        val value: Any?,
    )
}
