package ragas.metrics.defaults

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal object LlmJsonSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseFirstJsonObject(raw: String): JsonObject? {
        val extracted = extractFirstJsonObject(raw) ?: return null
        return runCatching { json.parseToJsonElement(extracted) as? JsonObject }.getOrNull()
    }

    fun readStringArray(
        root: JsonObject,
        key: String,
    ): List<String> =
        (root[key] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                val primitive = element as? JsonPrimitive ?: return@mapNotNull null
                if (primitive.isString) {
                    primitive.content.trim()
                } else {
                    primitive.content.trim().takeIf { value -> value.isNotBlank() && value != "null" }
                }
            }.filter { value -> value.isNotBlank() }

    fun readIntLike(
        root: JsonObject,
        key: String,
    ): Int? {
        val primitive = root[key] as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        primitive.booleanOrNull?.let { return if (it) 1 else 0 }
        return primitive.content.trim().toIntOrNull()
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) {
            return null
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            if (ch == '"') {
                inString = true
                continue
            }
            if (ch == '{') {
                depth += 1
            }
            if (ch == '}') {
                depth -= 1
                if (depth == 0) {
                    return text.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
