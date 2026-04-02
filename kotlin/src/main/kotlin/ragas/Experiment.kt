package ragas

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import ragas.backends.BACKEND_REGISTRY
import ragas.backends.BaseBackend
import ragas.model.Sample
import java.io.File
import java.io.IOException
import kotlin.random.Random

class Experiment(
    val name: String,
    val backend: BaseBackend,
) {
    private val rows = mutableListOf<Map<String, Any?>>()

    fun append(result: Any?) {
        if (result == null) {
            return
        }
        rows += toRowMap(result)
    }

    fun save() {
        backend.saveExperiment(name, rows.toList())
    }

    fun rows(): List<Map<String, Any?>> = rows.toList()

    companion object {
        internal fun resolveBackend(backend: Any?): BaseBackend =
            when (backend) {
                is BaseBackend -> {
                    backend
                }

                is String -> {
                    BACKEND_REGISTRY.create(backend)
                }

                null -> {
                    throw IllegalArgumentException("Backend cannot be null.")
                }

                else -> {
                    throw IllegalArgumentException(
                        "Unsupported backend type: ${backend::class.qualifiedName}. Expected BaseBackend or String.",
                    )
                }
            }

        private fun toRowMap(result: Any): Map<String, Any?> =
            when (result) {
                is Map<*, *> -> {
                    result.entries.associate { (key, value) ->
                        (key?.toString() ?: "null") to value
                    }
                }

                is Sample -> {
                    result.toMap()
                }

                else -> {
                    mapOf("value" to result)
                }
            }
    }
}

class ExperimentWrapper<T>(
    private val func: suspend (T) -> Any?,
    private val defaultBackend: Any? = null,
    private val namePrefix: String = "",
    private val progressCallback: ((completed: Int, total: Int) -> Unit)? = null,
) {
    suspend operator fun invoke(item: T): Any? = func(item)

    suspend fun arun(
        dataset: Iterable<T>,
        name: String? = null,
        backend: Any? = null,
    ): Experiment {
        val items = dataset.toList()
        val generatedName =
            buildString {
                if (namePrefix.isNotBlank()) {
                    append(namePrefix)
                    append('-')
                }
                append(name ?: generateMemorableName())
            }

        val resolvedBackend =
            when {
                backend != null -> Experiment.resolveBackend(backend)
                defaultBackend != null -> Experiment.resolveBackend(defaultBackend)
                else -> BACKEND_REGISTRY.create("inmemory")
            }

        val experiment = Experiment(name = generatedName, backend = resolvedBackend)
        val total = items.size

        supervisorScope {
            val resultChannel = Channel<Result<Any?>>(capacity = Channel.UNLIMITED)

            items.forEach { item ->
                launch {
                    val result = runCatching { func(item) }
                    resultChannel.send(result)
                }
            }

            var completed = 0
            repeat(total) {
                val result = resultChannel.receive()
                result
                    .onSuccess { value ->
                        if (value != null) {
                            experiment.append(value)
                        }
                    }.onFailure { err ->
                        println("Warning: Task failed with error: ${err.message ?: err::class.simpleName}")
                    }
                completed += 1
                progressCallback?.invoke(completed, total)
            }
        }

        experiment.save()
        return experiment
    }
}

fun <T> experiment(
    backend: Any? = null,
    namePrefix: String = "",
    progressCallback: ((completed: Int, total: Int) -> Unit)? = null,
    block: suspend (T) -> Any?,
): ExperimentWrapper<T> =
    ExperimentWrapper(
        func = block,
        defaultBackend = backend,
        namePrefix = namePrefix,
        progressCallback = progressCallback,
    )

@JvmOverloads
fun versionExperiment(
    experimentName: String,
    commitMessage: String? = null,
    repoPath: String? = null,
    createBranch: Boolean = true,
    stageAll: Boolean = false,
): String {
    val gitRoot = repoPath ?: findGitRoot().absolutePath
    val repoDir = File(gitRoot)
    require(repoDir.exists() && repoDir.isDirectory) { "Invalid repository path: $gitRoot" }

    val hasChanges =
        if (stageAll) {
            val dirty = runGit(repoDir, "status", "--porcelain").isNotBlank()
            if (dirty) {
                println("Staging all changes")
                runGit(repoDir, "add", ".")
            }
            dirty
        } else {
            val dirtyTracked =
                runGit(
                    repoDir,
                    "status",
                    "--porcelain",
                    "--untracked-files=no",
                ).isNotBlank()
            if (dirtyTracked) {
                println("Staging changes to tracked files")
                runGit(repoDir, "add", "-u")
            }
            dirtyTracked
        }

    val commitHash =
        if (hasChanges) {
            val message = commitMessage ?: "Experiment: $experimentName"
            runGit(repoDir, "commit", "-m", message)
            val hash = runGit(repoDir, "rev-parse", "HEAD")
            println("Changes committed with hash: ${hash.take(8)}")
            hash
        } else {
            val hash = runGit(repoDir, "rev-parse", "HEAD")
            println("No changes detected, nothing to commit")
            hash
        }

    val versionName = "ragas/$experimentName"
    if (createBranch) {
        runGit(repoDir, "branch", versionName, commitHash)
        println("Created branch: $versionName")
    }

    return commitHash
}

private fun generateMemorableName(): String {
    val adjectives =
        listOf(
            "bold",
            "brisk",
            "calm",
            "clever",
            "eager",
            "fancy",
            "fresh",
            "keen",
            "lively",
            "swift",
        )
    val nouns =
        listOf(
            "falcon",
            "harbor",
            "matrix",
            "nebula",
            "orbit",
            "river",
            "signal",
            "summit",
            "vector",
            "voyager",
        )
    val random = Random.Default
    val adjective = adjectives[random.nextInt(adjectives.size)]
    val noun = nouns[random.nextInt(nouns.size)]
    val suffix = (System.currentTimeMillis() % 100_000L).toString().padStart(5, '0')
    return "$adjective-$noun-$suffix"
}

private fun findGitRoot(startDir: File = File(".").absoluteFile): File {
    var current: File? = startDir
    while (current != null) {
        if (File(current, ".git").exists()) {
            return current
        }
        current = current.parentFile
    }
    error("Could not find a Git repository from ${startDir.absolutePath}")
}

private fun runGit(
    repoDir: File,
    vararg args: String,
): String {
    val command = mutableListOf("git", "-C", repoDir.absolutePath).apply { addAll(args) }
    val process =
        try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            throw IllegalStateException(
                "versionExperiment() requires git to be installed and available on PATH.",
                e,
            )
        }

    val output =
        process.inputStream
            .bufferedReader()
            .use { reader -> reader.readText() }
            .trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error(
            "git ${args.joinToString(" ")} failed with code $exitCode: $output",
        )
    }
    return output
}
