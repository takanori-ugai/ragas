package ragas.testset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal object WS6TestFixtures {
    fun readFixture(name: String): JsonElement =
        Json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/testset/$name")) {
                "Fixture not found on classpath: fixtures/testset/$name"
            }.bufferedReader().use { it.readText() },
        )
}
