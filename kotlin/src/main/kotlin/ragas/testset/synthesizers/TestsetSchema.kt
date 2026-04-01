package ragas.testset.synthesizers

import ragas.model.EvaluationDataset
import ragas.model.MultiTurnSample
import ragas.model.Sample
import ragas.model.SingleTurnSample

data class TestsetSample(
    val evalSample: Sample,
    val synthesizerName: String,
)

data class Testset(
    val samples: List<TestsetSample>,
    val runId: String =
        java.util.UUID
            .randomUUID()
            .toString(),
) {
    fun toEvaluationDataset(): EvaluationDataset<out Sample> {
        if (samples.isEmpty()) {
            return EvaluationDataset(emptyList<SingleTurnSample>())
        }
        val evalSamples = samples.map { sample -> sample.evalSample }
        val firstType = evalSamples.first()::class
        require(evalSamples.all { it::class == firstType }) {
            "Mixed sample types are not supported in a single Testset."
        }
        return when (firstType) {
            SingleTurnSample::class -> EvaluationDataset(evalSamples.map { it as SingleTurnSample })
            MultiTurnSample::class -> EvaluationDataset(evalSamples.map { it as MultiTurnSample })
            else -> error("Unsupported sample type: $firstType")
        }
    }

    fun toList(): List<Map<String, Any?>> =
        samples.map { sample ->
            sample.evalSample.toMap() + mapOf("synthesizer_name" to sample.synthesizerName)
        }

    companion object {
        fun fromList(data: List<Map<String, Any?>>): Testset {
            val samples =
                data.map { row ->
                    val synthesizer = row["synthesizer_name"]?.toString() ?: "unknown"
                    val sample =
                        run {
                            require(row["user_input"] !is List<*>) {
                                "Multi-turn deserialization is not implemented for fromList() yet."
                            }
                            SingleTurnSample(
                                userInput = row["user_input"]?.toString(),
                                response = row["response"]?.toString(),
                                reference = row["reference"]?.toString(),
                            )
                        }
                    TestsetSample(sample, synthesizer)
                }
            return Testset(samples)
        }
    }
}
