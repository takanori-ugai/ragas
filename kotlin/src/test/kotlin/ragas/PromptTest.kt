package ragas

import ragas.prompt.PromptCollection
import ragas.prompt.SimplePrompt
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptTest {
    @Test
    fun simplePromptFormatsAndPersists() {
        val prompt =
            SimplePrompt("Answer: {question}")
                .addExample(
                    input = mapOf("question" to "1+1"),
                    output = mapOf("answer" to "2"),
                )

        val formatted = prompt.format(mapOf("question" to "2+2"))
        assertTrue(formatted.contains("Answer: 2+2"))
        assertTrue(formatted.contains("Examples:"))

        val dir = createTempDirectory("ragas-prompt-test").toFile()
        try {
            val path = dir.resolve("prompt.json").absolutePath
            prompt.save(path)

            val loaded = SimplePrompt.load(path)
            assertEquals(prompt.instruction, loaded.instruction)
            assertEquals(prompt.examples.size, loaded.examples.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun promptCollectionSaveAndLoad() {
        val holder =
            DummyPromptHolder(
                mutableMapOf(
                    "p1" to SimplePrompt("Hello {name}"),
                    "p2" to SimplePrompt("Bye {name}"),
                ),
            )
        val dir = createTempDirectory("ragas-prompts").toFile()
        try {
            holder.savePrompts(dir.absolutePath)

            val loaded = holder.loadPrompts(dir.absolutePath)
            assertEquals(2, loaded.size)
            assertEquals("Hello {name}", loaded.getValue("p1").instruction)
        } finally {
            dir.deleteRecursively()
        }
    }
}

private class DummyPromptHolder(
    private var prompts: MutableMap<String, SimplePrompt>,
) : PromptCollection {
    override fun getPrompts(): Map<String, SimplePrompt> = prompts

    override fun setPrompts(prompts: Map<String, SimplePrompt>) {
        this.prompts = prompts.toMutableMap()
    }
}
