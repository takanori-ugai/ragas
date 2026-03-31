package ragas.model

import kotlin.reflect.KClass

data class EvaluationDataset<T : Sample>(
    val samples: List<T>,
) : Iterable<T> {
    init {
        validateSamples(samples)
    }

    fun toList(): List<Map<String, Any?>> = samples.map { it.toMap() }

    fun features(): Set<String> = samples.firstOrNull()?.toMap()?.keys ?: emptySet()

    fun getSampleType(): KClass<out Sample>? = samples.firstOrNull()?.let { it::class }

    override fun iterator(): Iterator<T> = samples.iterator()

    private fun validateSamples(items: List<T>) {
        if (items.isEmpty()) {
            return
        }
        val firstType = items.first()::class
        items.forEachIndexed { index, sample ->
            require(sample::class == firstType) {
                "Sample at index $index is ${sample::class}, expected $firstType."
            }
        }
    }
}
