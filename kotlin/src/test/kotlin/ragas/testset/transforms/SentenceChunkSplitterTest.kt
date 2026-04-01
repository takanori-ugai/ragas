package ragas.testset.transforms

import kotlinx.coroutines.runBlocking
import ragas.testset.graph.Node
import ragas.testset.graph.NodeType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentenceChunkSplitterTest {
    @Test
    fun splitKeepsAbbreviationsAndDecimalsIntact() =
        runBlocking {
            val splitter = SentenceChunkSplitter(maxSentencesPerChunk = 1)
            val node =
                Node(
                    type = NodeType.DOCUMENT,
                    properties =
                        mutableMapOf(
                            "page_content" to "Mr. Smith paid 3.14 dollars. He arrived at 5 p.m. sharp.",
                        ),
                )

            val (chunks, _) = splitter.split(node)
            val chunkTexts = chunks.map { chunk -> chunk.getProperty("page_content").orEmpty() }

            assertTrue(chunkTexts.any { text -> "3.14 dollars." in text })
            assertFalse(chunkTexts.any { text -> text == "Mr." })
            assertFalse(chunkTexts.any { text -> text == "14 dollars." })
        }
}
