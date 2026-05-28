package ragas.tokenizers

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.IntArrayList

/**
 * Abstract tokenizer contract matching Python ragas tokenizer semantics.
 */
interface BaseTokenizer {
    /**
     * Encodes text into token IDs.
     */
    fun encode(text: String): List<Int>

    /**
     * Decodes token IDs back into text.
     */
    fun decode(tokens: List<Int>): String

    /**
     * Counts tokens in text.
     */
    fun countTokens(text: String): Int = encode(text).size
}

/**
 * JTokkit-backed tokenizer wrapper (Python parity name retained).
 *
 * If none of [encoding], [modelName], or [encodingName] is provided, defaults to `o200k_base`.
 */
class TiktokenWrapper(
    encoding: Encoding? = null,
    modelName: String? = null,
    encodingName: String? = null,
) : BaseTokenizer {
    val encoding: Encoding =
        when {
            encoding != null -> {
                encoding
            }

            modelName != null -> {
                REGISTRY
                    .getEncodingForModel(modelName)
                    .orElseThrow { IllegalArgumentException("Unknown model name: $modelName") }
            }

            encodingName != null -> {
                REGISTRY
                    .getEncoding(encodingName)
                    .orElseThrow { IllegalArgumentException("Unknown encoding name: $encodingName") }
            }

            else -> {
                REGISTRY.getEncoding(EncodingType.O200K_BASE)
            }
        }

    override fun encode(text: String): List<Int> = encoding.encode(text).boxed()

    override fun decode(tokens: List<Int>): String = encoding.decode(tokens.toIntArrayList())

    override fun countTokens(text: String): Int = encoding.countTokens(text)

    companion object {
        private val REGISTRY = Encodings.newLazyEncodingRegistry()
    }
}

/**
 * Wrapper for HuggingFace-style tokenizer adapters.
 *
 * Kotlin core does not bundle a HuggingFace runtime; use [fromFunctions] to bridge one.
 */
class HuggingFaceTokenizer private constructor(
    private val encodeFn: (String) -> List<Int>,
    private val decodeFn: (List<Int>) -> String,
) : BaseTokenizer {
    override fun encode(text: String): List<Int> = encodeFn(text)

    override fun decode(tokens: List<Int>): String = decodeFn(tokens)

    companion object {
        /**
         * Creates a HuggingFace-compatible wrapper from encode/decode adapters.
         */
        fun fromFunctions(
            encode: (String) -> List<Int>,
            decode: (List<Int>) -> String,
        ): HuggingFaceTokenizer = HuggingFaceTokenizer(encode, decode)
    }
}

@Volatile
private var defaultTokenizerRef: TiktokenWrapper? = null

/**
 * Returns the process-wide default tokenizer singleton (`o200k_base`).
 */
fun getDefaultTokenizer(): TiktokenWrapper {
    val cached = defaultTokenizerRef
    if (cached != null) {
        return cached
    }
    return synchronized(Unit) {
        val synchronizedCached = defaultTokenizerRef
        if (synchronizedCached != null) {
            synchronizedCached
        } else {
            TiktokenWrapper(encodingName = "o200k_base").also { created ->
                defaultTokenizerRef = created
            }
        }
    }
}

private object LazyDefaultTokenizer : BaseTokenizer {
    override fun encode(text: String): List<Int> = getDefaultTokenizer().encode(text)

    override fun decode(tokens: List<Int>): String = getDefaultTokenizer().decode(tokens)

    override fun countTokens(text: String): Int = getDefaultTokenizer().countTokens(text)
}

/**
 * Backwards-compatible lazy default tokenizer handle.
 */
val DEFAULT_TOKENIZER: BaseTokenizer = LazyDefaultTokenizer

/**
 * Factory function to construct tokenizer instances.
 *
 * Supported [tokenizerType] values:
 * - `"tiktoken"` (JTokkit-backed)
 * - `"huggingface"` (unsupported in Kotlin core; use [HuggingFaceTokenizer.fromFunctions])
 */
fun getTokenizer(
    tokenizerType: String = "tiktoken",
    modelName: String? = null,
    encodingName: String? = null,
): BaseTokenizer =
    when (tokenizerType.lowercase()) {
        "tiktoken" -> {
            TiktokenWrapper(modelName = modelName, encodingName = encodingName)
        }

        "huggingface" -> {
            throw UnsupportedOperationException(
                "HuggingFace tokenizer loading is not bundled in Kotlin core. " +
                    "Use HuggingFaceTokenizer.fromFunctions(...) to provide adapters.",
            )
        }

        else -> {
            throw IllegalArgumentException("Unknown tokenizer type: $tokenizerType")
        }
    }

private fun List<Int>.toIntArrayList(): IntArrayList =
    IntArrayList(size).also { list ->
        forEach { token -> list.add(token) }
    }
