<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Text-to-SQL Evaluation Quickstart

Evaluate generated SQL by execution equivalence against expected SQL.

## Run

```sh
./gradlew build
./gradlew execute -PmainClass=ragas.examples.text2sql.EvalsKt
```

## Agent Contract

```kotlin
interface Text2SqlAgent {
    suspend fun generateSql(question: String): String
}
```

## Execution Accuracy

```kotlin
// @compile
fun executionAccuracy(expectedRows: List<List<Any?>>, predictedRows: List<List<Any?>>): String =
    if (expectedRows == predictedRows) "correct" else "incorrect"
```

## DB Connection

```kotlin
// @compile
import java.sql.Connection
import java.sql.DriverManager

fun getDbConnection(): Connection = DriverManager.getConnection("jdbc:sqlite:booksql.db")
```
