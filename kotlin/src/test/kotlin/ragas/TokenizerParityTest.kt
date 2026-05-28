package ragas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TokenizerParityTest {
    @Test
    fun defaultTokenizerEncodesAndDecodesRoundTrip() {
        val text = "Hello world"
        val tokens = DEFAULT_TOKENIZER.encode(text)
        val decoded = DEFAULT_TOKENIZER.decode(tokens)

        assertTrue(tokens.isNotEmpty())
        assertEquals(text, decoded)
    }

    @Test
    fun getDefaultTokenizerIsSingleton() {
        val left = getDefaultTokenizer()
        val right = getDefaultTokenizer()
        assertSame(left, right)
    }

    @Test
    fun defaultTokenizerCanBeUsedAsFieldDefault() {
        data class Holder(
            val tokenizer: BaseTokenizer = DEFAULT_TOKENIZER,
        )

        val holder = Holder()
        assertTrue(holder.tokenizer.encode("test").isNotEmpty())
    }

    @Test
    fun getTokenizerRejectsUnknownType() {
        assertFailsWith<IllegalArgumentException> {
            getTokenizer(tokenizerType = "unknown")
        }
    }
}
