package ragas.backends

import java.io.File

class LocalJsonlBackend(
    private val rootDir: String,
) : BaseBackend {
    private fun getDataDir(dataType: String): File = File(rootDir, dataType)

    private fun getFile(dataType: String, name: String): File = File(getDataDir(dataType), "$name.jsonl")

    private fun load(dataType: String, name: String): List<Map<String, Any?>> {
        val file = getFile(dataType, name)
        if (!file.exists()) {
            throw java.io.FileNotFoundException("No ${dataType.dropLast(1)} named '$name' found at ${file.path}")
        }
        if (file.readText().isBlank()) {
            return emptyList()
        }

        return file.readLines()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .map { line -> jsonLineToRow(line) }
    }

    private fun save(
        dataType: String,
        name: String,
        data: List<Map<String, Any?>>,
    ) {
        val file = getFile(dataType, name)
        file.parentFile.mkdirs()

        if (data.isEmpty()) {
            file.writeText("")
            return
        }

        val content = data.joinToString(separator = "\n") { row -> rowToJsonLine(row) } + "\n"
        file.writeText(content)
    }

    private fun list(dataType: String): List<String> {
        val dir = getDataDir(dataType)
        if (!dir.exists()) {
            return emptyList()
        }

        return dir.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.map { file -> file.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    override fun loadDataset(name: String): List<Map<String, Any?>> = load("datasets", name)

    override fun loadExperiment(name: String): List<Map<String, Any?>> = load("experiments", name)

    override fun saveDataset(
        name: String,
        data: List<Map<String, Any?>>,
    ) {
        save("datasets", name, data)
    }

    override fun saveExperiment(
        name: String,
        data: List<Map<String, Any?>>,
    ) {
        save("experiments", name, data)
    }

    override fun listDatasets(): List<String> = list("datasets")

    override fun listExperiments(): List<String> = list("experiments")

    override fun toString(): String = "LocalJsonlBackend(rootDir='$rootDir')"
}
