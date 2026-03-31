package ragas.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
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
    private val logger = KotlinLogging.logger {}
    private val jobsLock = Any()
    private val jobs = mutableListOf<Job>()
    private val activeTasksLock = Any()
    private val activeTasks = mutableMapOf<Int, Deferred<ResultEntry>>()
    private val cancelled = AtomicBoolean(false)
    private var jobsProcessed = 0

    fun cancel() {
        cancelled.set(true)
        synchronized(activeTasksLock) {
            activeTasks.values.forEach { task ->
                task.cancel(CancellationException("Executor was cancelled"))
            }
        }
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun submit(
        name: String? = null,
        block: suspend () -> Any?,
    ) {
        synchronized(jobsLock) {
            jobs += Job(index = jobsProcessed++, name = name, block = block)
        }
    }

    fun clearJobs() {
        synchronized(jobsLock) {
            jobs.clear()
            jobsProcessed = 0
        }
    }

    suspend fun aresults(): List<Any?> {
        val queuedJobs =
            synchronized(jobsLock) {
                if (jobs.isEmpty()) {
                    emptyList()
                } else {
                    jobs.toList().also { jobs.clear() }
                }
            }
        if (queuedJobs.isEmpty()) {
            return emptyList()
        }

        val semaphore = Semaphore(runConfig.maxWorkers)
        val entries =
            if (batchSize == null) {
                processJobs(queuedJobs, semaphore)
            } else {
                queuedJobs.chunked(batchSize).flatMap { chunk ->
                    if (isCancelled()) {
                        chunk.map { ResultEntry(index = it.index, value = null) }
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
    ): List<ResultEntry> {
        if (isCancelled()) {
            return queuedJobs.map { ResultEntry(index = it.index, value = null) }
        }

        return coroutineScope {
            val tasks =
                queuedJobs.map { job ->
                    job to
                        async {
                            semaphore.withPermit {
                                runJob(job)
                            }
                        }
                }

            synchronized(activeTasksLock) {
                tasks.forEach { (job, task) -> activeTasks[job.index] = task }
            }

            try {
                tasks.map { (job, task) ->
                    try {
                        task.await()
                    } catch (error: CancellationException) {
                        if (isCancelled()) {
                            ResultEntry(index = job.index, value = null)
                        } else {
                            throw error
                        }
                    }
                }
            } finally {
                synchronized(activeTasksLock) {
                    tasks.forEach { (job, _) -> activeTasks.remove(job.index) }
                }
            }
        }
    }

    private suspend fun runJob(job: Job): ResultEntry {
        if (isCancelled()) {
            return ResultEntry(job.index, null)
        }

        return try {
            val value =
                withTimeout(runConfig.timeoutSeconds * 1_000L) {
                    retryAsync(runConfig) {
                        job.block()
                    }
                }
            ResultEntry(job.index, value)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                if (isCancelled()) {
                    return ResultEntry(job.index, null)
                }
                if (error !is TimeoutCancellationException) {
                    throw error
                }
            }
            if (raiseExceptions) {
                throw error
            }
            val jobLabel = "${job.index}${job.name?.let { " ($it)" } ?: ""}"
            logger.warn(error) { "[$description] Job $jobLabel failed" }
            ResultEntry(job.index, null)
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
