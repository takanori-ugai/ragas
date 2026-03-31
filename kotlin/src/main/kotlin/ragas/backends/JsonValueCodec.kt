package ragas.backends

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private val json = Json

fun anyToJsonElement(value: Any?): JsonElement =
    when (value) {
        null -> {
            JsonNull
        }

        is JsonElement -> {
            value
        }

        is String -> {
            JsonPrimitive(value)
        }

        is Number -> {
            JsonPrimitive(value)
        }

        is Boolean -> {
            JsonPrimitive(value)
        }

        is Map<*, *> -> {
            val map = value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) }
            JsonObject(map)
        }

        is List<*> -> {
            JsonArray(value.map { anyToJsonElement(it) })
        }

        else -> {
            throw IllegalArgumentException("Unsupported value type for JSON serialization: ${value::class.qualifiedName}")
        }
    }

fun jsonElementToAny(element: JsonElement): Any? =
    when (element) {
        is JsonNull -> {
            null
        }

        is JsonObject -> {
            element.mapValues { (_, value) -> jsonElementToAny(value) }
        }

        is JsonArray -> {
            element.map { item -> jsonElementToAny(item) }
        }

        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }
        }
    }

fun rowToJsonLine(row: Map<String, Any?>): String {
    val objectElement = JsonObject(row.mapValues { (_, value) -> anyToJsonElement(value) })
    return json.encodeToString(JsonElement.serializer(), objectElement)
}

fun jsonLineToRow(line: String): Map<String, Any?> {
    val element = json.parseToJsonElement(line)
    require(element is JsonObject) { "JSONL row must be a JSON object" }
    return element.mapValues { (_, value) -> jsonElementToAny(value) }
}
