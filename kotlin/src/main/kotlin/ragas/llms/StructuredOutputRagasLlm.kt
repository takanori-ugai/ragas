package ragas.llms

interface StructuredOutputRagasLlm {
    suspend fun generateNumericValue(prompt: String): Double?

    suspend fun generateDiscreteValue(prompt: String): String?

    suspend fun generateRankingItems(prompt: String): List<String>
}
