package ragas.embeddings

interface BaseRagasEmbedding {
    suspend fun embedText(text: String): List<Float>

    suspend fun embedTexts(texts: List<String>): List<List<Float>> =
        texts.map { text ->
            embedText(text)
        }
}
