package ragas.metrics.collections

import ragas.metrics.BaseMetric
import ragas.metrics.MetricOutputType
import ragas.metrics.MetricType
import ragas.metrics.SingleTurnMetric
import ragas.model.SingleTurnSample
import kotlin.math.max

class DataCompyScoreMetric(
    private val mode: Mode = Mode.ROWS,
    private val metric: Metric = Metric.F1,
    name: String = "data_compare_score",
) : BaseMetric(
        name = name,
        requiredColumns = mapOf(MetricType.SINGLE_TURN to setOf("reference", "response")),
        outputType = MetricOutputType.CONTINUOUS,
    ),
    SingleTurnMetric {
    enum class Mode {
        ROWS,
        COLUMNS,
    }

    enum class Metric {
        PRECISION,
        RECALL,
        F1,
    }

    override suspend fun singleTurnAscore(sample: SingleTurnSample): Any {
        val reference = sample.reference.orEmpty()
        val response = sample.response.orEmpty()

        val referenceTable = parseCsv(reference) ?: return Double.NaN
        val responseTable = parseCsv(response) ?: return Double.NaN

        val (precision, recall) =
            when (mode) {
                Mode.ROWS -> rowPrecisionRecall(referenceTable, responseTable)
                Mode.COLUMNS -> columnPrecisionRecall(referenceTable, responseTable)
            }

        return when (metric) {
            Metric.PRECISION -> {
                precision
            }

            Metric.RECALL -> {
                recall
            }

            Metric.F1 -> {
                if (precision + recall == 0.0) {
                    0.0
                } else {
                    2.0 * precision * recall / (precision + recall)
                }
            }
        }
    }

    private fun rowPrecisionRecall(
        reference: CsvTable,
        response: CsvTable,
    ): Pair<Double, Double> {
        val matchingRows = countMatchingRows(reference, response)
        val recall = if (reference.rows.isEmpty()) 0.0 else matchingRows.toDouble() / reference.rows.size.toDouble()
        val precision = if (response.rows.isEmpty()) 0.0 else matchingRows.toDouble() / response.rows.size.toDouble()
        return precision to recall
    }

    private fun countMatchingRows(
        reference: CsvTable,
        response: CsvTable,
    ): Int {
        val columns = reference.header.union(response.header)
        val maxRows = max(reference.rows.size, response.rows.size)
        var count = 0
        for (idx in 0 until maxRows) {
            val leftRow = reference.rows.getOrNull(idx).orEmpty()
            val rightRow = response.rows.getOrNull(idx).orEmpty()
            val matches =
                columns.all { column ->
                    leftRow[column].orEmpty() == rightRow[column].orEmpty()
                }
            if (matches) {
                count += 1
            }
        }
        return count
    }

    private fun columnPrecisionRecall(
        reference: CsvTable,
        response: CsvTable,
    ): Pair<Double, Double> {
        val referenceColumns = reference.header
        val responseColumns = response.header
        val matchedColumns = referenceColumns.intersect(responseColumns).count { column -> columnsMatch(reference, response, column) }

        val recall = if (referenceColumns.isEmpty()) 0.0 else matchedColumns.toDouble() / referenceColumns.size.toDouble()
        val precision = if (responseColumns.isEmpty()) 0.0 else matchedColumns.toDouble() / responseColumns.size.toDouble()
        return precision to recall
    }

    private fun columnsMatch(
        reference: CsvTable,
        response: CsvTable,
        column: String,
    ): Boolean {
        val maxRows = max(reference.rows.size, response.rows.size)
        for (idx in 0 until maxRows) {
            val left =
                reference.rows
                    .getOrNull(idx)
                    ?.get(column)
                    .orEmpty()
            val right =
                response.rows
                    .getOrNull(idx)
                    ?.get(column)
                    .orEmpty()
            if (left != right) {
                return false
            }
        }
        return true
    }

    private fun parseCsv(text: String): CsvTable? {
        val lines = splitCsvLines(text)
        if (lines.isEmpty()) {
            return null
        }

        val header = parseCsvLine(lines.first()) ?: return null
        if (header.isEmpty()) {
            return null
        }

        val rows =
            lines
                .drop(1)
                .mapNotNull { line ->
                    val parsed = parseCsvLine(line) ?: return null
                    val normalized = parsed + List((header.size - parsed.size).coerceAtLeast(0)) { "" }
                    header.zip(normalized.take(header.size)).toMap()
                }
        return CsvTable(header = header.toSet(), rows = rows)
    }

    private fun splitCsvLines(text: String): List<String> =
        text
            .trim()
            .split(Regex("\\r?\\n"))
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

    private fun parseCsvLine(line: String): List<String>? {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"')
                    i += 1
                }

                ch == '"' -> {
                    inQuotes = !inQuotes
                }

                ch == ',' && !inQuotes -> {
                    cells += cell.toString().trim()
                    cell.clear()
                }

                else -> {
                    cell.append(ch)
                }
            }
            i += 1
        }

        if (inQuotes) {
            return null
        }
        cells += cell.toString().trim()
        return cells
    }

    private data class CsvTable(
        val header: Set<String>,
        val rows: List<Map<String, String>>,
    )
}
