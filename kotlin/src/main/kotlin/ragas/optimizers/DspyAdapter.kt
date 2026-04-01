package ragas.optimizers

import ragas.prompt.PromptContentPart
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

data class DspyCompileContext(
    val iteration: Int,
    val dataset: OptimizationDataset,
    val currentBestPrompt: OptimizerPrompt,
)

/**
 * Optional adapter seam for plugging in external DSPy-backed candidate compilation.
 *
 * Implementations can be provided via Java ServiceLoader as an optional runtime dependency.
 */
fun interface DspyAdapter {
    fun proposeCandidates(context: DspyCompileContext): List<OptimizerPrompt>
}

object DspyAdapterLoader {
    fun loadFirstOrNull(): DspyAdapter? =
        try {
            ServiceLoader.load(DspyAdapter::class.java).firstOrNull()
        } catch (_: ServiceConfigurationError) {
            null
        }
}

internal class HeuristicDspyAdapter : DspyAdapter {
    override fun proposeCandidates(context: DspyCompileContext): List<OptimizerPrompt> =
        when (val prompt = context.currentBestPrompt) {
            is OptimizerPrompt.Text -> proposeTextCandidates(prompt)
            is OptimizerPrompt.MultiModal -> proposeMultimodalCandidates(prompt)
        }

    private fun proposeTextCandidates(prompt: OptimizerPrompt.Text): List<OptimizerPrompt.Text> {
        val base = prompt.value.trim()
        if (base.isEmpty()) return listOf(prompt)
        return listOf(
            OptimizerPrompt.Text("$base\nThink step-by-step, then answer with JSON only."),
            OptimizerPrompt.Text("$base\nUse retrieved context strictly and avoid unsupported claims."),
            OptimizerPrompt.Text("$base\nKeep the answer concise and deterministic."),
        )
    }

    private fun proposeMultimodalCandidates(prompt: OptimizerPrompt.MultiModal): List<OptimizerPrompt.MultiModal> {
        val base = prompt.content
        return listOf(
            OptimizerPrompt.MultiModal(
                base + PromptContentPart.Text("Think step-by-step, then answer with JSON only."),
            ),
            OptimizerPrompt.MultiModal(
                base + PromptContentPart.Text("Use retrieved context strictly and avoid unsupported claims."),
            ),
            OptimizerPrompt.MultiModal(
                base + PromptContentPart.Text("Keep the answer concise and deterministic."),
            ),
        )
    }
}
