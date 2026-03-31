package ragas.prompt

import java.io.File

interface PromptCollection {
    fun getPrompts(): Map<String, SimplePrompt>

    fun setPrompts(prompts: Map<String, SimplePrompt>)

    fun savePrompts(path: String) {
        val dir = File(path)
        dir.mkdirs()
        getPrompts().forEach { (name, prompt) ->
            prompt.save(File(dir, "$name.json").path)
        }
    }

    fun loadPrompts(path: String): Map<String, SimplePrompt> {
        val dir = File(path)
        require(dir.exists()) { "Path does not exist: $path" }

        return getPrompts().keys.associateWith { name ->
            SimplePrompt.load(File(dir, "$name.json").path)
        }
    }
}
