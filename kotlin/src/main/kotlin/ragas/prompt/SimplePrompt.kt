package ragas.prompt

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PromptExample(
    val input: Map<String, String>,
    val output: Map<String, String>,
)

@Serializable
data class SimplePrompt(
    val instruction: String,
    val examples: List<PromptExample> = emptyList(),
) {
    fun format(values: Map<String, Any?>): String {
        var rendered = instruction
        values.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value?.toString().orEmpty())
        }

        if (examples.isEmpty()) {
            return rendered
        }

        val examplesText =
            examples
                .mapIndexed { index, example ->
                    val inputText = example.input.entries.joinToString("\n") { (k, v) -> "$k: $v" }
                    val outputText = example.output.entries.joinToString("\n") { (k, v) -> "$k: $v" }
                    "Example ${index + 1}:\nInput:\n$inputText\nOutput:\n$outputText"
                }.joinToString("\n\n")

        return "$rendered\n\nExamples:\n$examplesText"
    }

    fun addExample(
        input: Map<String, String>,
        output: Map<String, String>,
    ): SimplePrompt = copy(examples = examples + PromptExample(input, output))

    fun save(path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(Json.encodeToString(this))
    }

    companion object {
        fun load(path: String): SimplePrompt {
            val file = File(path)
            require(file.exists()) { "Prompt file not found: $path" }
            return Json.decodeFromString(file.readText())
        }
    }
}
