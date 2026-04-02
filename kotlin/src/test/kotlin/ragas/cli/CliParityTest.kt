package ragas.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliParityTest {
    @Test
    fun evalCommandProducesReportJson() {
        val dir = createTempDirectory("ragas-cli-eval").toFile()
        val dataset = dir.resolve("dataset.json")
        val report = dir.resolve("report.json")

        dataset.writeText(
            """
            [
              {
                "user_input": "What is Kotlin?",
                "response": "Kotlin is a JVM language.",
                "retrieved_contexts": ["Kotlin is a statically typed language for JVM and Android."],
                "reference_contexts": ["Kotlin targets JVM and Android."],
                "reference": "Kotlin is a language for JVM and Android."
              }
            ]
            """.trimIndent(),
        )

        val code = runCli(arrayOf("eval", "--input", dataset.absolutePath, "--output", report.absolutePath, "--metrics", "default"))

        assertEquals(0, code)
        assertTrue(report.exists())

        val parsed = Json.parseToJsonElement(report.readText()).jsonObject
        assertEquals(
            1,
            parsed
                .getValue("row_count")
                .jsonPrimitive.content
                .toInt(),
        )
        assertTrue(parsed.containsKey("scores"))
        assertTrue(parsed.containsKey("metric_means"))
    }

    @Test
    fun reportCommandAggregatesScores() {
        val dir = createTempDirectory("ragas-cli-report").toFile()
        val report = dir.resolve("input-report.json")
        val summary = dir.resolve("summary.json")

        report.writeText(
            """
            {
              "scores": [
                {"m1": 0.5, "m2": 0.2},
                {"m1": 0.7, "m2": 0.4}
              ]
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("report", "--input", report.absolutePath, "--output", summary.absolutePath))

        assertEquals(0, code)
        val parsed = Json.parseToJsonElement(summary.readText()).jsonObject
        val means = parsed.getValue("metric_means").jsonObject
        assertEquals(
            0.6,
            means
                .getValue("m1")
                .jsonPrimitive.content
                .toDouble(),
            1e-9,
        )
        assertEquals(
            0.3,
            means
                .getValue("m2")
                .jsonPrimitive.content
                .toDouble(),
            1e-9,
        )
    }

    @Test
    fun compareCommandReturnsNonZeroWhenGateFails() {
        val dir = createTempDirectory("ragas-cli-compare").toFile()
        val baseline = dir.resolve("baseline.json")
        val candidate = dir.resolve("candidate.json")

        baseline.writeText("""{"scores":[{"faithfulness":0.5},{"faithfulness":0.7}]}""")
        candidate.writeText("""{"scores":[{"faithfulness":0.55},{"faithfulness":0.65}]}""")

        val failCode =
            runCli(
                arrayOf(
                    "compare",
                    "--baseline",
                    baseline.absolutePath,
                    "--candidate",
                    candidate.absolutePath,
                    "--gate",
                    "faithfulness=0.10",
                ),
            )
        assertEquals(2, failCode)

        val passCode =
            runCli(
                arrayOf(
                    "compare",
                    "--baseline",
                    baseline.absolutePath,
                    "--candidate",
                    candidate.absolutePath,
                    "--gate",
                    "faithfulness=-0.10",
                ),
            )
        assertEquals(0, passCode)
    }
}
