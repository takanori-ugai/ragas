package ragas.optimizers

import io.github.oshai.kotlinlogging.KotlinLogging
import ragas.prompt.PromptContentPart
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Context snapshot passed to DSPy adapters during prompt candidate generation.
 *
 * @property iteration Iteration index.
 * @property dataset Dataset used for evaluation.
 * @property currentBestPrompt Current best prompt.
 * @property runtimeConfig DSPy runtime configuration.
 * @property labeledDemos Dataset examples selected as labeled demonstrations.
 * @property bootstrappedDemoHints Heuristic demo hints generated from the current best prompt.
 */
data class DspyCompileContext(
    val iteration: Int,
    val dataset: OptimizationDataset,
    val currentBestPrompt: OptimizerPrompt,
    val runtimeConfig: DspyRuntimeConfig,
    val labeledDemos: List<OptimizationExample>,
    val bootstrappedDemoHints: List<String>,
)

/**
 * DSPy runtime options (MIPRO-style controls) used during candidate compilation.
 */
data class DspyRuntimeConfig(
    val numCandidates: Int = 10,
    val maxBootstrappedDemos: Int = 5,
    val maxLabeledDemos: Int = 5,
    val initTemperature: Double = 1.0,
    val auto: AutoMode? = AutoMode.LIGHT,
    val numThreads: Int? = null,
    val maxErrors: Int? = null,
    val seed: Int = 9,
    val verbose: Boolean = false,
    val trackStats: Boolean = true,
    val logDir: String? = null,
    val metricThreshold: Double? = null,
) {
    enum class AutoMode {
        LIGHT,
        MEDIUM,
        HEAVY,
    }

    init {
        require(numCandidates > 0) { "numCandidates must be > 0" }
        require(maxBootstrappedDemos >= 0) { "maxBootstrappedDemos must be >= 0" }
        require(maxLabeledDemos >= 0) { "maxLabeledDemos must be >= 0" }
        require(initTemperature > 0.0) { "initTemperature must be > 0" }
        require(numThreads == null || numThreads > 0) { "numThreads must be > 0 when set" }
        require(maxErrors == null || maxErrors >= 0) { "maxErrors must be >= 0 when set" }
        require(metricThreshold == null || metricThreshold in 0.0..1.0) {
            "metricThreshold must be between 0 and 1 when set"
        }
    }
}

/**
 * Optional adapter seam for plugging in external DSPy-backed candidate compilation.
 *
 * Implementations can be provided via Java ServiceLoader as an optional runtime dependency.
 */
fun interface DspyAdapter {
    /**
     * Proposes candidate prompt variants for the current optimization step.
     *
     * @param context Context object for candidate proposal.
     */
    fun proposeCandidates(context: DspyCompileContext): List<OptimizerPrompt>
}

/**
 * Service-loader utility that discovers and returns DSPy adapter implementations.
 */
object DspyAdapterLoader {
    private val logger = KotlinLogging.logger {}

    /**
     * Loads the first available DSPy adapter via service discovery, or null.
     */
    fun loadFirstOrNull(): DspyAdapter? =
        try {
            ServiceLoader.load(DspyAdapter::class.java).firstOrNull()
        } catch (error: ServiceConfigurationError) {
            logger.warn(error) {
                "DSPy adapter discovery failed. Proceeding without external DSPy adapter."
            }
            null
        }
}

internal class HeuristicDspyAdapter : DspyAdapter {
    /**
     * Proposes candidate prompt variants for the current optimization step.
     */
    override fun proposeCandidates(context: DspyCompileContext): List<OptimizerPrompt> =
        when (val prompt = context.currentBestPrompt) {
            is OptimizerPrompt.Text -> proposeTextCandidates(prompt, context)
            is OptimizerPrompt.MultiModal -> proposeMultimodalCandidates(prompt, context)
        }

    private fun proposeTextCandidates(
        prompt: OptimizerPrompt.Text,
        context: DspyCompileContext,
    ): List<OptimizerPrompt.Text> {
        val base = prompt.value.trim()
        if (base.isEmpty()) return listOf(prompt)

        val strategies = mutableListOf<String>()
        strategies += "Think step-by-step, then answer with JSON only."
        strategies += "Use retrieved context strictly and avoid unsupported claims."
        strategies += "Keep the answer concise and deterministic."
        if (context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.MEDIUM ||
            context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.HEAVY
        ) {
            strategies += "Explicitly justify each field using the given context."
            strategies += "If uncertain, return the safest faithful answer."
        }
        if (context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.HEAVY) {
            strategies += "Perform a self-check before finalizing JSON."
            strategies += "Minimize hallucinations by cross-checking all claims."
        }

        val labeledHints =
            context.labeledDemos.map { demo ->
                val inKeys =
                    demo.promptInput.keys
                        .sorted()
                        .joinToString(",")
                "Labeled demo hint: prioritize fields [$inKeys] and align with expected outputs."
            }
        val demoHints = context.bootstrappedDemoHints + labeledHints
        val variants =
            (strategies + demoHints)
                .filter { it.isNotBlank() }
                .map { hint -> OptimizerPrompt.Text("$base\n$hint") }
                .distinctBy { it.value }

        return variants.take(context.runtimeConfig.numCandidates)
    }

    private fun proposeMultimodalCandidates(
        prompt: OptimizerPrompt.MultiModal,
        context: DspyCompileContext,
    ): List<OptimizerPrompt.MultiModal> {
        val base = prompt.content
        val hints = mutableListOf<String>()
        hints += "Think step-by-step, then answer with JSON only."
        hints += "Use retrieved context strictly and avoid unsupported claims."
        hints += "Keep the answer concise and deterministic."
        if (context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.MEDIUM ||
            context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.HEAVY
        ) {
            hints += "Ground every claim in either text or image evidence."
        }
        if (context.runtimeConfig.auto == DspyRuntimeConfig.AutoMode.HEAVY) {
            hints += "Cross-check visual details against textual context before final JSON."
        }
        hints += context.bootstrappedDemoHints

        return hints
            .filter { it.isNotBlank() }
            .map { hint -> OptimizerPrompt.MultiModal(base + PromptContentPart.Text(hint)) }
            .distinctBy { it.asTextPrompt() }
            .take(context.runtimeConfig.numCandidates)
    }
}
