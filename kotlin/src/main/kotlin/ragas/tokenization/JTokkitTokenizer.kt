package ragas.tokenization

import ragas.tokenizers.DEFAULT_TOKENIZER

/**
 * Shared tokenizer facade backed by JTokkit.
 */
object JTokkitTokenizer {
    fun countTokens(text: String): Int = DEFAULT_TOKENIZER.countTokens(text)
}
