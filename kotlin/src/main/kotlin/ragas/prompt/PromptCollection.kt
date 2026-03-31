package ragas.prompt

import java.io.File

interface PromptCollection {
    fun getPrompts(): Map<String, SimplePrompt>

    fun setPrompts(prompts: Map<String, SimplePrompt>)

    fun savePrompts(path: String) {
        val dir = File(path)
        require(dir.mkdirs() || dir.isDirectory) { "Failed to create directory: $path" }
        getPrompts().forEach { (name, prompt) ->
            prompt.save(File(dir, "${name}_${prompt.language}.json").path)
        }
    }

    fun loadPrompts(
        path: String,
        language: String = "english",
    ): Map<String, SimplePrompt> {
        val dir = File(path)
        require(dir.isDirectory) { "Path is not a directory: $path" }

        return getPrompts().keys.associateWith { name ->
            val languagePath = File(dir, "${name}_$language.json")
            val legacyPath = File(dir, "$name.json")
            when {
                languagePath.exists() -> SimplePrompt.load(languagePath.path)
                legacyPath.exists() -> SimplePrompt.load(legacyPath.path)
                else -> SimplePrompt.load(languagePath.path)
            }
        }
    }
}
