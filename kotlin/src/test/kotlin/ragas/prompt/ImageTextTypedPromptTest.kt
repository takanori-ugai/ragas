package ragas.prompt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import ragas.llms.BaseRagasLlm
import ragas.llms.LlmGeneration
import ragas.llms.LlmResult
import ragas.llms.MultiModalRagasLlm
import ragas.runtime.RunConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageTextTypedPromptTest {
    @Test
    fun imageTextPromptBuildsMixedContent() {
        val prompt =
            ImageTextTypedPrompt(
                inputSerializer = ImageQuestionInput.serializer(),
                outputSerializer = ImageQuestionOutput.serializer(),
                model =
                    TypedPromptModel(
                        instruction = "Answer the question using text and image context.",
                    ),
                inputContentBuilder = { input ->
                    listOf(
                        PromptContentPart.Text(input.question),
                        PromptContentPart.ImageUrl(input.imageUrl),
                        PromptContentPart.ImageDataUri(input.inlineImageDataUri),
                    )
                },
            )

        val parts =
            prompt.toContent(
                ImageQuestionInput(
                    question = "What is shown?",
                    imageUrl = "https://example.com/a.png",
                    inlineImageDataUri = "data:image/png;base64,AAAA",
                ),
            )
        val rendered = parts.joinToString("\n") { it.toPromptText() }

        assertTrue(parts.any { it is PromptContentPart.ImageUrl })
        assertTrue(parts.any { it is PromptContentPart.ImageDataUri })
        assertTrue(rendered.contains("[image:url:https://example.com/a.png]"))
        assertTrue(rendered.contains("[image:data-uri]"))
    }

    @Test
    fun imageTextPromptRawItemsBuilderNormalizesUntrustedItems() {
        val prompt =
            ImageTextTypedPrompt(
                inputSerializer = ImageQuestionInput.serializer(),
                outputSerializer = ImageQuestionOutput.serializer(),
                model = TypedPromptModel(instruction = "Use multimodal context."),
                inputItemsBuilder = { input ->
                    listOf(input.question, input.imageUrl, input.inlineImageDataUri)
                },
            )

        val parts =
            prompt.toContent(
                ImageQuestionInput(
                    question = "What is shown?",
                    imageUrl = "http://127.0.0.1/image.png",
                    inlineImageDataUri = "data:image/png;base64,AAAA",
                ),
            )

        assertTrue(parts.any { it == PromptContentPart.Text("http://127.0.0.1/image.png") })
        assertTrue(parts.any { it is PromptContentPart.ImageDataUri })
    }

    @Test
    fun imageTextPromptUsesMultimodalGenerateWithRetry() =
        runBlocking {
            val llm = FakeMultiModalLlm(outputs = listOf("not-json", """{"answer":"cat"}"""))
            val prompt =
                ImageTextTypedPrompt(
                    inputSerializer = ImageQuestionInput.serializer(),
                    outputSerializer = ImageQuestionOutput.serializer(),
                    model = TypedPromptModel(instruction = "Answer from image and text."),
                    inputContentBuilder = { input ->
                        listOf(
                            PromptContentPart.Text(input.question),
                            PromptContentPart.ImageUrl(input.imageUrl),
                        )
                    },
                )

            val result =
                prompt.generate(
                    llm = llm,
                    input =
                        ImageQuestionInput(
                            question = "What animal is this?",
                            imageUrl = "https://example.com/cat.png",
                            inlineImageDataUri = "data:image/png;base64,AAAA",
                        ),
                    config = StructuredOutputParserConfig(maxParseRetries = 1),
                )

            assertEquals(ImageQuestionOutput(answer = "cat"), result)
            assertEquals(2, llm.multimodalCalls)
            assertEquals(0, llm.textCalls)
        }

    @Test
    fun imageTextPromptGenerateMultipleParsesBatch() =
        runBlocking {
            val llm =
                FakeBatchMultiModalLlm(
                    outputsByCall =
                        listOf(
                            listOf("""{"answer":"cat"}""", """{"answer":"dog"}"""),
                        ),
                )
            val prompt =
                ImageTextTypedPrompt(
                    inputSerializer = ImageQuestionInput.serializer(),
                    outputSerializer = ImageQuestionOutput.serializer(),
                    model = TypedPromptModel(instruction = "Answer from image and text."),
                    inputContentBuilder = { input ->
                        listOf(
                            PromptContentPart.Text(input.question),
                            PromptContentPart.ImageUrl(input.imageUrl),
                        )
                    },
                )

            val result =
                prompt.generateMultiple(
                    llm = llm,
                    input =
                        ImageQuestionInput(
                            question = "What animals are shown?",
                            imageUrl = "https://example.com/animals.png",
                            inlineImageDataUri = "data:image/png;base64,AAAA",
                        ),
                    n = 2,
                )

            assertEquals(listOf(ImageQuestionOutput("cat"), ImageQuestionOutput("dog")), result)
            assertEquals(1, llm.multimodalCalls)
        }

    @Test
    fun imageTextPromptFallsBackToTextOnlyLlm() =
        runBlocking {
            val llm = FakeTextOnlyLlm(outputs = listOf("""{"answer":"dog"}"""))
            val prompt =
                ImageTextTypedPrompt(
                    inputSerializer = ImageQuestionInput.serializer(),
                    outputSerializer = ImageQuestionOutput.serializer(),
                    model = TypedPromptModel(instruction = "Answer from image and text."),
                    inputContentBuilder = { input ->
                        listOf(
                            PromptContentPart.Text(input.question),
                            PromptContentPart.ImageUrl(input.imageUrl),
                        )
                    },
                )

            val result =
                prompt.generate(
                    llm = llm,
                    input =
                        ImageQuestionInput(
                            question = "What animal is this?",
                            imageUrl = "https://example.com/dog.png",
                            inlineImageDataUri = "data:image/png;base64,AAAA",
                        ),
                )

            assertEquals(ImageQuestionOutput(answer = "dog"), result)
            assertEquals(1, llm.textCalls)
        }

    @Serializable
    private data class ImageQuestionInput(
        val question: String,
        val imageUrl: String,
        val inlineImageDataUri: String,
    )

    @Serializable
    private data class ImageQuestionOutput(
        val answer: String,
    )

    private class FakeMultiModalLlm(
        private val outputs: List<String>,
    ) : BaseRagasLlm,
        MultiModalRagasLlm {
        override var runConfig: RunConfig = RunConfig()
        var textCalls: Int = 0
        var multimodalCalls: Int = 0

        override suspend fun generateText(
            prompt: String,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult {
            textCalls += 1
            return LlmResult(generations = listOf(LlmGeneration(text = "")))
        }

        override suspend fun generateContent(
            content: List<PromptContentPart>,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult {
            val response = outputs[multimodalCalls]
            multimodalCalls += 1
            return LlmResult(generations = listOf(LlmGeneration(text = response)))
        }
    }

    private class FakeTextOnlyLlm(
        private val outputs: List<String>,
    ) : BaseRagasLlm {
        override var runConfig: RunConfig = RunConfig()
        var textCalls: Int = 0

        override suspend fun generateText(
            prompt: String,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult {
            val response = outputs[textCalls]
            textCalls += 1
            return LlmResult(generations = listOf(LlmGeneration(text = response)))
        }
    }

    private class FakeBatchMultiModalLlm(
        private val outputsByCall: List<List<String>>,
    ) : BaseRagasLlm,
        MultiModalRagasLlm {
        override var runConfig: RunConfig = RunConfig()
        var multimodalCalls: Int = 0

        override suspend fun generateText(
            prompt: String,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult = LlmResult(generations = emptyList())

        override suspend fun generateContent(
            content: List<PromptContentPart>,
            n: Int,
            temperature: Double?,
            stop: List<String>?,
        ): LlmResult {
            val batch = outputsByCall[multimodalCalls]
            multimodalCalls += 1
            return LlmResult(
                generations = batch.take(n).map { output -> LlmGeneration(text = output) },
            )
        }
    }
}
