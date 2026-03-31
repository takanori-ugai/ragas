package ragas.metrics.primitives

class PromptTemplate(
    private val template: String,
) {
    fun render(inputs: Map<String, Any?>): String {
        var rendered = template
        inputs.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value?.toString() ?: "")
        }
        return rendered
    }
}
