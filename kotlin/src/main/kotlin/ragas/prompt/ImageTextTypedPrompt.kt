package ragas.prompt

import kotlinx.serialization.KSerializer
import ragas.llms.BaseRagasLlm
import ragas.llms.MultiModalRagasLlm

/**
 * Typed prompt that renders mixed text/image content and parses structured output.
 */
class ImageTextTypedPrompt<InputT, OutputT>(
    inputSerializer: KSerializer<InputT>,
    outputSerializer: KSerializer<OutputT>,
    override val model: TypedPromptModel<InputT, OutputT>,
    private val inputContentBuilder: (InputT) -> List<PromptContentPart>,
) : BasePrompt<InputT, OutputT>(
        inputSerializer = inputSerializer,
        outputSerializer = outputSerializer,
        model = model,
    ) {
    constructor(
        inputSerializer: KSerializer<InputT>,
        outputSerializer: KSerializer<OutputT>,
        model: TypedPromptModel<InputT, OutputT>,
        inputItemsBuilder: (InputT) -> List<String>,
        inputPolicy: MultiModalInputPolicy = MultiModalInputPolicy(),
    ) : this(
        inputSerializer = inputSerializer,
        outputSerializer = outputSerializer,
        model = model,
        inputContentBuilder = { input ->
            PromptContentPart.fromUntrustedItems(
                items = inputItemsBuilder(input),
                policy = inputPolicy,
            )
        },
    )

    /**
     * Builds prompt content parts from typed input for multimodal generation.
     *
     * @param input Input payload.
     */
    fun toContent(input: InputT?): List<PromptContentPart> {
        val parts = mutableListOf<PromptContentPart>()
        parts += PromptContentPart.Text(model.instruction)
        parts += PromptContentPart.Text("")

        if (!model.outputJsonSchema.isNullOrBlank()) {
            parts +=
                PromptContentPart.Text(
                    "Please return the output in a JSON format that complies with the following schema as specified in JSON Schema:",
                )
            parts += PromptContentPart.Text(model.outputJsonSchema)
            parts +=
                PromptContentPart.Text(
                    "Do not use single quotes in your response. Use double quotes, properly escaped with a backslash where necessary.",
                )
        } else {
            parts += PromptContentPart.Text("Return JSON only.")
        }

        if (model.examples.isNotEmpty()) {
            parts += PromptContentPart.Text("\n--------EXAMPLES-----------")
            model.examples.forEachIndexed { index, example ->
                parts += PromptContentPart.Text("Example ${index + 1}")
                parts += PromptContentPart.Text("Input: ${json.encodeToString(inputSerializer, example.input)}")
                parts += PromptContentPart.Text("Output: ${json.encodeToString(outputSerializer, example.output)}")
            }
        }

        if (model.includeInputOutputFrame) {
            parts += PromptContentPart.Text("\n-----------------------------")
            parts += PromptContentPart.Text("Now perform the same with the following input")
            if (input == null) {
                parts += PromptContentPart.Text("Input: (None)")
            } else {
                parts += PromptContentPart.Text("Input:")
                parts += inputContentBuilder(input)
            }
            parts += PromptContentPart.Text("Output: ")
        }

        return parts
    }

    /**
     * Renders the prompt text for the provided input.
     */
    override suspend fun format(input: InputT?): String = toContent(input).joinToString(separator = "\n") { it.toPromptText() }

    /**
     * Generates and parses structured output using configured retry behavior.
     */
    override suspend fun generateMultiple(
        llm: BaseRagasLlm,
        input: InputT?,
        n: Int,
        config: StructuredOutputParserConfig,
    ): List<OutputT> {
        require(n > 0) { "n must be > 0" }
        require(config.maxParseRetries >= 0) { "maxParseRetries must be >= 0" }

        val baseContent = toContent(input)
        var currentContent = baseContent
        val failures = mutableListOf<StructuredParseAttempt>()

        repeat(config.maxParseRetries + 1) { attempt ->
            val generations =
                if (llm is MultiModalRagasLlm) {
                    llm
                        .generateContent(
                            content = currentContent,
                            n = n,
                            temperature = config.temperature,
                            stop = config.stop,
                        ).generations
                } else {
                    llm
                        .generateText(
                            prompt = currentContent.joinToString(separator = "\n") { it.toPromptText() },
                            n = n,
                            temperature = config.temperature,
                            stop = config.stop,
                        ).generations
                }

            if (generations.isEmpty()) {
                failures +=
                    StructuredParseAttempt(
                        attempt = attempt + 1,
                        errorMessage = "LLM returned no generations.",
                        rawOutput = "",
                    )
            } else {
                val parsed = mutableListOf<OutputT>()
                var parseError: String? = null
                var rawFailureText: String? = null
                for (generation in generations) {
                    val raw = generation.text
                    val value =
                        runCatching { parse(raw) }.getOrElse { error ->
                            parseError = error.message ?: error::class.simpleName.orEmpty()
                            rawFailureText = raw
                            null
                        }
                    if (value == null) {
                        break
                    }
                    parsed += value
                }
                if (parseError == null) {
                    return parsed
                }
                failures +=
                    StructuredParseAttempt(
                        attempt = attempt + 1,
                        errorMessage = parseError,
                        rawOutput = rawFailureText.orEmpty(),
                    )
            }

            if (attempt < config.maxParseRetries) {
                currentContent = buildRetryContent(baseContent, failures.last().rawOutput, failures.last().errorMessage)
            }
        }

        throw StructuredParseException(failures)
    }

    /**
     * Generates and parses one structured output using configured retry behavior.
     */
    override suspend fun generate(
        llm: BaseRagasLlm,
        input: InputT?,
        config: StructuredOutputParserConfig,
    ): OutputT = generateMultiple(llm = llm, input = input, n = 1, config = config).first()

    private fun buildRetryContent(
        originalContent: List<PromptContentPart>,
        previousRawOutput: String,
        parseError: String,
    ): List<PromptContentPart> =
        originalContent +
            listOf(
                PromptContentPart.Text(""),
                PromptContentPart.Text("Your previous output could not be parsed as valid JSON."),
                PromptContentPart.Text("Parse error: $parseError"),
                PromptContentPart.Text("Previous output:"),
                PromptContentPart.Text(previousRawOutput),
                PromptContentPart.Text("Return only corrected JSON."),
            )
}
