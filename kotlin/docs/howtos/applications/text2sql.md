<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Text-to-SQL Evaluation

Evaluate generated SQL by execution-result equivalence.

```kotlin
interface Text2SqlAgent {
    suspend fun query(nlQuestion: String): String
}
```

```kotlin
import java.sql.Connection

fun executeSql(connection: Connection, sql: String): List<List<Any?>> {
    val rows = mutableListOf<List<Any?>>()
    connection.createStatement().use { stmt ->
        stmt.executeQuery(sql).use { rs ->
            val meta = rs.metaData
            while (rs.next()) {
                rows += (1..meta.columnCount).map { idx -> rs.getObject(idx) }
            }
        }
    }
    return rows
}

fun executionAccuracy(expectedRows: List<List<Any?>>, predictedRows: List<List<Any?>>): String =
    if (expectedRows == predictedRows) "correct" else "incorrect"
```
