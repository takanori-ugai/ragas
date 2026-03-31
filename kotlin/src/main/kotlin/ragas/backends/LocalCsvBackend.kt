package ragas.backends

import java.io.File

class LocalCsvBackend(
    private val rootDir: String,
) : BaseBackend {
    private fun getDataDir(dataType: String): File = File(rootDir, dataType)

    private fun getFile(
        dataType: String,
        name: String,
    ): File = File(getDataDir(dataType), "$name.csv")

    private fun load(
        dataType: String,
        name: String,
    ): List<Map<String, Any?>> {
        val file = getFile(dataType, name)
        if (!file.exists()) {
            throw java.io.FileNotFoundException(
                "No ${dataType.dropLast(1)} named '$name' found at ${file.path}",
            )
        }
        val lines = file.readLines()
        if (lines.isEmpty()) {
            return emptyList()
        }

        val headers = parseCsvLine(lines.first())
        return lines
            .drop(1)
            .filter { line -> line.isNotBlank() }
            .map { line ->
                val cells = parseCsvLine(line)
                headers
                    .mapIndexed { index, header ->
                        header to cells.getOrElse(index) { "" }
                    }.toMap()
            }
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

        val headers = data.first().keys.toList()
        val builder = StringBuilder()
        builder
            .append(headers.joinToString(",") { header -> csvEscape(header) })
            .append('\n')
        data.forEach { row ->
            val line =
                headers.joinToString(",") { key ->
                    csvEscape(row[key]?.toString().orEmpty())
                }
            builder.append(line).append('\n')
        }
        file.writeText(builder.toString())
    }

    private fun list(dataType: String): List<String> {
        val dir = getDataDir(dataType)
        if (!dir.exists()) {
            return emptyList()
        }

        return dir
            .listFiles { file -> file.isFile && file.extension == "csv" }
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

    override fun toString(): String = "LocalCsvBackend(rootDir='$rootDir')"

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        if (!needsQuotes) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 1
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i += 1
        }

        result += current.toString()
        return result
    }
}
