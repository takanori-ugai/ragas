package ragas.embeddings

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LangChain4jEmbedding(
    private val model: EmbeddingModel,
) : BaseRagasEmbedding {
    override suspend fun embedText(text: String): List<Float> =
        withContext(Dispatchers.IO) {
            model.embed(text).content().vectorAsList()
        }

    override suspend fun embedTexts(texts: List<String>): List<List<Float>> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) {
                return@withContext emptyList()
            }
            val segments = texts.map { text -> TextSegment.from(text) }
            model.embedAll(segments).content().map { embedding ->
                embedding.vectorAsList()
            }
        }
}
